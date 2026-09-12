package com.hotelapp.guestbooking;

import com.hotelapp.communications.GuestComms;
import com.hotelapp.consent.Consents;
import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.settings.HotelSettings;
import com.hotelapp.core.text.Sanitizer;
import com.hotelapp.email.EmailLayout;
import com.hotelapp.guestbooking.FunnelModels.AnonymousBookingRequest;
import com.hotelapp.guestbooking.FunnelModels.AvailabilityEvent;
import com.hotelapp.guestbooking.FunnelModels.BookingInsert;
import com.hotelapp.guestbooking.FunnelModels.BookingQuoteRequest;
import com.hotelapp.guestbooking.FunnelModels.CreateGuestBookingRequest;
import com.hotelapp.guestbooking.FunnelModels.GuestBookingConfirmation;
import com.hotelapp.guestbooking.FunnelModels.GuestBookingOffer;
import com.hotelapp.guestbooking.FunnelModels.GuestBookingQuote;
import com.hotelapp.guestbooking.FunnelModels.GuestBookingVoucherOptions;
import com.hotelapp.guestbooking.FunnelModels.GuestContact;
import com.hotelapp.guestbooking.FunnelModels.NightlyRate;
import com.hotelapp.guestbooking.FunnelModels.BulkUpdateOnlineInventoryRequest;
import com.hotelapp.guestbooking.FunnelModels.OnlineInventoryAllocation;
import com.hotelapp.guestbooking.FunnelModels.OnlineInventoryCellUpdate;
import com.hotelapp.guestbooking.FunnelModels.RoomTypeInventory;
import com.hotelapp.guestbooking.FunnelModels.UpdateOnlineInventoryRequest;
import com.hotelapp.guestbooking.FunnelModels.ValidatedAnonymousGuest;
import com.hotelapp.guestbooking.FunnelModels.ValidatedStay;
import com.hotelapp.guestbooking.FunnelModels.VoucherPricing;
import com.hotelapp.portal.PortalAuth;
import com.hotelapp.portal.PortalService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Port of {@code modules/guest_booking/service.rs}: public/session search,
 * quotes (with vouchers + complimentary credits), the two booking-create
 * flows, online-inventory admin, and the confirmation mail composer.
 */
@Service
public class FunnelService {

    /** Upstream PORTAL_SOURCE. */
    public static final String PORTAL_SOURCE = "website";

    /** Minimum life of an anonymous booking's access token. */
    public static final long ANONYMOUS_ACCESS_TOKEN_DAYS = 14;

    /** Mirrors VERIFY_REISSUE_WINDOW_DAYS — verify_guest_booking's window. */
    public static final long VERIFY_REISSUE_WINDOW_DAYS = 7;

    private final JdbcTemplate jdbc;
    private final FunnelValidation validation;
    private final FunnelBookingTx bookingTx;
    private final PortalService portalService;
    private final HotelSettings settings;
    private final AppProperties props;
    private final GuestComms guestComms;
    private final AvailabilityHub hub;

    public FunnelService(JdbcTemplate jdbc, FunnelValidation validation,
            FunnelBookingTx bookingTx, PortalService portalService,
            HotelSettings settings, AppProperties props,
            GuestComms guestComms, AvailabilityHub hub) {
        this.jdbc = jdbc;
        this.validation = validation;
        this.bookingTx = bookingTx;
        this.portalService = portalService;
        this.settings = settings;
        this.props = props;
        this.guestComms = guestComms;
        this.hub = hub;
    }

    // ------------------------------------------------------------------
    // Pure pricing helpers (unit-tested contracts)
    // ------------------------------------------------------------------

    /** {@code tourism_tax_for_type} — rate × billable nights for foreign guests. */
    public static BigDecimal tourismTaxForType(String tourismType, long nights, BigDecimal rate) {
        if (!isForeignTourist(tourismType)) {
            return BigDecimal.ZERO;
        }
        return rate.multiply(BigDecimal.valueOf(Math.max(nights, 1)));
    }

    /** {@code is_foreign_tourist}. */
    public static boolean isForeignTourist(String tourismType) {
        return tourismType != null && tourismType.equalsIgnoreCase("foreign");
    }

    /** {@code anonymous_access_token_expiry} — the two windows meet with no gap. */
    public static OffsetDateTime anonymousAccessTokenExpiry(OffsetDateTime now, LocalDate checkIn) {
        OffsetDateTime minimum = now.plus(ANONYMOUS_ACCESS_TOKEN_DAYS, ChronoUnit.DAYS);
        OffsetDateTime verifyOpens = checkIn.atStartOfDay().atOffset(ZoneOffset.UTC)
                .minus(VERIFY_REISSUE_WINDOW_DAYS, ChronoUnit.DAYS);
        return verifyOpens.isAfter(minimum) ? verifyOpens : minimum;
    }

    /** {@code replay_anonymous_access_token_expiry} — keep a still-valid deadline. */
    public static OffsetDateTime replayAnonymousAccessTokenExpiry(OffsetDateTime now,
            LocalDate checkIn, OffsetDateTime storedExpiry) {
        if (storedExpiry != null && storedExpiry.isAfter(now)) {
            return storedExpiry;
        }
        return anonymousAccessTokenExpiry(now, checkIn);
    }

