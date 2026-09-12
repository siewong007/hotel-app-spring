package com.hotelapp.email;

import com.hotelapp.communications.CommunicationsSchedulerTx;
import com.hotelapp.communications.CommunicationsSchedulerTx.DeliveryValues;
import com.hotelapp.communications.UnsubscribeTokens;
import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.i18n.Locales;
import com.hotelapp.core.i18n.Locales.Locale;
import com.hotelapp.core.settings.HotelSettings;
import com.hotelapp.email.EmailLayout.Cta;
import com.hotelapp.guestbooking.FunnelService;
import com.hotelapp.paymentrecovery.PaymentRecovery;
import com.hotelapp.portal.PortalAuth;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Port of {@code services/booking_emails.rs} plus the guest-facing mail
 * composers in {@code services/payments.rs}: booking-confirmed,
 * payment-confirmed, paid-online room-assignment, checkout-receipt,
 * receipt-request and payment-rejected notifications.
 *
 * <p>Every sender is a no-op when the guest has no usable email — that is the
 * "(if available)" contract. All kinds reuse the {@code booking_confirmation}
 * pair except {@code checkout_receipt}; both are schema-allowed with
 * {@code campaign_id IS NULL}. Idempotency keys make repeated staff actions
 * queue exactly one email.
 *
 * <p>Mail is written in the guest's own language: the worker sends minutes
 * later with no request/session to consult, so the locale is resolved at queue
 * time from {@code guests.language_preference} and frozen into the stored
 * subject and body.
 */
@Service
public class BookingEmails {

    private static final Logger log = LoggerFactory.getLogger(BookingEmails.class);

    private final JdbcTemplate jdbc;
    private final HotelSettings settings;
    private final AppProperties props;
    private final FunnelService funnel;
    private final PaymentRecovery recovery;
    private final UnsubscribeTokens tokens;
    private final CommunicationsSchedulerTx schedulerTx;

