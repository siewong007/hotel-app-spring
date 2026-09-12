package com.hotelapp.communications;

import com.hotelapp.communications.CommsModels.AudienceGuest;
import com.hotelapp.communications.CommsModels.EmailCampaign;
import com.hotelapp.communications.CommsModels.PreArrivalBooking;
import com.hotelapp.communications.CommunicationsSchedulerTx.DeliveryValues;
import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.settings.HotelSettings;
import com.hotelapp.email.EmailLayout;
import com.hotelapp.guestbooking.FunnelService;
import com.hotelapp.portal.PortalAuth;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Communications scheduler, mirroring
 * {@code modules/communications/scheduler.rs}: campaign fan-out, daily
 * birthday vouchers, and pre-arrival reminders.
 *
 * <p>Campaign fan-out: campaigns whose {@code scheduled_at} has arrived move
 * scheduled→running, then eligible recipients are expanded in bounded batches
 * into the {@code email_deliveries} outbox (the worker does the actual
 * sending). Expansion is restart-safe: recipients are selected by "no
 * delivery row for this campaign yet" and inserts dedup on idempotency key.
 *
 * <p>Birthday vouchers: once per hotel-local day, guests whose birthday is
 * today (Feb-29 honoured on Feb-28 in non-leap years), hold a live
 * {@code birthday_voucher} subscription, and have not yet received this
 * year's voucher get — in ONE transaction — a voucher, an audit event, and a
 * queued email.
 */
@Component
public class CommunicationsScheduler {

    private static final Logger log = LoggerFactory.getLogger(CommunicationsScheduler.class);

    private static final int EXPANSION_BATCH = 200;

    private final CommunicationsRepo repo;
    private final CommunicationsSchedulerTx schedulerTx;
    private final HotelSettings settings;
    private final AppProperties props;
    private final UnsubscribeTokens tokens;
    private final FunnelService funnel;

    private LocalDate lastBirthdayRun;

    public CommunicationsScheduler(CommunicationsRepo repo,
            CommunicationsSchedulerTx schedulerTx, HotelSettings settings,
            AppProperties props, UnsubscribeTokens tokens, FunnelService funnel) {
        this.repo = repo;
        this.schedulerTx = schedulerTx;
        this.settings = settings;
        this.props = props;
        this.tokens = tokens;
        this.funnel = funnel;
    }