    /** {@code voucher_discount} — percentage or fixed, capped, never below zero. */
    public static BigDecimal voucherDiscount(BigDecimal subtotal, VoucherPricing voucher) {
        BigDecimal discount = "percentage".equals(voucher.discountType())
                ? subtotal.multiply(voucher.discountValue())
                        .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                : voucher.discountValue();
        if (voucher.maxDiscountAmount() != null) {
            discount = discount.min(voucher.maxDiscountAmount());
        }
        return discount.min(subtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** {@code complimentary_discount} — a credit is worth the night it is spent on. */
    public static BigDecimal complimentaryDiscount(List<NightlyRate> nightlyRates,
            List<LocalDate> dates) {
        BigDecimal total = BigDecimal.ZERO;
        for (NightlyRate rate : nightlyRates) {
            if (dates.contains(rate.date())) {
                total = total.add(rate.amount());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * {@code settlement} — credits settle their nights first, then the voucher
     * discounts what remains. Returns {discountAmount, totalAmount}.
     */
    public static BigDecimal[] settlement(BigDecimal subtotal, BigDecimal complimentaryDiscount,
            VoucherPricing voucher) {
        BigDecimal payableSubtotal = subtotal.subtract(complimentaryDiscount).max(BigDecimal.ZERO);
        BigDecimal voucherAmount = voucher == null ? BigDecimal.ZERO
                : voucherDiscount(payableSubtotal, voucher);
        BigDecimal discountAmount = complimentaryDiscount.add(voucherAmount)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal[] {discountAmount, totalAmount};
    }

    /** {@code generate_booking_number_for_date} — BK-YYYYMMDD-xxxxxxxx. */
    public static String generateBookingNumberForDate(LocalDate date) {
        return "BK-" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    // ------------------------------------------------------------------
    // Shared reads
    // ------------------------------------------------------------------

    /** Upstream `currency()` — trimmed uppercase 3-letter setting, else MYR. */
    public String currency() {
        String value = settings.getString("currency", "").trim().toUpperCase();
        return value.length() == 3 ? value : "MYR";
    }

    /** hotel_today — the connection tz carries the hotel business day. */
    public LocalDate hotelToday() {
        LocalDate today = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
        if (today == null) {
            throw ApiError.internal("Database clock unavailable");
        }
        return today;
    }

    private static BigDecimal dec(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal d) {
            return d;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(value.toString());
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        String text = value.toString();
        if (text.startsWith("[")) {
            try {
                List<?> parsed = new tools.jackson.databind.ObjectMapper()
                        .readValue(text, List.class);
                return parsed.stream().map(String::valueOf).toList();
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private RoomTypeInventory inventoryFromRow(Map<String, Object> row) {
        return new RoomTypeInventory(
                ((Number) row.get("id")).longValue(),
                (String) row.get("code"), (String) row.get("name"),
                (String) row.get("description"),
                dec(row.get("base_price")), dec(row.get("weekday_rate")),
                dec(row.get("weekend_rate")),
                row.get("max_occupancy") == null ? 2
                        : ((Number) row.get("max_occupancy")).intValue(),
                (String) row.get("bed_type"),
                row.get("bed_count") == null ? null : ((Number) row.get("bed_count")).intValue(),
                stringList(row.get("images")), stringList(row.get("features")),
                row.get("available_rooms") == null ? 0
                        : ((Number) row.get("available_rooms")).longValue());
    }

    /** {@code list_inventory}. */
    public List<RoomTypeInventory> listInventory(LocalDate checkIn, LocalDate checkOut,
            int occupancy) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT rt.id, rt.code, rt.name, rt.description,
                       rt.base_price::text AS base_price,
                       rt.weekday_rate::text AS weekday_rate,
                       rt.weekend_rate::text AS weekend_rate,
                       rt.max_occupancy, rt.bed_type, rt.bed_count,
                       rt.images, rt.features,
                       COUNT(r.id)::bigint AS available_rooms
                FROM room_types rt
                JOIN rooms r ON r.room_type_id = rt.id
                WHERE rt.is_active = true
                  AND rt.max_occupancy >= ?
                  AND r.is_active = true
                  AND COALESCE(r.status, 'available') NOT IN ('maintenance', 'out_of_order')
                  AND NOT EXISTS (
                    SELECT 1 FROM bookings b
                    WHERE b.room_id = r.id
                      AND b.status IN ('reserved', 'confirmed', 'checked_in', 'auto_checked_in',
                                       'pending', 'pending_payment', 'pending_confirmation')
                      AND b.check_in_date < ?
                      AND b.check_out_date > ?
                  )
                GROUP BY rt.id, rt.code, rt.name, rt.description, rt.base_price,
                         rt.weekday_rate, rt.weekend_rate, rt.max_occupancy,
                         rt.bed_type, rt.bed_count, rt.images, rt.features, rt.sort_order
                ORDER BY rt.sort_order, rt.name
                """, occupancy, checkOut, checkIn);
        return rows.stream().map(this::inventoryFromRow).toList();
    }

    /** {@code find_inventory} — Conflict when the type drops out of inventory. */
    public RoomTypeInventory findInventory(long roomTypeId, LocalDate checkIn,
            LocalDate checkOut, int occupancy) {
        return listInventory(checkIn, checkOut, occupancy).stream()
                .filter(rt -> rt.id() == roomTypeId).findFirst()
                .orElseThrow(() -> ApiError.conflict("This room type is no longer available"));
    }

    /** {@code applicable_rate} — the day's rate plan, if any. */
    public Map<String, Object> applicableRate(long roomTypeId, LocalDate date) {
        int weekday = date.getDayOfWeek().getValue() - 1; // num_days_from_monday
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT rp.code, rr.price::text AS price
                FROM room_rates rr
                JOIN rate_plans rp ON rp.id = rr.rate_plan_id
                WHERE rr.room_type_id = ?
                  AND rp.is_active = true
                  AND rr.effective_from <= ?
                  AND (rr.effective_to IS NULL OR rr.effective_to >= ?)
                  AND (rp.valid_from IS NULL OR rp.valid_from <= ?)
                  AND (rp.valid_to IS NULL OR rp.valid_to >= ?)
                  AND ((? = 0 AND rp.applies_monday = true)
                    OR (? = 1 AND rp.applies_tuesday = true)
                    OR (? = 2 AND rp.applies_wednesday = true)
                    OR (? = 3 AND rp.applies_thursday = true)
                    OR (? = 4 AND rp.applies_friday = true)
                    OR (? = 5 AND rp.applies_saturday = true)
                    OR (? = 6 AND rp.applies_sunday = true))
                ORDER BY rp.priority DESC, rr.id DESC LIMIT 1
                """, roomTypeId, date, date, date, date, weekday, weekday, weekday, weekday,
                weekday, weekday, weekday);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private BigDecimal baseRateForDate(RoomTypeInventory roomType, LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return roomType.weekendRate() != null ? roomType.weekendRate() : roomType.basePrice();
        }
        return roomType.weekdayRate() != null ? roomType.weekdayRate() : roomType.basePrice();
    }

    /** {@code online_custom_prices_for_stay}. */
    public Map<LocalDate, BigDecimal> onlineCustomPricesForStay(long roomTypeId,
            LocalDate checkIn, LocalDate checkOut) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT stay_date, custom_price::text AS custom_price
                FROM online_inventory_allocations
                WHERE room_type_id = ? AND stay_date >= ? AND stay_date < ?
                  AND custom_price IS NOT NULL
                """, roomTypeId, checkIn, checkOut);
        Map<LocalDate, BigDecimal> prices = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            prices.put(((java.sql.Date) row.get("stay_date")).toLocalDate(),
                    dec(row.get("custom_price")));
        }
        return prices;
    }

    private List<NightlyRate> nightlyRates(RoomTypeInventory roomType, ValidatedStay stay) {
        Map<LocalDate, BigDecimal> customPrices = onlineCustomPricesForStay(
                roomType.id(), stay.checkInDate(), stay.checkOutDate());
        List<NightlyRate> rates = new ArrayList<>();
        for (LocalDate date = stay.checkInDate(); date.isBefore(stay.checkOutDate());
                date = date.plusDays(1)) {
            String planCode;
            BigDecimal amount;
            if (customPrices.containsKey(date)) {
                planCode = "ONLINE_CUSTOM";
                amount = customPrices.get(date);
            } else {
                Map<String, Object> rate = applicableRate(roomType.id(), date);
                if (rate != null) {
                    planCode = (String) rate.get("code");
                    amount = dec(rate.get("price"));
                } else {
                    planCode = "BASE";
                    amount = baseRateForDate(roomType, date);
                }
            }
            rates.add(new NightlyRate(date, planCode, amount));
        }
        return rates;
    }

    /** {@code online_allocation_for_stay} → (reserved, enabled). */
    private Map<String, Object> onlineAllocationForStay(long roomTypeId, LocalDate checkIn,
            LocalDate checkOut) {
        return jdbc.queryForMap("""
                SELECT COALESCE(MAX(walk_in_reserved_rooms), 0)::bigint AS reserved,
                       COALESCE(BOOL_AND(online_booking_enabled), true) AS enabled
                FROM online_inventory_allocations
                WHERE room_type_id = ? AND stay_date >= ? AND stay_date < ?
                """, roomTypeId, checkIn, checkOut);
    }

    private RoomTypeInventory applyOnlineAllocation(RoomTypeInventory roomType, ValidatedStay stay) {
        Map<String, Object> allocation = onlineAllocationForStay(
                roomType.id(), stay.checkInDate(), stay.checkOutDate());
        long reserved = ((Number) allocation.get("reserved")).longValue();
        boolean enabled = Boolean.TRUE.equals(allocation.get("enabled"));
        return roomType.withAvailableRooms(
                enabled ? Math.max(roomType.availableRooms() - reserved, 0) : 0);
    }

    /** {@code complimentary_credits_available}. */
    public int complimentaryCreditsAvailable(long guestId, long roomTypeId) {
        Integer nights = jdbc.query(
                "SELECT nights_available FROM guest_complimentary_credits "
                        + "WHERE guest_id = ? AND room_type_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, guestId, roomTypeId);
        return nights == null ? 0 : nights;
    }

    /** {@code guest_tourism_type}. */
    public String guestTourismType(long guestId) {
        List<String> rows = jdbc.query(
                "SELECT tourism_type::text FROM guests WHERE id = ?",
                (rs, i) -> rs.getString(1), guestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code guest_contact}. */
    public GuestContact guestContact(long guestId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT g.nick_name, g.email,
                       (SELECT u.id FROM users u
                        WHERE u.guest_id = g.id AND u.user_type::text = 'guest'
                        ORDER BY u.id LIMIT 1) AS actor_user_id
                FROM guests g WHERE g.id = ?
                """, guestId);
        return new GuestContact(
                row.get("actor_user_id") == null ? null
                        : ((Number) row.get("actor_user_id")).longValue(),
                (String) row.get("nick_name"), (String) row.get("email"));
    }

    /** {@code direct_booking_channel}. */
    public Long directBookingChannel() {
        List<Long> rows = jdbc.query("""
                SELECT id FROM booking_channels
                WHERE is_active = true AND (LOWER(name) IN ('website', 'direct website', 'direct')
                  OR channel_type = 'direct')
                ORDER BY CASE LOWER(name) WHEN 'direct website' THEN 0 WHEN 'website' THEN 1
                    ELSE 2 END, id
                LIMIT 1
                """, (rs, i) -> rs.getLong(1));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code available_count}. */
    public long availableCount(long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        return listInventory(checkIn, checkOut, 1).stream()
                .filter(rt -> rt.id() == roomTypeId).findFirst()
                .map(RoomTypeInventory::availableRooms).orElse(0L);
    }

    // ------------------------------------------------------------------
    // Vouchers
    // ------------------------------------------------------------------

    /** Structural twin of upstream's {@code VoucherEligibilityQuery}. */
    public record VoucherQueryLike(long guestId, long roomTypeId, LocalDate checkIn,
            LocalDate checkOut, long nights, BigDecimal subtotal, String currency) {
    }

    /** {@code eligible_voucher} — Conflict when it does not qualify. */
    public VoucherPricing eligibleVoucher(long voucherId, VoucherQueryLike query) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT v.id AS voucher_id, p.id AS promotion_id, p.name AS promotion_name,
                       p.discount_type, p.discount_value::text AS discount_value,
                       p.max_discount_amount::text AS max_discount_amount
                FROM vouchers v JOIN promotions p ON p.id = v.promotion_id
                WHERE v.id = ? AND v.guest_id = ? AND v.status = 'available'
                  AND (v.expires_at IS NULL OR v.expires_at > CURRENT_TIMESTAMP)
                  AND p.status = 'published'
                  AND (p.stay_starts_on IS NULL OR p.stay_starts_on <= ?)
                  AND (p.stay_ends_on IS NULL OR p.stay_ends_on >= ?)
                  AND (p.min_nights IS NULL OR p.min_nights <= ?)
                  AND (p.max_nights IS NULL OR p.max_nights >= ?)
                  AND p.min_subtotal <= ?
                  AND p.currency = ?
                  AND (NOT EXISTS (SELECT 1 FROM promotion_room_types pr0
                        WHERE pr0.promotion_id = p.id)
                    OR EXISTS (SELECT 1 FROM promotion_room_types pr
                        WHERE pr.promotion_id = p.id AND pr.room_type_id = ?))
                """, voucherId, query.guestId(), query.checkIn(), query.checkOut(),
                query.nights(), query.nights(), query.subtotal(), query.currency(),
                query.roomTypeId());
        if (rows.isEmpty()) {
            throw ApiError.conflict("This voucher is not eligible for the selected stay");
        }
        Map<String, Object> row = rows.get(0);
        return new VoucherPricing(
                ((Number) row.get("voucher_id")).longValue(),
                ((Number) row.get("promotion_id")).longValue(),
                (String) row.get("promotion_name"), (String) row.get("discount_type"),
                dec(row.get("discount_value")), dec(row.get("max_discount_amount")));
    }

    /** {@code eligible_voucher_ids}. */
    public List<Long> eligibleVoucherIds(VoucherQueryLike query) {
        return jdbc.query("""
                SELECT v.id
                FROM vouchers v JOIN promotions p ON p.id = v.promotion_id
                WHERE v.guest_id = ? AND v.status = 'available'
                  AND (v.expires_at IS NULL OR v.expires_at > CURRENT_TIMESTAMP)
                  AND p.status = 'published'
                  AND (p.stay_starts_on IS NULL OR p.stay_starts_on <= ?)
                  AND (p.stay_ends_on IS NULL OR p.stay_ends_on >= ?)
                  AND (p.min_nights IS NULL OR p.min_nights <= ?)
                  AND (p.max_nights IS NULL OR p.max_nights >= ?)
                  AND p.min_subtotal <= ?
                  AND p.currency = ?
                  AND (NOT EXISTS (SELECT 1 FROM promotion_room_types pr0
                        WHERE pr0.promotion_id = p.id)
                    OR EXISTS (SELECT 1 FROM promotion_room_types pr
                        WHERE pr.promotion_id = p.id AND pr.room_type_id = ?))
                ORDER BY v.id
                """, (rs, i) -> rs.getLong(1), query.guestId(), query.checkIn(), query.checkOut(),
                query.nights(), query.nights(), query.subtotal(), query.currency(),
                query.roomTypeId());
    }

    // ------------------------------------------------------------------
    // Quote pipeline
    // ------------------------------------------------------------------

    /** The complimentary-night credits in play for one quote. */
    private record ComplimentaryContext(List<LocalDate> dates, int creditsAvailable) {
        static final ComplimentaryContext NONE = new ComplimentaryContext(List.of(), 0);
    }

    private ComplimentaryContext complimentaryContext(Long guestId, long roomTypeId,
            ValidatedStay stay, List<String> requestedDates) {
        if (guestId == null) {
            if (requestedDates != null && !requestedDates.isEmpty()) {
                throw ApiError.badRequest(
                        "Complimentary nights require a signed-in account.");
            }
            return ComplimentaryContext.NONE;
        }
        int creditsAvailable = complimentaryCreditsAvailable(guestId, roomTypeId);
        List<LocalDate> dates = validation.validateComplimentaryDates(requestedDates, stay);
        if (dates.size() > creditsAvailable) {
            throw ApiError.badRequest("You have " + creditsAvailable
                    + " complimentary night(s) for this room type but selected " + dates.size());
        }
        return new ComplimentaryContext(dates, creditsAvailable);
    }

    private VoucherPricing voucherForQuote(Long guestId, long roomTypeId, ValidatedStay stay,
            BigDecimal subtotal, String currency, Long voucherId) {
        if (voucherId == null) {
            return null;
        }
        if (guestId == null) {
            throw ApiError.badRequest("Vouchers require a signed-in account.");
        }
        return eligibleVoucher(voucherId, new VoucherQueryLike(guestId, roomTypeId,
                stay.checkInDate(), stay.checkOutDate(), stay.nights(), subtotal, currency));
    }

    private GuestBookingQuote quoteForInventory(Long guestId, RoomTypeInventory roomType,
            ValidatedStay stay, Long voucherId, ComplimentaryContext complimentary,
            String tourismType) {
        if (stay.adults() + stay.children() > roomType.maxOccupancy()) {
            throw ApiError.badRequest(
                    "The selected room type cannot accommodate this party");
        }
        String currency = currency();
        List<NightlyRate> nightlyRates = nightlyRates(roomType, stay);
        BigDecimal subtotal = nightlyRates.stream()
                .map(NightlyRate::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal complimentaryDiscount = complimentaryDiscount(nightlyRates, complimentary.dates());
        VoucherPricing voucher = voucherForQuote(guestId, roomType.id(), stay, subtotal,
                currency, voucherId);
        BigDecimal[] settled = settlement(subtotal, complimentaryDiscount, voucher);
        BigDecimal discountAmount = settled[0];
        BigDecimal roomTotal = settled[1];
        BigDecimal taxAmount = BigDecimal.ZERO;
        if (isForeignTourist(tourismType)) {
            BigDecimal rate = settings.getPositiveDecimal("tourism_tax_rate",
                    BigDecimal.valueOf(10));
            taxAmount = tourismTaxForType(tourismType, stay.nights(), rate);
        }
        BigDecimal totalAmount = roomTotal.add(taxAmount);
        return new GuestBookingQuote(
                roomType.id(), roomType.code(), roomType.name(),
                stay.checkInDate(), stay.checkOutDate(), stay.adults(), stay.children(),
                currency, nightlyRates, subtotal, discountAmount, taxAmount, totalAmount,
                voucher == null ? null : voucher.voucherId(),
                voucher == null ? null : voucher.promotionName(),
                complimentary.dates(), complimentary.dates().size(), complimentaryDiscount,
                complimentary.creditsAvailable());
    }

    /**
     * {@code search} — every bookable room type priced for the stay. An
     * anonymous booker gets undiscounted list prices.
     */
    public List<GuestBookingOffer> search(Long guestId, String checkIn, String checkOut,
            Integer adults, Integer children) {
        ValidatedStay stay = validation.validateStay(checkIn, checkOut, adults, children,
                hotelToday());
        List<RoomTypeInventory> roomTypes = listInventory(stay.checkInDate(), stay.checkOutDate(),
                stay.adults() + stay.children());
        List<GuestBookingOffer> offers = new ArrayList<>(roomTypes.size());
        for (RoomTypeInventory raw : roomTypes) {
            RoomTypeInventory roomType = applyOnlineAllocation(raw, stay);
            if (roomType.availableRooms() == 0) {
                continue;
            }
            GuestBookingQuote quote = quoteForInventory(guestId, roomType, stay, null,
                    ComplimentaryContext.NONE, null);
            offers.add(new GuestBookingOffer(
                    roomType.id(), roomType.code(), roomType.name(), roomType.description(),
                    roomType.maxOccupancy(), roomType.bedType(), roomType.bedCount(),
                    roomType.images(), roomType.features(), roomType.availableRooms(),
                    quote.currency(), quote.nightlyRates(), quote.subtotal(),
                    quote.discountAmount(), quote.taxAmount(), quote.totalAmount()));
        }
        return offers;
    }

    /** {@code quote} — price one room type; {@code guestId} null for anonymous. */
    public GuestBookingQuote quote(Long guestId, BookingQuoteRequest request) {
        ValidatedStay stay = validation.validateStay(request.checkInDate(), request.checkOutDate(),
                request.adults(), request.children(), hotelToday());
        RoomTypeInventory roomType = applyOnlineAllocation(
                findInventory(request.roomTypeId(), stay.checkInDate(), stay.checkOutDate(),
                        stay.adults() + stay.children()), stay);
        if (roomType.availableRooms() == 0) {
            throw ApiError.conflict(
                    "This room type is reserved for walk-in guests or unavailable online");
        }
        ComplimentaryContext complimentary = complimentaryContext(guestId, roomType.id(), stay,
                request.complimentaryDates());
        String tourismType = resolveQuoteTourismType(guestId, request.tourismType());
        return quoteForInventory(guestId, roomType, stay, request.voucherId(), complimentary,
                tourismType);
    }

    private String resolveQuoteTourismType(Long guestId, String requested) {
        if (requested != null && !requested.trim().isEmpty()) {
            return requested.trim();
        }
        if (guestId == null) {
            return null;
        }
        return guestTourismType(guestId);
    }

    /** {@code quote_with_eligible_vouchers}. */
    public GuestBookingVoucherOptions quoteWithEligibleVouchers(long guestId,
            BookingQuoteRequest request) {
        GuestBookingQuote quote = quote(guestId, new BookingQuoteRequest(
                request.roomTypeId(), request.checkInDate(), request.checkOutDate(),
                request.adults(), request.children(), null, request.complimentaryDates(),
                request.tourismType()));
        List<Long> eligibleVoucherIds = eligibleVoucherIds(new VoucherQueryLike(
                guestId, quote.roomTypeId(), quote.checkInDate(), quote.checkOutDate(),
                quote.checkOutDate().toEpochDay() - quote.checkInDate().toEpochDay(),
                quote.subtotal(), quote.currency()));
        return new GuestBookingVoucherOptions(quote, eligibleVoucherIds);
    }

    // ------------------------------------------------------------------
    // Online inventory admin
    // ------------------------------------------------------------------

    /** {@code list_online_inventory} = range query over a single date. */
    public List<OnlineInventoryAllocation> listOnlineInventory(String stayDateRaw) {
        LocalDate stayDate;
        try {
            stayDate = LocalDate.parse(stayDateRaw == null ? "" : stayDateRaw.trim());
        } catch (Exception e) {
            throw ApiError.badRequest("Invalid stay date. Use YYYY-MM-DD");
        }
        return listOnlineInventoryRange(stayDate, stayDate);
    }

    /**
     * {@code list_online_inventory_range}: one row per active room type per
     * date in [from, to]. {@code standard_price} resolves the nightly rate a
     * guest pays without a custom override — weekday/weekend rate, then base
     * price; rate plans never feed public pricing.
     */
    public List<OnlineInventoryAllocation> listOnlineInventoryRange(LocalDate from,
            LocalDate to) {
        return jdbc.query("""
                WITH dates AS (
                    SELECT generate_series(?::date, ?::date, interval '1 day')::date AS stay_date
                )
                SELECT rt.id AS room_type_id, rt.code AS room_type_code, rt.name AS room_type_name,
                       d.stay_date,
                       COALESCE(avail.cnt, 0)::bigint AS physical_available_rooms,
                       COALESCE(a.walk_in_reserved_rooms, 0) AS walk_in_reserved_rooms,
                       COALESCE(a.online_booking_enabled, true) AS online_booking_enabled,
                       a.custom_price::text AS custom_price,
                       (a.room_type_id IS NOT NULL) AS is_override,
                       COALESCE(
                           CASE WHEN extract(isodow FROM d.stay_date) IN (6, 7)
                                THEN rt.weekend_rate ELSE rt.weekday_rate END,
                           rt.base_price)::text AS standard_price
                FROM room_types rt
                CROSS JOIN dates d
                LEFT JOIN LATERAL (
                    SELECT COUNT(*)::bigint AS cnt
                    FROM rooms r
                    WHERE r.room_type_id = rt.id AND r.is_active = true
                      AND COALESCE(r.status, 'available') NOT IN ('maintenance', 'out_of_order')
                      AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.room_id = r.id
                        AND b.status IN ('reserved', 'confirmed', 'checked_in', 'auto_checked_in',
                                         'pending', 'pending_payment', 'pending_confirmation')
                        AND b.check_in_date < d.stay_date + 1 AND b.check_out_date > d.stay_date)
                ) avail ON true
                LEFT JOIN online_inventory_allocations a
                  ON a.room_type_id = rt.id AND a.stay_date = d.stay_date
                WHERE rt.is_active = true
                ORDER BY rt.name, d.stay_date
                """, (rs, i) -> {
            LocalDate stayDate = rs.getObject("stay_date", LocalDate.class);
            long physical = rs.getLong("physical_available_rooms");
            int reserved = rs.getInt("walk_in_reserved_rooms");
            boolean enabled = rs.getBoolean("online_booking_enabled");
            return new OnlineInventoryAllocation(
                    rs.getLong("room_type_id"), rs.getString("room_type_code"),
                    rs.getString("room_type_name"), stayDate, physical, reserved, enabled,
                    dec(rs.getString("custom_price")), dec(rs.getString("standard_price")),
                    rs.getBoolean("is_override"),
                    enabled ? Math.max(physical - reserved, 0) : 0);
        }, from, to);
    }

    /** {@code validate_inventory_fields}. */
    private static void validateInventoryFields(int reserved, BigDecimal customPrice) {
        if (reserved < 0) {
            throw ApiError.badRequest("Walk-in reserve cannot be negative");
        }
        if (customPrice != null) {
            if (customPrice.signum() <= 0) {
                throw ApiError.badRequest("Custom online price must be greater than zero");
            }
            if (customPrice.scale() > 2) {
                throw ApiError.badRequest(
                        "Custom online price can have at most two decimal places");
            }
        }
    }

    /** {@code MAX_BULK_INVENTORY_CELLS}. */
    public static final int MAX_BULK_INVENTORY_CELLS = 500;

    /**
     * {@code resolve_bulk_cells}: validation is pure — dates, duplicates,
     * required-field and field-bound errors all surface before the write tx
     * opens.
     */
    static List<FunnelBookingTx.ResolvedCell> resolveBulkCells(
            List<OnlineInventoryCellUpdate> cells) {
        if (cells == null || cells.isEmpty()) {
            throw ApiError.badRequest("At least one cell is required");
        }
        if (cells.size() > MAX_BULK_INVENTORY_CELLS) {
            throw ApiError.badRequest("Too many cells in one update (max 500)");
        }
        Set<String> seen = new HashSet<>();
        List<FunnelBookingTx.ResolvedCell> resolved = new ArrayList<>(cells.size());
        for (OnlineInventoryCellUpdate cell : cells) {
            LocalDate stayDate;
            try {
                stayDate = LocalDate.parse(cell.stayDate() == null ? "" : cell.stayDate().trim());
            } catch (Exception e) {
                throw ApiError.badRequest("Invalid stay date. Use YYYY-MM-DD");
            }
            if (!seen.add(cell.roomTypeId() + "|" + stayDate)) {
                throw ApiError.badRequest("Duplicate cell for a room type and date");
            }
            if (Boolean.TRUE.equals(cell.reset())) {
                resolved.add(new FunnelBookingTx.ResolvedCell.Reset(cell.roomTypeId(), stayDate));
                continue;
            }
            if (cell.walkInReservedRooms() == null || cell.onlineBookingEnabled() == null) {
                throw ApiError.badRequest(
                        "walk_in_reserved_rooms and online_booking_enabled are required");
            }
            validateInventoryFields(cell.walkInReservedRooms(), cell.customPrice());
            resolved.add(new FunnelBookingTx.ResolvedCell.Set(cell.roomTypeId(), stayDate,
                    cell.walkInReservedRooms(), cell.onlineBookingEnabled(),
                    cell.customPrice()));
        }
        return resolved;
    }

    /**
     * {@code bulk_update_online_inventory}: one all-or-nothing tx, one
     * availability_changed publish per touched room type over its [first,
     * last+1) span, then the refreshed rows for the touched types only.
     */
    public List<OnlineInventoryAllocation> bulkUpdateOnlineInventory(
            BulkUpdateOnlineInventoryRequest request, long actorId) {
        List<FunnelBookingTx.ResolvedCell> resolved = resolveBulkCells(request.cells());

        Map<Long, LocalDate[]> spanByRoomType = new TreeMap<>();
        for (FunnelBookingTx.ResolvedCell cell : resolved) {
            long roomTypeId;
            LocalDate stayDate;
            if (cell instanceof FunnelBookingTx.ResolvedCell.Set set) {
                roomTypeId = set.roomTypeId();
                stayDate = set.stayDate();
            } else {
                FunnelBookingTx.ResolvedCell.Reset reset =
                        (FunnelBookingTx.ResolvedCell.Reset) cell;
                roomTypeId = reset.roomTypeId();
                stayDate = reset.stayDate();
            }
            spanByRoomType.compute(roomTypeId, (k, span) -> span == null
                    ? new LocalDate[]{stayDate, stayDate}
                    : new LocalDate[]{span[0].isBefore(stayDate) ? span[0] : stayDate,
                            span[1].isAfter(stayDate) ? span[1] : stayDate});
        }
        LocalDate minDate = spanByRoomType.values().stream().map(s -> s[0])
                .min(LocalDate::compareTo).orElseThrow();
        LocalDate maxDate = spanByRoomType.values().stream().map(s -> s[1])
                .max(LocalDate::compareTo).orElseThrow();

        bookingTx.bulkUpdateOnlineInventory(resolved, actorId,
                List.copyOf(spanByRoomType.keySet()), minDate, maxDate);

        for (Map.Entry<Long, LocalDate[]> entry : spanByRoomType.entrySet()) {
            hub.publish(new AvailabilityEvent(UUID.randomUUID().toString(),
                    "availability_changed", "online_inventory_changed", entry.getKey(),
                    entry.getValue()[0], entry.getValue()[1].plusDays(1), null));
        }
        Set<Long> roomTypeIds = spanByRoomType.keySet();
        return listOnlineInventoryRange(minDate, maxDate).stream()
                .filter(a -> roomTypeIds.contains(a.roomTypeId()))
                .toList();
    }

    /** {@code update_online_inventory} + the availability_changed publish. */
    public OnlineInventoryAllocation updateOnlineInventory(long roomTypeId, String stayDateRaw,
            UpdateOnlineInventoryRequest request, long actorId) {
        validateInventoryFields(request.walkInReservedRooms(), request.customPrice());
        LocalDate stayDate;
        try {
            stayDate = LocalDate.parse(stayDateRaw == null ? "" : stayDateRaw.trim());
        } catch (Exception e) {
            throw ApiError.badRequest("Invalid stay date. Use YYYY-MM-DD");
        }
        bookingTx.updateOnlineInventoryTx(roomTypeId, stayDate, request.walkInReservedRooms(),
                request.onlineBookingEnabled(), request.customPrice(), actorId);
        OnlineInventoryAllocation allocation = listOnlineInventory(stayDate.toString()).stream()
                .filter(row -> row.roomTypeId() == roomTypeId).findFirst()
                .orElseThrow(() -> ApiError.notFound("Room type not found"));
        hub.publish(new AvailabilityEvent(UUID.randomUUID().toString(), "availability_changed",
                "online_inventory_changed", roomTypeId, allocation.stayDate(),
                allocation.stayDate().plusDays(1),
                (long) allocation.onlineAvailableRooms()));
        return allocation;
    }

    // ------------------------------------------------------------------
    // Booking creation
    // ------------------------------------------------------------------

    /** {@code find_by_request_id} — idempotent replay for a session booking. */
    public GuestBookingConfirmation findByRequestId(long guestId, String requestId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.id, b.booking_number, rt.name AS room_type_name,
                       b.check_in_date, b.check_out_date, b.status, b.payment_status,
                       b.currency, b.subtotal::text AS subtotal,
                       b.discount_amount::text AS discount_amount,
                       b.tax_amount::text AS tax_amount, b.total_amount::text AS total_amount,
                       COALESCE(b.tourism_tax_amount, 0)::text AS tourism_tax_amount,
                       b.created_at
                FROM bookings b
                JOIN rooms r ON r.id = b.room_id
                JOIN room_types rt ON rt.id = r.room_type_id
                WHERE b.guest_id = ? AND b.portal_request_id = ?
                """, guestId, requestId);
        return rows.isEmpty() ? null : confirmationFromRow(rows.get(0));
    }

    /** {@code find_anonymous_by_request_id} — keyed on request id + email. */
    public GuestBookingConfirmation findAnonymousByRequestId(String requestId, String email) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.id, b.booking_number, rt.name AS room_type_name,
                       b.check_in_date, b.check_out_date, b.status, b.payment_status,
                       b.currency, b.subtotal::text AS subtotal,
                       b.discount_amount::text AS discount_amount,
                       b.tax_amount::text AS tax_amount, b.total_amount::text AS total_amount,
                       COALESCE(b.tourism_tax_amount, 0)::text AS tourism_tax_amount,
                       b.created_at, b.pre_checkin_token_expires_at
                FROM bookings b
                JOIN rooms r ON r.id = b.room_id
                JOIN room_types rt ON rt.id = r.room_type_id
                JOIN guests g ON g.id = b.guest_id
                WHERE b.portal_request_id = ?
                  AND LOWER(TRIM(g.email)) = LOWER(TRIM(?))
                  AND g.deleted_at IS NULL
                """, requestId, email);
        return rows.isEmpty() ? null : confirmationFromRow(rows.get(0));
    }

    /** {@code confirmation_by_id}. */
    public GuestBookingConfirmation confirmationById(long bookingId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT b.id, b.booking_number, rt.name AS room_type_name,
                       b.check_in_date, b.check_out_date, b.status, b.payment_status,
                       b.currency, b.subtotal::text AS subtotal,
                       b.discount_amount::text AS discount_amount,
                       b.tax_amount::text AS tax_amount, b.total_amount::text AS total_amount,
                       COALESCE(b.tourism_tax_amount, 0)::text AS tourism_tax_amount,
                       b.created_at
                FROM bookings b JOIN rooms r ON r.id = b.room_id
                JOIN room_types rt ON rt.id = r.room_type_id WHERE b.id = ?
                """, bookingId);
        return confirmationFromRow(row);
    }