    public BookingEmails(JdbcTemplate jdbc, HotelSettings settings, AppProperties props,
            FunnelService funnel, PaymentRecovery recovery, UnsubscribeTokens tokens,
            CommunicationsSchedulerTx schedulerTx) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.props = props;
        this.funnel = funnel;
        this.recovery = recovery;
        this.tokens = tokens;
        this.schedulerTx = schedulerTx;
    }

    // ------------------------------------------------------------------
    // Shared source row — {@code BookingEmailSource}
    // ------------------------------------------------------------------

    /** Booking + guest fields shared by the confirmation mails. */
    public record BookingEmailSource(long guestId, String guestName, String guestFirstName,
            String guestLastName, String guestEmail, String bookingNumber,
            LocalDate checkInDate, LocalDate checkOutDate, BigDecimal totalAmount,
            String currency, String roomNumber, String roomType, String guestLocale) {

        /** Legal name once check-in supplies both halves, else the nickname. */
        String guestName(Locale locale) {
            String display = displayGuestName(guestName, guestFirstName, guestLastName);
            return display.isEmpty() ? locale.message("email.fallback.guest") : display;
        }

        String bookingLabel(Locale locale) {
            String value = bookingNumber == null ? "" : bookingNumber.trim();
            return value.isEmpty() ? locale.message("email.fallback.booking") : value;
        }

        long nights() {
            return Math.max(0, java.time.temporal.ChronoUnit.DAYS
                    .between(checkInDate, checkOutDate));
        }

        /** {@code "MYR 250.00"} — the booking's own currency, or bare amount. */
        String money(BigDecimal amount) {
            String scaled = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
            String code = currency == null ? "" : currency.trim();
            return code.isEmpty() ? scaled : code + " " + scaled;
        }

        String stayBlockHtml(Locale locale) {
            String room = (roomNumber == null ? "-" : roomNumber)
                    + " (" + (roomType == null ? "-" : roomType) + ")";
            String stay = locale.format("email.stay.range", Map.of(
                    "from", locale.formatDate(checkInDate),
                    "to", locale.formatDate(checkOutDate),
                    "nights", Long.toString(nights())));
            String total = money(totalAmount);
            return EmailLayout.detailsTable(new String[][] {
                    {locale.message("email.labels.booking"), bookingLabel(locale)},
                    {locale.message("email.labels.room"), room},
                    {locale.message("email.labels.stay"), stay},
                    {locale.message("email.labels.total"), total}});
        }

        String stayBlockText(Locale locale) {
            return locale.format("email.stay.text", Map.of(
                    "booking", bookingLabel(locale),
                    "room", roomNumber == null ? "-" : roomNumber,
                    "roomType", roomType == null ? "-" : roomType,
                    "from", locale.formatDate(checkInDate),
                    "to", locale.formatDate(checkOutDate),
                    "nights", Long.toString(nights()),
                    "total", money(totalAmount)));
        }
    }

    /** {@code display_guest_name}: legal name wins when both halves exist. */
    static String displayGuestName(String nickName, String firstName, String lastName) {
        String first = firstName == null ? null : firstName.trim();
        String last = lastName == null ? null : lastName.trim();
        if (first != null && !first.isEmpty() && last != null && !last.isEmpty()) {
            return first + " " + last;
        }
        return nickName == null ? "" : nickName.trim();
    }

    /**
     * {@code load_source}: null when the booking is gone or the guest has no
     * usable email — callers treat both as "nothing to send".
     */
    private record LoadedSource(BookingEmailSource source, String recipient) {}

    private LoadedSource loadSource(long bookingId) {
        List<BookingEmailSource> rows = jdbc.query("""
                SELECT g.id AS guest_id,
                       g.nick_name AS guest_name,
                       g.first_name AS guest_first_name,
                       g.last_name AS guest_last_name,
                       g.email AS guest_email,
                       b.booking_number,
                       b.check_in_date,
                       b.check_out_date,
                       b.total_amount,
                       b.currency,
                       r.room_number,
                       rt.name AS room_type,
                       g.language_preference AS guest_locale
                FROM bookings b
                JOIN guests g ON g.id = b.guest_id
                LEFT JOIN rooms r ON r.id = b.room_id
                LEFT JOIN room_types rt ON rt.id = r.room_type_id
                WHERE b.id = ?
                """, (rs, i) -> new BookingEmailSource(
                        rs.getLong("guest_id"),
                        rs.getString("guest_name"),
                        rs.getString("guest_first_name"),
                        rs.getString("guest_last_name"),
                        rs.getString("guest_email"),
                        rs.getString("booking_number"),
                        rs.getObject("check_in_date", LocalDate.class),
                        rs.getObject("check_out_date", LocalDate.class),
                        rs.getBigDecimal("total_amount"),
                        rs.getString("currency"),
                        rs.getString("room_number"),
                        rs.getString("room_type"),
                        rs.getString("guest_locale")),
                bookingId);
        if (rows.isEmpty()) {
            return null;
        }
        BookingEmailSource source = rows.get(0);
        String recipient = source.guestEmail() == null ? "" : source.guestEmail().trim();
        return recipient.isEmpty() ? null : new LoadedSource(source, recipient);
    }

    /**
     * {@code resolve_locale}: the guest's preference wins, the hotel's
     * configured default supplies the house language, English is the floor.
     */
    private Locale resolveLocale(BookingEmailSource source) {
        String hotelDefault = settings.getString(
                Locales.DEFAULT_LOCALE_SETTING_KEY, Locales.DEFAULT_LOCALE);
        return Locales.resolve(source.guestLocale(), hotelDefault);
    }

    /** Queue one {@code email_deliveries} row in its own transaction. */
    private void queue(long guestId, String recipient, String subject, String bodyHtml,
            String bodyText, String idempotencyKey) {
        schedulerTx.insertDelivery(new DeliveryValues(
                null, "booking_confirmation", guestId, "booking_confirmation",
                recipient, subject, bodyHtml, bodyText, null, idempotencyKey));
    }

    // ------------------------------------------------------------------
    // Booking-confirmed — {@code queue_booking_confirmation_email}
    // ------------------------------------------------------------------

    public void queueBookingConfirmationEmail(long bookingId) {
        LoadedSource loaded = loadSource(bookingId);
        if (loaded == null) {
            return;
        }
        BookingEmailSource source = loaded.source();
        Locale locale = resolveLocale(source);
        String hotel = EmailLayout.hotelDisplayName(props);
        String booking = source.bookingLabel(locale);
        String subject = locale.format("email.bookingConfirmed.subject",
                Map.of("hotel", hotel, "booking", booking));
        String portal = EmailLayout.absoluteUrl(props, "/portal");
        String innerHtml = "<p>" + locale.format("email.greeting",
                Map.of("name", EmailLayout.htmlEscape(source.guestName(locale))))
                + "</p><p>" + locale.format("email.bookingConfirmed.bodyHtml",
                        Map.of("booking", EmailLayout.htmlEscape(booking)))
                + "</p>" + source.stayBlockHtml(locale)
                + "<p>" + locale.message("email.bookingConfirmed.portalNote") + "</p>";
        String innerText = locale.format("email.greeting",
                Map.of("name", source.guestName(locale)))
                + "\n" + locale.format("email.bookingConfirmed.bodyText",
                        Map.of("booking", booking))
                + "\n" + source.stayBlockText(locale)
                + "\n" + locale.message("email.bookingConfirmed.portalNote");
        String preheader = locale.format("email.bookingConfirmed.preheader",
                Map.of("hotel", hotel, "booking", booking));
        EmailLayout.RenderedEmail rendered = EmailLayout.render(props, preheader,
                locale.message("email.bookingConfirmed.heading"), innerHtml, innerText,
                new Cta(locale.message("email.cta.viewBooking"), portal));
        queue(source.guestId(), loaded.recipient(), subject, rendered.html(),
                rendered.text(), "booking-confirmed:" + bookingId);
    }

    /** Best-effort wrapper: a mail failure must never surface post-commit. */
    public void tryQueueBookingConfirmationEmail(long bookingId) {
        try {
            queueBookingConfirmationEmail(bookingId);
        } catch (Exception e) {
            log.error("Failed to queue booking confirmation email for booking {}: {}",
                    bookingId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Payment-confirmed — {@code queue_payment_confirmation_email}
    // ------------------------------------------------------------------

    /**
     * {@code payment_confirmation_cta}: with a booking token the guest goes
     * straight into the pre-check-in wizard; without one to the portal. The
     * split follows the account, not the booking — {@code /portal} is a
     * sign-in wall to someone who never made an account.
     */
    static String[] paymentConfirmationCta(Locale locale, AppProperties props,
            String accessToken) {
        String token = accessToken == null ? "" : accessToken.trim();
        if (!token.isEmpty()) {
            return new String[] {
                    EmailLayout.absoluteUrl(props, "/guest-checkin/form?token=" + token),
                    locale.message("email.cta.preCheckIn")};
        }
        return new String[] {
                EmailLayout.absoluteUrl(props, "/portal"),
                locale.message("email.cta.viewBooking")};
    }

    public void queuePaymentConfirmationEmail(long bookingId, long paymentId) {
        LoadedSource loaded = loadSource(bookingId);
        if (loaded == null) {
            return;
        }
        BookingEmailSource source = loaded.source();

        List<Map<String, Object>> paymentRows = jdbc.queryForList(
                "SELECT amount, payment_method FROM payments WHERE id = ? AND booking_id = ?",
                paymentId, bookingId);
        if (paymentRows.isEmpty()) {
            return;
        }
        Map<String, Object> payment = paymentRows.get(0);
        BigDecimal amount = (BigDecimal) payment.get("amount");
        String method = String.valueOf(payment.get("payment_method")).replace('_', ' ');

        // Running position across every non-refund completed payment.
        BigDecimal paid = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount) FILTER (
                    WHERE status = 'completed'
                      AND COALESCE(payment_type, 'booking') != 'refund'
                ), 0)
                FROM payments
                WHERE booking_id = ?
                """, BigDecimal.class, bookingId);
        BigDecimal balance = source.totalAmount().subtract(paid).max(BigDecimal.ZERO);
        Locale locale = resolveLocale(source);
        String closing = balance.signum() == 0
                ? locale.message("email.paymentConfirmed.settled")
                : locale.message("email.paymentConfirmed.outstanding");

        String hotel = EmailLayout.hotelDisplayName(props);
        String booking = source.bookingLabel(locale);
        String subject = locale.format("email.paymentConfirmed.subject",
                Map.of("hotel", hotel, "booking", booking));
        // A guest with no portal account cannot use /portal — mint a booking
        // token so the CTA lands in the wizard instead of a sign-in wall.
        String accessToken = guestHasPortalAccount(source.guestId())
                ? null
                : issueBookingAccessToken(bookingId, source.checkInDate());
        String[] cta = paymentConfirmationCta(locale, props, accessToken);
        String amountLabel = source.money(amount);
        String paidLabel = source.money(paid);
        String balanceLabel = source.money(balance);
        String extra = EmailLayout.detailsTable(new String[][] {
                {locale.message("email.labels.payment"), amountLabel},
                {locale.message("email.labels.method"), method},
                {locale.message("email.labels.paymentsReceived"), paidLabel},
                {locale.message("email.labels.balance"), balanceLabel}});
        String innerHtml = "<p>" + locale.format("email.greeting",
                Map.of("name", EmailLayout.htmlEscape(source.guestName(locale))))
                + "</p><p>" + locale.format("email.paymentConfirmed.bodyHtml",
                        Map.of("amount", EmailLayout.htmlEscape(amountLabel),
                                "method", EmailLayout.htmlEscape(method),
                                "booking", EmailLayout.htmlEscape(booking)))
                + "</p>" + source.stayBlockHtml(locale) + extra + "<p>" + closing + "</p>";
        String innerText = locale.format("email.greeting",
                Map.of("name", source.guestName(locale)))
                + "\n" + locale.format("email.paymentConfirmed.bodyText",
                        Map.of("amount", amountLabel, "method", method,
                                "booking", booking))
                + "\n" + source.stayBlockText(locale)
                + "\n" + locale.message("email.labels.paymentsReceived") + ": " + paidLabel
                + "\n" + locale.message("email.labels.balance") + ": " + balanceLabel
                + "\n" + closing;
        String preheader = locale.format("email.paymentConfirmed.preheader",
                Map.of("hotel", hotel, "booking", booking));
        EmailLayout.RenderedEmail rendered = EmailLayout.render(props, preheader,
                locale.message("email.paymentConfirmed.heading"), innerHtml, innerText,
                new Cta(cta[1], cta[0]));
        queue(source.guestId(), loaded.recipient(), subject, rendered.html(),
                rendered.text(), "payment-confirmed:" + paymentId);
    }

    /** Best-effort wrapper: a notification failure must never undo an approval. */
    public void tryQueuePaymentConfirmationEmail(long bookingId, long paymentId) {
        try {
            queuePaymentConfirmationEmail(bookingId, paymentId);
        } catch (Exception e) {
            log.error("Failed to queue payment confirmation email for payment {}"
                    + " (booking {}): {}", paymentId, bookingId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Paid online booking room-assignment — {@code queue_paid_online_booking_room_assignment}
    // ------------------------------------------------------------------

    /**
     * Whether this booking is covered by the room-assignment notification —
     * i.e. whether the guest is being told, by this mail, that their payment
     * is confirmed. Callers that would otherwise send their own
     * payment-confirmation mail use it to suppress the duplicate. {@code false}
     * means nothing was queued.
     */
    public boolean queuePaidOnlineBookingRoomAssignment(long bookingId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.id AS booking_id, b.booking_number, b.guest_id,
                       COALESCE(NULLIF(g.nick_name, ''), 'Guest') AS guest_name,
                       g.email AS guest_email, r.room_number,
                       rt.name AS room_type_name, b.check_in_date, b.check_out_date
                FROM bookings b
                JOIN guests g ON g.id = b.guest_id
                JOIN rooms r ON r.id = b.room_id
                JOIN room_types rt ON rt.id = r.room_type_id
                WHERE b.id = ?
                  AND b.portal_request_id IS NOT NULL
                  AND b.payment_status = 'paid'
                  AND g.email IS NOT NULL AND TRIM(g.email) <> ''
                  AND EXISTS (
                      SELECT 1 FROM payments p
                      WHERE p.booking_id = b.id AND p.status = 'completed'
                        AND p.payment_method IN ('card', 'duitnow', 'online_banking')
                  )
                """, bookingId);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> assignment = rows.get(0);
        String bookingNumber = String.valueOf(assignment.get("booking_number"));
        String roomNumber = String.valueOf(assignment.get("room_number"));
        String roomTypeName = String.valueOf(assignment.get("room_type_name"));
        String guestName = String.valueOf(assignment.get("guest_name"));
        String guestEmail = String.valueOf(assignment.get("guest_email"));
        LocalDate checkIn = ((java.sql.Date) assignment.get("check_in_date")).toLocalDate();
        LocalDate checkOut = ((java.sql.Date) assignment.get("check_out_date")).toLocalDate();

        String hotel = EmailLayout.hotelDisplayName(props);
        String subject = hotel + " · room " + roomNumber + " assigned · " + bookingNumber;
        String portal = EmailLayout.absoluteUrl(props, "/portal");
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd MMM yyyy",
                java.util.Locale.ENGLISH);
        String stay = checkIn.format(dayFmt) + " to " + checkOut.format(dayFmt);
        String room = roomNumber + " (" + roomTypeName + ")";
        String details = EmailLayout.detailsTable(new String[][] {
                {"Booking", bookingNumber}, {"Room", room}, {"Stay", stay}});
        String innerHtml = "<p>Dear " + EmailLayout.htmlEscape(guestName) + ",</p>"
                + "<p>Your online payment is confirmed and your room has been assigned.</p>"
                + details
                + "<p>You can also view these details in your guest portal.</p>";
        String innerText = "Dear " + guestName
                + ",\nYour online payment is confirmed and your room has been assigned.\n"
                + "Booking: " + bookingNumber + "\nRoom: " + room + "\nStay: " + stay;
        EmailLayout.RenderedEmail rendered = EmailLayout.render(props,
                "Room " + roomNumber + " assigned for " + hotel + " reservation "
                        + bookingNumber + ".",
                "Your room is assigned", innerHtml, innerText,
                new Cta("View your booking", portal));
        schedulerTx.insertDelivery(new DeliveryValues(
                null, "booking_confirmation", (Long) assignment.get("guest_id"),
                "booking_confirmation", guestEmail, subject, rendered.html(),
                rendered.text(), null, "online-room-assignment:" + bookingId));
        return true;
    }

    /** Best-effort wrapper; {@code false} on failure as well as ineligibility. */
    public boolean tryQueuePaidOnlineBookingRoomAssignment(long bookingId) {
        try {
            return queuePaidOnlineBookingRoomAssignment(bookingId);
        } catch (Exception e) {
            log.error("Failed to queue room-assignment email for booking {}: {}",
                    bookingId, e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Checkout receipt — {@code queue_checkout_receipt_email}
    // ------------------------------------------------------------------

    public void queueCheckoutReceiptEmail(long bookingId, String invoiceNumber) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT g.id AS guest_id,
                       g.nick_name AS guest_name,
                       g.email AS guest_email,
                       b.company_id,
                       b.booking_number,
                       b.check_in_date,
                       b.check_out_date,
                       b.total_amount,
                       r.room_number,
                       rt.name AS room_type
                FROM bookings b
                JOIN guests g ON g.id = b.guest_id
                LEFT JOIN rooms r ON r.id = b.room_id
                LEFT JOIN room_types rt ON rt.id = r.room_type_id
                WHERE b.id = ?
                """, bookingId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Booking not found");
        }
        Map<String, Object> source = rows.get(0);
        String recipient = source.get("guest_email") == null ? ""
                : String.valueOf(source.get("guest_email")).trim();
        // Corporate stays are billed to the company; no personal receipt.
        if (recipient.isEmpty() || source.get("company_id") != null) {
            return;
        }
        long guestId = ((Number) source.get("guest_id")).longValue();
        String guestName = String.valueOf(source.get("guest_name"));
        BigDecimal totalAmount = (BigDecimal) source.get("total_amount");
        LocalDate checkIn = ((java.sql.Date) source.get("check_in_date")).toLocalDate();
        LocalDate checkOut = ((java.sql.Date) source.get("check_out_date")).toLocalDate();
        String bookingNumber = source.get("booking_number") == null
                ? "" : String.valueOf(source.get("booking_number"));

        BigDecimal paid = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount) FILTER (
                    WHERE status = 'completed'
                      AND COALESCE(payment_type, 'booking') != 'refund'
                ), 0)
                FROM payments
                WHERE booking_id = ?
                """, BigDecimal.class, bookingId);
        long nights = Math.max(0,
                java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut));
        BigDecimal balance = totalAmount.subtract(paid).max(BigDecimal.ZERO);

        String hotel = EmailLayout.hotelDisplayName(props);
        String subject = "Your " + hotel + " receipt · " + bookingNumber;
        String portal = EmailLayout.absoluteUrl(props, "/portal");
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd MMM yyyy",
                java.util.Locale.ENGLISH);
        String stay = checkIn.format(dayFmt) + " to " + checkOut.format(dayFmt)
                + " · " + nights + " night(s)";
        String room = (source.get("room_number") == null ? "-"
                        : String.valueOf(source.get("room_number")))
                + " (" + (source.get("room_type") == null ? "-"
                        : String.valueOf(source.get("room_type"))) + ")";
        String total = totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String paidLabel = paid.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String balanceLabel = balance.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String details = EmailLayout.detailsTable(new String[][] {
                {"Booking", bookingNumber}, {"Invoice", invoiceNumber}, {"Room", room},
                {"Stay", stay}, {"Total charged", total},
                {"Payments received", paidLabel}, {"Balance", balanceLabel}});
        String innerHtml = "<p>Dear " + EmailLayout.htmlEscape(guestName) + ",</p>"
                + "<p>Thank you for staying with us. Here is your receipt.</p>"
                + details
                + "<p>You can review your bookings any time in your guest portal.</p>";
        String innerText = "Dear " + guestName
                + ",\nThank you for staying with us. Here is your receipt.\n"
                + "Booking: " + bookingNumber + "\nInvoice: " + invoiceNumber
                + "\nStay: " + stay + "\nTotal charged: " + total
                + "\nPayments received: " + paidLabel + "\nBalance: " + balanceLabel;
        EmailLayout.RenderedEmail rendered = EmailLayout.render(props,
                "Receipt for your " + hotel + " stay " + bookingNumber + ".",
                "Your receipt", innerHtml, innerText,
                new Cta("View your booking", portal));
        String bodyHtml = rendered.html() + unsubscribeFooterHtml(guestId);
        schedulerTx.insertDelivery(new DeliveryValues(
                null, "checkout_receipt", guestId, "checkout_receipt", recipient,
                subject, bodyHtml, rendered.text(), null,
                "checkout-receipt:" + invoiceNumber));
    }

    /** Best-effort wrapper around the checkout receipt. */
    public void tryQueueCheckoutReceiptEmail(long bookingId, String invoiceNumber) {
        try {
            queueCheckoutReceiptEmail(bookingId, invoiceNumber);
        } catch (Exception e) {
            log.warn("Failed to queue checkout receipt for booking {}: {}",
                    bookingId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Receipt request — {@code queue_payment_receipt_request_notification}
    // ------------------------------------------------------------------

    /**
     * Notify the guest each time staff request or re-request proof of a bank
     * transfer. Never throws: a mail hiccup must not undo the staff action.
     */
    public void queuePaymentReceiptRequestNotification(Long guestId, String guestName,
            long bookingId, String bookingNumber, long paymentId, String message) {
        if (guestId == null) {
            return;
        }
        String email;
        try {
            email = guestEmail(guestId);
        } catch (Exception e) {
            log.error("Failed to find guest email for payment receipt request: {}",
                    e.getMessage());
            return;
        }
        if (email == null) {
            return;
        }
        String booking = bookingNumber == null ? "your booking" : bookingNumber;
        String note = message == null
                ? "Please upload a clear receipt showing the transfer reference and date."
                : message;
        String accessToken = issueAnonymousReceiptUploadToken(guestId, bookingId);
        String[] mail = receiptRequestMail(
                guestName == null ? "Guest" : guestName, booking, note, accessToken);
        String idempotencyKey = "payment-receipt-request:" + paymentId + ":"
                + System.currentTimeMillis();
        try {
            schedulerTx.insertDelivery(new DeliveryValues(
                    null, "booking_confirmation", guestId, "booking_confirmation",
                    email, mail[0], mail[1], mail[2], null, idempotencyKey));
        } catch (Exception e) {
            log.error("Failed to queue payment receipt request notification: {}",
                    e.getMessage());
        }
    }

    /**
     * Mint a fresh booking access token when this guest has no portal account.
     * The stored token is hashed, so a later request cannot reuse the original
     * — reissuing is the same move anonymous create uses.
     */
    private String issueAnonymousReceiptUploadToken(long guestId, long bookingId) {
        if (guestHasPortalAccount(guestId)) {
            return null;
        }
        List<LocalDate> dates = jdbc.query(
                "SELECT check_in_date FROM bookings WHERE id = ?",
                (rs, i) -> rs.getObject(1, LocalDate.class), bookingId);
        if (dates.isEmpty()) {
            return null;
        }
        return issueBookingAccessToken(bookingId, dates.get(0));
    }

    /** Branded receipt-request mail: portal CTA for account holders, upload link otherwise. */
    private String[] receiptRequestMail(String guestName, String booking, String message,
            String accessToken) {
        String hotel = EmailLayout.hotelDisplayName(props);
        String subject = "Receipt requested · " + hotel + " " + booking;
        String token = accessToken == null ? "" : accessToken.trim();
        String ctaUrl = token.isEmpty()
                ? EmailLayout.absoluteUrl(props, "/portal")
                : EmailLayout.absoluteUrl(props, "/guest-checkin/form?token=" + token);
        String ctaLabel = token.isEmpty() ? "Open guest portal" : "Upload receipt";
        String innerHtml = "<p>Dear " + EmailLayout.htmlEscape(guestName) + ",</p>"
                + "<p>Please upload your bank-transfer receipt for booking <strong>"
                + EmailLayout.htmlEscape(booking)
                + "</strong> within 24 hours.</p><p>"
                + EmailLayout.htmlEscape(message) + "</p>";
        String innerText = "Dear " + guestName
                + ",\nPlease upload your bank-transfer receipt for booking " + booking
                + " within 24 hours.\n" + message;
        EmailLayout.RenderedEmail rendered = EmailLayout.render(props,
                "Please upload your transfer receipt for " + hotel + " reservation "
                        + booking + ".",
                "Receipt needed", innerHtml, innerText, new Cta(ctaLabel, ctaUrl));
        return new String[] {subject, rendered.html(), rendered.text()};
    }

    // ------------------------------------------------------------------
    // Payment rejected — {@code queue_payment_rejected_notification}
    // ------------------------------------------------------------------

    /** Guest-facing mail explaining why their payment claim was rejected. */
    public void queuePaymentRejectedNotification(long guestId, String guestName,
            long bookingId, String bookingNumber, long paymentId, String reason) {
        String email;
        try {
            email = guestEmail(guestId);
        } catch (Exception e) {
            log.error("Failed to find guest email for payment rejection: {}",
                    e.getMessage());
            return;
        }
        if (email == null) {
            return;
        }

        // An anonymous booker has no account, so "log in" is advice they
        // cannot follow — offer a scoped recovery link when the booking can
        // still take one. A mint failure must never suppress the notice.
        Map<String, Object> link;
        try {
            link = recovery.issueRecoveryLink(bookingId, paymentId);
        } catch (Exception e) {
            log.error("Failed to issue payment recovery link for booking {}: {}",
                    bookingId, e.getMessage());
            link = null;
        }

        String hotel = EmailLayout.hotelDisplayName(props);
        String subject = "Payment update · " + hotel + " " + bookingNumber;
        String portal = EmailLayout.absoluteUrl(props, "/portal");
        String sealHtml = EmailLayout.identitySealHtml(props);
        String sealText = EmailLayout.identitySealText(props);

        String expiryLabel = null;
        if (link != null && link.get("expires_at") instanceof OffsetDateTime expires) {
            expiryLabel = expires.atZoneSameInstant(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'",
                            java.util.Locale.ENGLISH));
        }
        String[][] rows = expiryLabel == null
                ? new String[][] {{"Booking", bookingNumber}, {"Reason", reason}}
                : new String[][] {{"Booking", bookingNumber}, {"Reason", reason},
                        {"Link valid until", expiryLabel}};
        String details = EmailLayout.detailsTable(rows);

        String actionHtml;
        String actionText;
        Cta cta;
        if (link != null) {
            actionHtml = "<p>You can pay again using the button below. The link works once and "
                    + "then expires, so please do not share it.</p>";
            actionText = "You can pay again using the link below. It works once and then expires, "
                    + "so please do not share it.";
            cta = new Cta("Complete your payment", String.valueOf(link.get("url")));
        } else {
            actionHtml = "<p>Please contact the hotel and we will help you complete this "
                    + "booking.</p>";
            actionText = "Please contact the hotel and we will help you complete this booking.";
            cta = new Cta("Open guest portal", portal);
        }

        String innerHtml = "<p>Dear " + EmailLayout.htmlEscape(guestName) + ",</p>"
                + "<p>We were unable to confirm your recent payment for booking <strong>"
                + EmailLayout.htmlEscape(bookingNumber) + ".</strong></p>"
                + details + actionHtml + sealHtml;
        String innerText = "Dear " + guestName
                + ",\nWe were unable to confirm your recent payment for booking "
                + bookingNumber + ".\nReason: " + reason + "\n" + actionText
                + "\n\n" + sealText;
        EmailLayout.RenderedEmail rendered = EmailLayout.render(props,
                "An update on your payment for " + hotel + " reservation "
                        + bookingNumber + ".",
                "Payment not confirmed", innerHtml, innerText, cta);
        schedulerTx.insertDelivery(new DeliveryValues(
                null, "booking_confirmation", guestId, "booking_confirmation",
                email, subject, rendered.html(), rendered.text(), null,
                "payment-rejected:" + paymentId));
    }

    /**
     * Best-effort wrapper: a notification failure must never undo a staff
     * rejection that has already been committed.
     */
    public void tryQueuePaymentRejectedNotification(Long guestId, String guestName,
            long bookingId, String bookingNumber, long paymentId, String reason) {
        if (guestId == null) {
            return;
        }
        try {
            queuePaymentRejectedNotification(guestId, guestName, bookingId,
                    bookingNumber, paymentId, reason);
        } catch (Exception e) {
            log.error("Failed to queue payment rejected notification for payment {}: {}",
                    paymentId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * {@code guest_has_portal_account} — whether this guest can sign in to the
     * portal. Errors assume an account exists: the portal fallback link is
     * merely unhelpful, where a wrongly-minted token would rotate a live one
     * out from under the guest.
     */
    boolean guestHasPortalAccount(long guestId) {
        try {
            List<Long> rows = jdbc.query("""
                    SELECT id FROM users
                    WHERE guest_id = ? AND user_type::text = 'guest'
                      AND is_active = true
                    ORDER BY id LIMIT 1
                    """, (rs, i) -> rs.getLong(1), guestId);
            return !rows.isEmpty();
        } catch (Exception e) {
            log.error("Failed to resolve portal account for guest {}: {}",
                    guestId, e.getMessage());
            return true;
        }
    }

    /**
     * {@code issue_booking_access_token}: returns null rather than throwing —
     * an email must still go out with a lookup-page link if issuance fails.
     */
    String issueBookingAccessToken(long bookingId, LocalDate checkInDate) {
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

    /** {@code get_guest_email}: the guest's email, or null when gone/blank. */
    private String guestEmail(long guestId) {
        List<String> rows = jdbc.query("SELECT email FROM guests WHERE id = ?",
                (rs, i) -> rs.getString(1), guestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code unsubscribe_footer_html}: empty string when signing fails. */
    private String unsubscribeFooterHtml(long guestId) {
        return tokens.footerHtml(guestId);
    }
}