    /** The upstream 60s poll: campaigns, then birthdays, then pre-arrival. */
    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        try {
            tickCampaigns();
        } catch (Exception e) {
            log.warn("Campaign scheduler tick failed: {}", e.getMessage());
        }
        try {
            int issued = tickBirthdays();
            if (issued > 0) {
                log.info("Birthday scheduler issued {} voucher(s)", issued);
            }
        } catch (Exception e) {
            log.warn("Birthday scheduler tick failed: {}", e.getMessage());
        }
        try {
            tickPreArrivalReminders();
        } catch (Exception e) {
            log.warn("Pre-arrival scheduler tick failed: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Shared mail helpers
    // ------------------------------------------------------------------

    String unsubscribeFooterHtml(long guestId) {
        return tokens.footerHtml(guestId);
    }

    /**
     * {@code guest_vars}: the standard per-guest template variables. The
     * {@code full_name} KEY is a template contract — campaigns already in the
     * database interpolate {@code {{full_name}}} — so it keeps its name even
     * though the value now reads from {@code guests.nick_name}.
     */
    static Map<String, String> guestVars(AudienceGuest guest) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("first_name", guest.firstName());
        vars.put("full_name", guest.nickName());
        vars.put("email", guest.email());
        return vars;
    }

    // ------------------------------------------------------------------
    // Campaign fan-out
    // ------------------------------------------------------------------

    void tickCampaigns() {
        for (EmailCampaign campaign : repo.dueScheduledCampaigns()) {
            if (!repo.markCampaignRunning(campaign.id())) {
                continue; // another instance won the transition
            }
            int expanded = expandCampaign(campaign);
            log.info("Campaign {} expanded to {} recipient(s)", campaign.id(), expanded);
        }
    }

    /**
     * {@code campaign_body_for_guest}: the template rendered with guest vars,
     * falling back to the campaign's own body on any render failure — one odd
     * template must not sink the whole send.
     */
    private String campaignBodyForGuest(EmailCampaign campaign, AudienceGuest guest) {
        String base;
        if (campaign.templateId() == null) {
            base = campaign.bodyHtml();
        } else {
            base = repo.getTemplate(campaign.templateId())
                    .map(template -> {
                        try {
                            return CommsValidation.renderTemplate(template.bodyHtml(),
                                    guestVars(guest), template.variables());
                        } catch (ApiError e) {
                            return campaign.bodyHtml();
                        }
                    })
                    .orElse(campaign.bodyHtml());
        }
        return base + unsubscribeFooterHtml(guest.id());
    }

    private int expandCampaign(EmailCampaign campaign) {
        int total = 0;
        while (true) {
            List<AudienceGuest> batch =
                    repo.audienceBatch(campaign.topic(), campaign.id(), EXPANSION_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (AudienceGuest guest : batch) {
                String bodyHtml = campaignBodyForGuest(campaign, guest);
                schedulerTx.insertDelivery(new DeliveryValues(
                        campaign.id(), "campaign", guest.id(), campaign.topic(),
                        guest.email(), campaign.subject(), bodyHtml,
                        campaign.bodyText(), null,
                        "campaign:" + campaign.id() + ":guest:" + guest.id()));
                total++;
            }
        }
        repo.refreshCampaignTotal(campaign.id());
        // A campaign with zero recipients completes immediately once running.
        repo.completeCampaignIfDone(campaign.id());
        return total;
    }

    // ------------------------------------------------------------------
    // Pre-arrival reminders
    // ------------------------------------------------------------------

    /** Settings clamp: at least 2 hours (actionable) and at most one week out. */
    static int clampedHoursBefore(int hours) {
        return Math.max(2, Math.min(168, hours));
    }

    /** Whole days the reminder window spans for a configured hours-before value. */
    static int reminderWindowDays(int hoursBefore) {
        return (int) Math.ceil(clampedHoursBefore(hoursBefore) / 24.0);
    }

    void tickPreArrivalReminders() {
        if (!"true".equals(settings.getString("pre_arrival_reminder_enabled", "false"))) {
            return;
        }
        int hours = settings.getInt("pre_arrival_reminder_hours_before", 48);
        int windowDays = reminderWindowDays(hours);
        LocalDate today = repo.hotelLocalDate();
        String hotel = EmailLayout.hotelDisplayName(props);

        for (PreArrivalBooking booking : repo.duePreArrivalBookings(today, windowDays)) {
            String subject = "Your stay at " + hotel + " begins soon · " + booking.bookingNumber();
            // Deep-link into the wizard with a booking token; without one the
            // CTA lands on the lookup page and the guest retypes booking number
            // and name — the reminder exists to remove that friction.
            String checkin;
            String token = issueBookingAccessToken(booking.id(), booking.checkInDate());
            if (token != null) {
                checkin = EmailLayout.absoluteUrl(props, "/guest-checkin/form?token=" + token);
            } else {
                checkin = EmailLayout.absoluteUrl(props, "/guest-checkin");
            }
            String stayIn = booking.checkInDate()
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
            String stayOut = booking.checkOutDate()
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
            String room = (booking.roomNumber() == null ? "-" : booking.roomNumber())
                    + " (" + (booking.roomTypeName() == null ? "-" : booking.roomTypeName()) + ")";
            String details = EmailLayout.detailsTable(new String[][] {
                    {"Booking", booking.bookingNumber()},
                    {"Room", room},
                    {"Check-in", stayIn},
                    {"Check-out", stayOut}});
            String innerHtml = "<p>Dear " + EmailLayout.htmlEscape(booking.guestName()) + ",</p>"
                    + "<p>We look forward to welcoming you. Your stay <strong>"
                    + EmailLayout.htmlEscape(booking.bookingNumber()) + "</strong> starts on "
                    + "<strong>" + EmailLayout.htmlEscape(stayIn) + "</strong>.</p>"
                    + details
                    + "<p>You can complete online check-in from the link below to skip the "
                    + "front desk.</p>";
            String innerText = "Dear " + booking.guestName() + ",\nYour stay "
                    + booking.bookingNumber() + " starts on " + stayIn + ". Room: " + room
                    + ". Check-out: " + stayOut
                    + ".\nYou can complete online check-in from the link in this email.";
            EmailLayout.RenderedEmail rendered = EmailLayout.render(props,
                    "Your " + hotel + " stay " + booking.bookingNumber() + " starts on "
                            + stayIn + ".",
                    "Your stay begins soon", innerHtml, innerText,
                    new EmailLayout.Cta("Complete pre-check-in", checkin));
            String bodyHtml = rendered.html() + unsubscribeFooterHtml(booking.guestId());

            schedulerTx.insertDelivery(new DeliveryValues(
                    null, "pre_arrival_reminder", booking.guestId(), "pre_arrival_reminder",
                    booking.guestEmail(), subject, bodyHtml, rendered.text(), null,
                    "pre-arrival:" + booking.id()));
        }
    }

    /**
     * {@code issue_booking_access_token}: returns null rather than throwing —
     * an email must still go out with a lookup-page link if issuance fails.
     */
    private String issueBookingAccessToken(long bookingId, LocalDate checkInDate) {
        String token = PortalAuth.generateSessionToken();
        OffsetDateTime expiresAt = FunnelService.anonymousAccessTokenExpiry(
                OffsetDateTime.now(), checkInDate);
        try {
            funnel.updatePrecheckinToken(bookingId, token, expiresAt);
            return token;
        } catch (Exception e) {
            log.error("Failed to issue booking access token for booking {}: {}",
                    bookingId, e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Birthday vouchers
    // ------------------------------------------------------------------

    static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    /**
     * Month/day pairs honoured today. On Feb 28 of a non-leap year this also
     * covers Feb-29 birthdays (recommended default policy).
     */
    static int[][] birthdayMatchPairs(LocalDate today) {
        int[] primary = {today.getMonthValue(), today.getDayOfMonth()};
        if (today.getMonthValue() == 2 && today.getDayOfMonth() == 28
                && !isLeapYear(today.getYear())) {
            return new int[][] {primary, {2, 29}};
        }
        return new int[][] {primary, primary};
    }

    static String generateBirthdayVoucherCode() {
        String random = java.util.UUID.randomUUID().toString()
                .replace("-", "").toUpperCase(Locale.ROOT);
        return "BDY" + random.substring(0, 20);
    }

    int tickBirthdays() {
        if (!"true".equals(settings.getString("birthday_voucher_enabled", "false"))) {
            return 0;
        }
        LocalDate today = repo.hotelLocalDate();
        if (today.equals(lastBirthdayRun)) {
            return 0;
        }
        long promotionId = settings.getInt("birthday_promotion_id", 0);
        if (promotionId <= 0) {
            log.warn("Birthday vouchers enabled but 'birthday_promotion_id' is not configured");
            lastBirthdayRun = today;
            return 0;
        }
        var status = repo.promotionStatus(promotionId);
        if (status.isEmpty()) {
            log.warn("Birthday promotion {} does not exist; skipping issuance", promotionId);
            lastBirthdayRun = today;
            return 0;
        }
        if (!"published".equals(status.get())) {
            log.warn("Birthday promotion {} is '{}', not 'published'; skipping issuance",
                    promotionId, status.get());
            lastBirthdayRun = today;
            return 0;
        }
        int expiryDays = settings.getPositiveInt("birthday_voucher_expiry_days", 30);
        String promotionName = repo.promotionName(promotionId)
                .orElse("your birthday reward");
        String hotelName = settings.getString("hotel_name", "our hotel");
        String sourceReference = "birthday:" + today.getYear();
        int[][] pairs = birthdayMatchPairs(today);

        int issued = 0;
        while (true) {
            List<AudienceGuest> targets = repo.birthdayTargets(
                    pairs[0][0], pairs[0][1], pairs[1][0], pairs[1][1],
                    sourceReference, promotionId, EXPANSION_BATCH);
            if (targets.isEmpty()) {
                break;
            }
            for (AudienceGuest guest : targets) {
                issued += issueBirthdayVoucher(guest, promotionId, promotionName,
                        hotelName, expiryDays, sourceReference);
            }
        }
        lastBirthdayRun = today;
        return issued;
    }

    /**
     * One transaction per guest: voucher + audit + queued email. A uniqueness
     * conflict (already issued this year, or this promotion already gave this
     * guest a voucher in a previous year) writes nothing.
     */
    private int issueBirthdayVoucher(AudienceGuest guest, long promotionId,
            String promotionName, String hotelName, int expiryDays, String sourceReference) {
        String code = generateBirthdayVoucherCode();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(expiryDays);
        String expiryText = expiresAt
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
        String subject = "Happy birthday from " + hotelName + "!";
        String portal = EmailLayout.absoluteUrl(props, "/portal");
        String details = EmailLayout.detailsTable(new String[][] {
                {"Gift", promotionName},
                {"Voucher code", code},
                {"Valid until", expiryText}});
        String innerHtml = "<p>Dear " + EmailLayout.htmlEscape(guest.firstName()) + ",</p>"
                + "<p>Happy birthday! As a thank-you for staying with us, here is your gift.</p>"
                + details
                + "<p>You can also find it in your guest portal wallet.</p>"
                + "<p>Warm wishes,<br>" + EmailLayout.htmlEscape(hotelName) + "</p>";
        String innerText = "Dear " + guest.firstName() + ",\nHappy birthday! Your gift is "
                + promotionName + ".\nVoucher code: " + code + " (valid until " + expiryText
                + ").\nWarm wishes,\n" + hotelName;
        EmailLayout.RenderedEmail rendered = EmailLayout.render(props,
                "A birthday gift from " + hotelName + " is waiting in your wallet.",
                "Happy birthday", innerHtml, innerText,
                new EmailLayout.Cta("Open your wallet", portal));
        String bodyHtml = rendered.html() + unsubscribeFooterHtml(guest.id());

        Long voucherId = schedulerTx.issueBirthdayVoucher(
                promotionId, guest.id(), code, expiresAt, sourceReference,
                new DeliveryValues(
                        null, "birthday_voucher", guest.id(), "birthday_voucher",
                        guest.email(), subject, bodyHtml, rendered.text(), null,
                        sourceReference + ":guest:" + guest.id()));
        if (voucherId == null) {
            log.warn("Birthday voucher skipped for guest {}: voucher uniqueness conflict "
                    + "(already issued this year, or promotion {} previously issued to "
                    + "this guest)", guest.id(), promotionId);
            return 0;
        }
        return 1;
    }
}