    private GuestBookingConfirmation confirmationFromRow(Map<String, Object> row) {
        BigDecimal tourismTax = dec(row.get("tourism_tax_amount"));
        BigDecimal roomTotal = dec(row.get("total_amount"));
        return new GuestBookingConfirmation(
                ((Number) row.get("id")).longValue(), (String) row.get("booking_number"),
                (String) row.get("room_type_name"),
                ((java.sql.Date) row.get("check_in_date")).toLocalDate(),
                ((java.sql.Date) row.get("check_out_date")).toLocalDate(),
                (String) row.get("status"), (String) row.get("payment_status"),
                row.get("currency") == null ? "MYR" : (String) row.get("currency"),
                dec(row.get("subtotal")), dec(row.get("discount_amount")), tourismTax,
                roomTotal.add(tourismTax), row.get("created_at"), null,
                row.get("pre_checkin_token_expires_at"));
    }

    /** {@code update_precheckin_token} on the pool — replay reissues. */
    public void updatePrecheckinToken(long bookingId, String token, OffsetDateTime expiresAt) {
        jdbc.update("UPDATE bookings SET pre_checkin_token = ?, "
                        + "pre_checkin_token_expires_at = ? WHERE id = ?",
                PortalAuth.persistBookingAccessToken(token), expiresAt, bookingId);
    }

    /** 422 mapper for the booking profile guard. */
    private static ApiError profileIncompleteError(List<String> missingFields) {
        return ApiError.profileIncomplete(missingFields);
    }

    /**
     * {@code create} — a session-authenticated portal booking. Idempotent on
     * (guest_id, portal_request_id); requires a complete profile and consent.
     */
    public GuestBookingConfirmation create(long guestId, CreateGuestBookingRequest request,
            String ipAddress, String userAgent) {
        String requestId = validation.validateClientRequestId(request.clientRequestId());
        Consents.validateLocales(request.consents());
        Consents.requireConsents(request.consents(), Consents.BOOKING_REQUIRED);
        GuestBookingConfirmation existing = findByRequestId(guestId, requestId);
        if (existing != null) {
            return existing;
        }
        List<String> missing = portalService.missingProfileFields(guestId);
        if (!missing.isEmpty()) {
            throw profileIncompleteError(missing);
        }
        GuestBookingQuote quote = quote(guestId, new BookingQuoteRequest(
                request.roomTypeId(), request.checkInDate(), request.checkOutDate(),
                request.adults(), request.children(), request.voucherId(),
                request.complimentaryDates(), null));
        if (request.expectedTotal() == null) {
            throw ApiError.badRequest("expected_total is required");
        }
        if (quote.totalAmount().compareTo(expectedTotal(request.expectedTotal())) != 0) {
            throw ApiError.conflict(
                    "The booking price changed. Please review the refreshed total.");
        }

        GuestContact contact = guestContact(guestId);
        Long bookingChannelId = directBookingChannel();
        VoucherPricing voucherToRedeem = quote.voucherId() == null ? null
                : eligibleVoucher(quote.voucherId(), new VoucherQueryLike(guestId,
                        request.roomTypeId(), quote.checkInDate(), quote.checkOutDate(),
                        quote.checkOutDate().toEpochDay() - quote.checkInDate().toEpochDay(),
                        quote.subtotal(), quote.currency()));
        String specialRequests = request.specialRequests() == null ? null
                : cleanNotes(request.specialRequests());
        BigDecimal firstRate = quote.nightlyRates().isEmpty() ? null
                : quote.nightlyRates().get(0).amount();
        if (firstRate == null) {
            throw ApiError.badRequest("A booking requires at least one night");
        }
        Map<String, BigDecimal> dailyRates = new TreeMap<>();
        for (NightlyRate rate : quote.nightlyRates()) {
            dailyRates.put(rate.date().toString(), rate.amount());
        }
        String bookingNumber = generateBookingNumberForDate(quote.checkInDate());
        long stayNights = quote.checkOutDate().toEpochDay() - quote.checkInDate().toEpochDay();
        String complimentaryReason = quote.complimentaryNights() > 0
                ? "Guest portal: " + quote.complimentaryNights() + " of " + stayNights
                        + " night(s) funded by complimentary credits for " + quote.roomTypeName()
                        + " (dates: " + String.join(", ", quote.complimentaryDates().stream()
                                .map(LocalDate::toString).toList()) + ")"
                : null;
        boolean settledByCredits = quote.complimentaryNights() > 0
                && quote.totalAmount().signum() <= 0;
        String bookingStatus = settledByCredits ? "confirmed" : "pending_payment";

        String[] mail = contact.email() == null || contact.email().trim().isEmpty() ? null
                : portalBookingMail(contact.nickName(), bookingNumber, quote.roomTypeName(),
                        quote.checkInDate(), quote.checkOutDate(), quote.currency(),
                        quote.totalAmount(), settledByCredits, false, null);

        long bookingId;
        try {
            bookingId = bookingTx.persistSessionBooking(new FunnelBookingTx.SessionBookingPlan(
                    new BookingInsert(requestId, guestId, contact.actorUserId(), 0,
                            bookingNumber, quote.checkInDate(), quote.checkOutDate(),
                            quote.adults(), quote.children(), firstRate, quote.subtotal(),
                            quote.discountAmount(), quote.totalAmount().subtract(quote.taxAmount()),
                            quote.currency(), specialRequests, request.cleaningPreference(),
                            bookingChannelId, dailyRates, complimentaryReason, settledByCredits,
                            quote.taxAmount().signum() > 0, quote.taxAmount()),
                    request.roomTypeId(), quote.checkInDate(), quote.checkOutDate(),
                    voucherToRedeem, quote.complimentaryNights(), quote.subtotal(),
                    quote.discountAmount(), quote.totalAmount(), quote.complimentaryDiscount(),
                    request.consents(), ipAddress, userAgent, contact, bookingStatus,
                    mail == null ? null : mail[0], mail == null ? null : mail[1],
                    mail == null ? null : mail[2]));
        } catch (RuntimeException failure) {
            GuestBookingConfirmation replayed = findByRequestId(guestId, requestId);
            if (replayed != null) {
                return replayed;
            }
            throw failure;
        }

        GuestBookingConfirmation confirmation = confirmationById(bookingId);
        publishBookingCreated(request.roomTypeId(), quote.checkInDate(), quote.checkOutDate());
        return confirmation;
    }

    /**
     * {@code create_anonymous} — a booking for someone with no account. Fresh
     * guest profile every time (matching by email would leak the profile).
     */
    public GuestBookingConfirmation createAnonymous(AnonymousBookingRequest request,
            String ipAddress, String userAgent) {
        String requestId = validation.validateClientRequestId(request.clientRequestId());
        ValidatedAnonymousGuest guest = validation.validateAnonymousGuest(request.guest());
        Consents.validateLocales(request.consents());
        Consents.requireConsents(request.consents(), Consents.BOOKING_REQUIRED);

        GuestBookingConfirmation existing = findAnonymousByRequestId(requestId, guest.email());
        if (existing != null) {
            String accessToken = PortalAuth.generateSessionToken();
            OffsetDateTime expiresAt = replayAnonymousAccessTokenExpiry(
                    OffsetDateTime.now(ZoneOffset.UTC), existing.checkInDate(),
                    toOffset(existing.accessTokenExpiresAt()));
            updatePrecheckinToken(existing.bookingId(), accessToken, expiresAt);
            return existing.withAccessToken(accessToken, expiresAt);
        }

        GuestBookingQuote quote = quote(null, new BookingQuoteRequest(
                request.roomTypeId(), request.checkInDate(), request.checkOutDate(),
                request.adults(), request.children(), null, null, guest.tourismType()));
        if (request.expectedTotal() == null) {
            throw ApiError.badRequest("expected_total is required");
        }
        if (quote.totalAmount().compareTo(expectedTotal(request.expectedTotal())) != 0) {
            throw ApiError.conflict(
                    "The booking price changed. Please review the refreshed total.");
        }

        Long bookingChannelId = directBookingChannel();
        String specialRequests = request.specialRequests() == null ? null
                : cleanNotes(request.specialRequests());
        BigDecimal firstRate = quote.nightlyRates().isEmpty() ? null
                : quote.nightlyRates().get(0).amount();
        if (firstRate == null) {
            throw ApiError.badRequest("A booking requires at least one night");
        }
        Map<String, BigDecimal> dailyRates = new TreeMap<>();
        for (NightlyRate rate : quote.nightlyRates()) {
            dailyRates.put(rate.date().toString(), rate.amount());
        }
        String bookingNumber = generateBookingNumberForDate(quote.checkInDate());
        String accessToken = PortalAuth.generateSessionToken();
        OffsetDateTime accessTokenExpiresAt = anonymousAccessTokenExpiry(
                OffsetDateTime.now(ZoneOffset.UTC), quote.checkInDate());
        String languagePreference = Consents.preferredLocale(request.consents());

        String[] mail = portalBookingMail(guest.nickName(), bookingNumber, quote.roomTypeName(),
                quote.checkInDate(), quote.checkOutDate(), quote.currency(), quote.totalAmount(),
                false, true, accessToken);

        long bookingId = bookingTx.persistAnonymousBooking(new FunnelBookingTx.AnonymousBookingPlan(
                guest, languagePreference, accessToken, accessTokenExpiresAt,
                new BookingInsert(requestId, 0, null, 0, bookingNumber, quote.checkInDate(),
                        quote.checkOutDate(), quote.adults(), quote.children(), firstRate,
                        quote.subtotal(), quote.discountAmount(),
                        quote.totalAmount().subtract(quote.taxAmount()), quote.currency(),
                        specialRequests, request.cleaningPreference(), bookingChannelId,
                        dailyRates, null, false, isForeignTourist(guest.tourismType()),
                        quote.taxAmount()),
                request.roomTypeId(), quote.checkInDate(), quote.checkOutDate(),
                request.consents(), ipAddress, userAgent, bookingNumber, guest.email(),
                mail[0], mail[1], mail[2]));

        // Marketing preference outside the booking tx — a preference write must
        // not roll back a booking the guest is about to pay for.
        try {
            guestComms.recordSignupMarketingConsent(guestIdForBooking(bookingId),
                    Boolean.TRUE.equals(request.marketingOptIn()), "online_booking",
                    Consents.Document.PRIVACY_NOTICE.currentVersion(), ipAddress, userAgent);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(FunnelService.class)
                    .warn("Failed to record marketing preference for booking {}: {}",
                            bookingId, e.getMessage());
        }

        GuestBookingConfirmation confirmation = confirmationById(bookingId)
                .withAccessToken(accessToken, accessTokenExpiresAt);
        publishBookingCreated(request.roomTypeId(), quote.checkInDate(), quote.checkOutDate());
        return confirmation;
    }

    private long guestIdForBooking(long bookingId) {
        Long guestId = jdbc.queryForObject(
                "SELECT guest_id FROM bookings WHERE id = ?", Long.class, bookingId);
        return guestId == null ? 0 : guestId;
    }

    /** Upstream compares `quote.total_amount != expected_total.round_dp(2)`. */
    private static BigDecimal expectedTotal(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static OffsetDateTime toOffset(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(value.toString());
    }

    private static String cleanNotes(String value) {
        String cleaned = Sanitizer.sanitizeNotes(value);
        cleaned = cleaned == null ? "" : cleaned.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private void publishBookingCreated(long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        hub.publish(new AvailabilityEvent(UUID.randomUUID().toString(), "availability_changed",
                "booking_created", roomTypeId, checkIn, checkOut,
                availableCount(roomTypeId, checkIn, checkOut)));
    }

    // ------------------------------------------------------------------
    // Confirmation mail (portal_booking_mail)
    // ------------------------------------------------------------------

    private static String moneyLabel(String currency, BigDecimal amount) {
        String c = currency == null ? "" : currency.trim();
        return c.isEmpty() ? String.format("%.2f", amount)
                : c + " " + String.format("%.2f", amount);
    }

    /**
     * {@code portal_booking_mail} — returns {subject, html, text}. Token
     * deep-link for anonymous pending-payment bookings; plain portal link
     * otherwise.
     */
    public String[] portalBookingMail(String guestName, String bookingNumber,
            String roomTypeName, LocalDate checkIn, LocalDate checkOut, String currency,
            BigDecimal total, boolean settledByCredits, boolean anonymous, String accessToken) {
        String hotel = EmailLayout.hotelDisplayName(props);
        String stayIn = checkIn.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        String stayOut = checkOut.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        String totalLabel = moneyLabel(currency, total);
        String details = EmailLayout.detailsTable(new String[][] {
                {"Booking", bookingNumber},
                {"Room", roomTypeName},
                {"Check-in", stayIn},
                {"Check-out", stayOut},
                {"Total", totalLabel}});
        String viewUrl = EmailLayout.absoluteUrl(props, "/portal");
        String payUrl = accessToken != null && !accessToken.trim().isEmpty()
                ? EmailLayout.absoluteUrl(props, "/guest-checkin/form?token=" + accessToken)
                : EmailLayout.absoluteUrl(props, "/guest-checkin");

        String subject;
        String heading;
        String preheader;
        String introHtml;
        String closingHtml;
        String introText;
        String closingText;
        EmailLayout.Cta cta;
        if (settledByCredits) {
            subject = hotel + " reservation confirmed " + bookingNumber;
            heading = "Reservation confirmed";
            preheader = "Your " + hotel + " reservation " + bookingNumber + " is confirmed.";
            introHtml = "<p>Dear " + EmailLayout.htmlEscape(guestName)
                    + ",</p><p>Your reservation <strong>"
                    + EmailLayout.htmlEscape(bookingNumber)
                    + "</strong> is confirmed and fully covered by complimentary nights.</p>";
            closingHtml = "<p>There is nothing left to pay. You can view this booking any time "
                    + "in your guest portal.</p><p>If this message is not in your Primary inbox, "
                    + "check Spam and Promotions and mark it as not spam so the next one arrives.</p>";
            introText = "Dear " + guestName + ",\nYour reservation " + bookingNumber
                    + " is confirmed and fully covered by complimentary nights.";
            closingText = "There is nothing left to pay. You can view this booking any time in "
                    + "your guest portal.\nIf this message is not in your Primary inbox, check "
                    + "Spam and Promotions and mark it as not spam so the next one arrives.";
            cta = new EmailLayout.Cta("View your booking", viewUrl);
        } else {
            String spamHtml = "<p>If this message is not in your Primary inbox, check Spam and "
                    + "Promotions and mark it as not spam so the next one arrives.</p>";
            String spamText = "If this message is not in your Primary inbox, check Spam and "
                    + "Promotions and mark it as not spam so the next one arrives.";
            if (anonymous) {
                closingHtml = "<p>Please complete payment to confirm this stay.</p>"
                        + "<p>To view it later, use booking number <strong>"
                        + EmailLayout.htmlEscape(bookingNumber)
                        + "</strong> with this email address.</p>" + spamHtml;
                closingText = "Please complete payment to confirm this stay.\nTo view it later, "
                        + "use booking number " + bookingNumber + " with this email address.\n"
                        + spamText;
            } else {
                closingHtml = "<p>Please complete payment to confirm this stay. You can pay "
                        + "online from your guest portal or complete a bank transfer.</p>" + spamHtml;
                closingText = "Please complete payment to confirm this stay. You can pay online "
                        + "from your guest portal or complete a bank transfer.\n" + spamText;
            }
            subject = hotel + " reservation " + bookingNumber;
            heading = "Reservation received";
            preheader = "Your " + hotel + " reservation " + bookingNumber + " is pending payment.";
            introHtml = "<p>Dear " + EmailLayout.htmlEscape(guestName)
                    + ",</p><p>Your reservation <strong>"
                    + EmailLayout.htmlEscape(bookingNumber)
                    + "</strong> has been received and is pending payment.</p>";
            introText = "Dear " + guestName + ",\nYour reservation " + bookingNumber
                    + " has been received and is pending payment.";
            cta = new EmailLayout.Cta("Complete payment", anonymous ? payUrl : viewUrl);
        }

        String innerHtml = introHtml + details + closingHtml;
        String innerText = introText + "\n\nBooking: " + bookingNumber + "\nRoom: " + roomTypeName
                + "\nCheck-in: " + stayIn + "\nCheck-out: " + stayOut + "\nTotal: " + totalLabel
                + "\n\n" + closingText;
        EmailLayout.RenderedEmail rendered = EmailLayout.render(props, preheader, heading,
                innerHtml, innerText, cta);
        return new String[] {subject, rendered.html(), rendered.text()};
    }
}
