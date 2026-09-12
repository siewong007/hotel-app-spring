package com.hotelapp.bookings;

import com.hotelapp.billing.InvoiceNumbers;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.RbacService;
import com.hotelapp.core.settings.HotelSettings;
import com.hotelapp.core.text.Sanitizer;
import com.hotelapp.email.BookingEmails;
import com.hotelapp.loyalty.LoyaltyAwards;
import com.hotelapp.payments.PaymentRepo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Port of {@code repositories::bookings::lifecycle::update_booking_handler}:
 * validation and derived values happen pre-transaction, the money-critical
 * mutation runs in {@link BookingLifecycleTx}, and room-status/housekeeping/
 * invoice/loyalty/night-audit side effects are best-effort after commit.
 */
@Component
public class BookingLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BookingLifecycle.class);

    private final JdbcTemplate jdbc;
    private final HotelSettings settings;
    private final BookingLifecycleTx tx;
    private final PaymentRepo payments;
    private final BookingEmails bookingEmails;
    private final LoyaltyAwards loyaltyAwards;
    private final RbacService rbac;
    private final InvoiceNumbers invoiceNumbers;
    private final ObjectMapper objectMapper;

    public BookingLifecycle(JdbcTemplate jdbc, HotelSettings settings,
            BookingLifecycleTx tx, PaymentRepo payments, BookingEmails bookingEmails,
            LoyaltyAwards loyaltyAwards, RbacService rbac,
            InvoiceNumbers invoiceNumbers, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.tx = tx;
        this.payments = payments;
        this.bookingEmails = bookingEmails;
        this.loyaltyAwards = loyaltyAwards;
        this.rbac = rbac;
        this.invoiceNumbers = invoiceNumbers;
        this.objectMapper = objectMapper;
    }

    /** {@code update_booking_handler}. */
    public Map<String, Object> updateBooking(long userId, long bookingId,
            BookingUpdate input) {
        Map<String, Object> existing = fetchBooking(bookingId);

        boolean hasBookingUpdate = rbac.hasPermission(userId, "bookings:update");
        boolean ownsBooking = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM user_guests ug"
                        + " WHERE ug.user_id = ? AND ug.guest_id = ?)",
                Boolean.class, userId, ((Number) existing.get("guest_id")).longValue()));
        if (!hasBookingUpdate && !ownsBooking) {
            throw ApiError.forbidden(
                    "You don't have permission to modify this booking");
        }

        long oldRoomId = ((Number) existing.get("room_id")).longValue();
        long newRoomId = oldRoomId;
        if (input.roomId() != null) {
            try {
                newRoomId = Long.parseLong(input.roomId().trim());
            } catch (NumberFormatException e) {
                throw ApiError.badRequest("Invalid room");
            }
        }

        String newStatus = input.status() != null ? input.status()
                : String.valueOf(existing.get("status"));
        if ("cancelled".equals(newStatus) || "comp_cancelled".equals(newStatus)) {
            throw ApiError.badRequest(
                    "Use 'voided' for booking status or 'comp_void' for complimentary"
                            + " voids; 'cancelled' is no longer accepted.");
        }
        if ("cancelled".equals(input.paymentStatus())) {
            throw ApiError.badRequest(
                    "Use 'void' for payment status; 'cancelled' is no longer accepted.");
        }

        LocalDate checkIn = input.checkInDate() != null
                ? parseDateFlexible(input.checkInDate(), "check-in")
                : toLocalDate(existing.get("check_in_date"));
        LocalDate checkOut = input.checkOutDate() != null
                ? parseDateFlexible(input.checkOutDate(), "check-out")
                : toLocalDate(existing.get("check_out_date"));
        if ((input.checkInDate() != null || input.checkOutDate() != null)
                && checkOut.isBefore(checkIn)) {
            throw ApiError.badRequest(
                    "Check-out date must be on or after check-in date");
        }

        LocalDateTime actualCheckOutOverride = null;
        if (input.actualCheckOut() != null && !input.actualCheckOut().trim().isEmpty()) {
            actualCheckOutOverride = parseDateTimeFlexible(input.actualCheckOut());
        }

        boolean roomChanged = input.roomId() != null && newRoomId != oldRoomId;
        boolean datesChanged = input.checkInDate() != null
                || input.checkOutDate() != null;
        boolean inactiveStatus = List.of("voided", "checked_out", "late_checkout")
                .contains(newStatus);
        if ((roomChanged || datesChanged) && !inactiveStatus) {
            Boolean conflict = jdbc.queryForObject("""
                    SELECT EXISTS(
                        SELECT 1 FROM bookings
                        WHERE room_id = ? AND id != ?
                          AND status IN ('reserved', 'confirmed', 'checked_in',
                              'auto_checked_in', 'pending', 'pending_payment',
                              'pending_confirmation') AND status != 'voided'
                          AND ((check_in_date <= ? AND check_out_date > ?)
                              OR (check_in_date < ? AND check_out_date >= ?)
                              OR (check_in_date >= ? AND check_out_date <= ?))
                    )
                    """, Boolean.class, newRoomId, bookingId,
                    checkIn, checkIn, checkOut, checkOut, checkIn, checkOut);
            if (Boolean.TRUE.equals(conflict)) {
                throw ApiError.badRequest("Room is already booked for these dates");
            }
        }

        List<String> roomRows = jdbc.queryForList(
                "SELECT status FROM rooms WHERE id = ? AND is_active = true",
                String.class, newRoomId);
        if (roomRows.isEmpty()) {
            throw ApiError.notFound("Room not found");
        }
        String newRoomStatus = roomRows.get(0);
        if (!inactiveStatus && List.of("maintenance", "out_of_order").contains(newRoomStatus)) {
            throw ApiError.badRequest(
                    "Room is not available - currently " + newRoomStatus.replace('_', ' '));
        }

        String postType = checkIn.equals(checkOut) ? "hourly" : null;
        String newPaymentStatus = existing.get("payment_status") == null ? "unpaid"
                : String.valueOf(existing.get("payment_status"));

        Map<String, Object> dailyRatesJson = input.dailyRates();
        Map<String, Object> existingDailyRates = parseDailyRates(
                existing.get("daily_rates"));
        if (dailyRatesJson == null && datesChanged && checkIn.isBefore(checkOut)
                && existingDailyRates != null && !existingDailyRates.isEmpty()) {
            BigDecimal fallbackRate = existing.get("room_rate") == null ? BigDecimal.ZERO
                    : new BigDecimal(existing.get("room_rate").toString());
            Map<String, Object> rebuilt = new LinkedHashMap<>();
            for (LocalDate date = checkIn; date.isBefore(checkOut);
                    date = date.plusDays(1)) {
                String key = date.toString();
                Object value = existingDailyRates.get(key);
                rebuilt.put(key, value == null ? fallbackRate : value);
            }
            dailyRatesJson = rebuilt;
        }

        BigDecimal newRoomRate = null;
        BigDecimal newSubtotal = null;
        BigDecimal newTotalAmount = null;
        if (dailyRatesJson != null) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Object value : dailyRatesJson.values()) {
                BigDecimal parsed = value instanceof Number n
                        ? new BigDecimal(n.toString())
                        : value instanceof String s && !s.isBlank()
                                ? new BigDecimal(s.trim()) : null;
                if (parsed != null) {
                    sum = sum.add(parsed);
                }
            }
            newSubtotal = sum;
            newTotalAmount = sum;
            newRoomRate = input.roomRateOverride() != null
                    && Double.isFinite(input.roomRateOverride())
                    ? BigDecimal.valueOf(input.roomRateOverride())
                    : decOrNull(existing.get("room_rate"));
        } else if (input.roomRateOverride() != null) {
            long nights = Math.max(checkOut.toEpochDay() - checkIn.toEpochDay(), 1);
            newRoomRate = Double.isFinite(input.roomRateOverride())
                    ? BigDecimal.valueOf(input.roomRateOverride())
                    : decOrNull(existing.get("room_rate"));
            newSubtotal = newRoomRate.multiply(BigDecimal.valueOf(nights));
            newTotalAmount = newSubtotal;
        } else if (datesChanged) {
            long nights = Math.max(checkOut.toEpochDay() - checkIn.toEpochDay(), 1);
            BigDecimal rate = existing.get("room_rate") == null ? BigDecimal.ZERO
                    : new BigDecimal(existing.get("room_rate").toString());
            newSubtotal = rate.multiply(BigDecimal.valueOf(nights));
            newTotalAmount = newSubtotal;
        }

        long guestId = ((Number) existing.get("guest_id")).longValue();
        List<String> tourismRows = jdbc.queryForList(
                "SELECT tourism_type::text FROM guests WHERE id = ?",
                String.class, guestId);
        boolean canonicalIsTourist = !tourismRows.isEmpty()
                && "foreign".equals(tourismRows.get(0));
        BigDecimal canonicalTourismTax = canonicalIsTourist
                ? settings.getPositiveDecimal("tourism_tax_rate", BigDecimal.TEN)
                        .multiply(BigDecimal.valueOf(
                                Math.max(checkOut.toEpochDay() - checkIn.toEpochDay(), 1)))
                : BigDecimal.ZERO;

        boolean clearCompany = Boolean.TRUE.equals(input.clearCompany());
        String otaReference = sanitizeOtaReference(input.otaReference());

        ensureCheckoutBalanceResolved(bookingId, existing, input, newStatus,
                newTotalAmount);
        long defaultTermsDays = settings.getPositiveInt("default_payment_terms_days", 30);

        Map<String, Object> booking = tx.updateBookingTx(new BookingLifecycleTx.UpdateContext(
                bookingId, userId, input, existing, newRoomId, newStatus,
                checkIn, checkOut, postType, newPaymentStatus, dailyRatesJson,
                newRoomRate, newSubtotal, newTotalAmount, canonicalIsTourist,
                canonicalTourismTax, clearCompany, otaReference,
                actualCheckOutOverride, defaultTermsDays));

        applyPostCommitEffects(userId, bookingId, existing, booking,
                oldRoomId, newRoomId, newStatus, newRoomStatus);

        // Upstream returns the row captured inside the transaction — the
        // post-commit recompute updates the row for the NEXT read, so the
        // response still carries the pre-recompute payment_status.
        payments.recomputePaymentStatus(bookingId);
        return booking;
    }

    // ------------------------- post-commit effects ------------------------

    private void applyPostCommitEffects(long userId, long bookingId,
            Map<String, Object> existing, Map<String, Object> booking,
            long oldRoomId, long newRoomId, String updatedStatus, String newRoomStatus) {
        String oldStatus = String.valueOf(existing.get("status"));

        if (newRoomId != oldRoomId) {
            try {
                reconcileRoomStatusAfterBookingRelease(oldRoomId, bookingId);
            } catch (Exception e) {
                log.warn("Failed to reconcile old room {} after moving booking {}: {}",
                        oldRoomId, bookingId, e.getMessage());
            }
            if ("confirmed".equals(updatedStatus) || "pending".equals(updatedStatus)) {
                String reservedStatus = List.of("dirty", "cleaning", "reserved_dirty")
                        .contains(newRoomStatus) ? "reserved_dirty" : "reserved";
                jdbc.update("UPDATE rooms SET status = ? WHERE id = ?",
                        reservedStatus, newRoomId);
            }
        }

        if (!oldStatus.equals(updatedStatus)) {
            switch (updatedStatus) {
                case "voided" -> {
                    try {
                        reconcileRoomStatusAfterBookingRelease(newRoomId, bookingId);
                    } catch (Exception e) {
                        log.warn("Failed to reconcile room {} after voiding booking {}: {}",
                                newRoomId, bookingId, e.getMessage());
                    }
                    loyaltyAwards.tryReverseBookingPoints(bookingId, userId,
                            "Booking voided");
                }
                case "checked_out", "completed" -> {
                    boolean hasUpcoming = Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT EXISTS(
                                SELECT 1 FROM bookings
                                WHERE room_id = ? AND id != ?
                                  AND status IN ('confirmed', 'pending',
                                      'pending_payment', 'pending_confirmation')
                                  AND check_out_date >= CURRENT_DATE
                            )
                            """, Boolean.class, newRoomId, bookingId));
                    String nextRoomStatus = hasUpcoming ? "reserved_dirty" : "dirty";
                    jdbc.update("UPDATE rooms SET status = ? WHERE id = ?",
                            nextRoomStatus, newRoomId);
                    try {
                        ensureCheckoutCleaningTask(newRoomId, userId);
                    } catch (Exception e) {
                        log.warn("Failed to create housekeeping task for checked-out"
                                        + " booking {} in room {}: {}",
                                bookingId, newRoomId, e.getMessage());
                    }
                    try {
                        String invoiceNumber =
                                invoiceNumbers.ensureInvoiceForBooking(bookingId, userId);
                        bookingEmails.tryQueueCheckoutReceiptEmail(bookingId, invoiceNumber);
                    } catch (Exception e) {
                        log.warn("Failed to issue checkout invoice for booking {}: {}",
                                bookingId, e.getMessage());
                    }
                    loyaltyAwards.tryAwardEligibleBookingPoints(bookingId, null, userId);
                }
                case "checked_in", "auto_checked_in" -> jdbc.update(
                        "UPDATE rooms SET status = 'occupied' WHERE id = ?", newRoomId);
                case "confirmed" -> bookingEmails
                        .tryQueueBookingConfirmationEmail(bookingId);
                default -> {
                }
            }

            if (List.of("checked_in", "auto_checked_in", "checked_out",
                    "late_checkout", "completed").contains(updatedStatus)) {
                try {
                    backfillBookingPostedNights(bookingId, userId);
                } catch (Exception e) {
                    log.warn("Failed to backfill posted nights for booking {}: {}",
                            bookingId, e.getMessage());
                }
            }
        }
    }

    // ------------------------- ported helpers -----------------------------

    /** {@code reconcile_room_status_after_booking_release}. */
    private void reconcileRoomStatusAfterBookingRelease(long roomId,
            long releasedBookingId) {
        String status = jdbc.queryForObject("""
                SELECT CASE
                    WHEN EXISTS (
                        SELECT 1 FROM bookings
                        WHERE room_id = ? AND id != ?
                          AND status IN ('checked_in', 'auto_checked_in',
                              'late_checkout')
                          AND check_in_date <= CURRENT_DATE
                          AND check_out_date >= CURRENT_DATE
                    ) THEN 'occupied'
                    WHEN EXISTS (
                        SELECT 1 FROM bookings
                        WHERE room_id = ? AND id != ?
                          AND status IN ('reserved', 'confirmed', 'pending',
                              'pending_payment', 'pending_confirmation')
                          AND check_out_date > CURRENT_DATE
                    ) THEN 'reserved'
                    ELSE 'available'
                END
                """, String.class, roomId, releasedBookingId, roomId,
                releasedBookingId);

        List<String> currentRows = jdbc.queryForList(
                "SELECT status FROM rooms WHERE id = ?", String.class, roomId);
        String currentStatus = currentRows.isEmpty() ? null : currentRows.get(0);
        if ("reserved_dirty".equals(currentStatus)) {
            status = switch (status) {
                case "reserved" -> "reserved_dirty";
                case "available" -> "dirty";
                default -> status;
            };
        }
        String statusNotes = switch (status) {
            case "occupied" -> "Room status reconciled: current stay remains";
            case "reserved" -> "Room status reconciled: upcoming reservation remains";
            case "reserved_dirty" -> "Room status reconciled: upcoming reservation"
                    + " remains, cleaning needed";
            case "dirty" -> "Room released; cleaning still needed";
            default -> "Room released after booking update";
        };
        jdbc.update("""
                UPDATE rooms SET status = ?, status_notes = ?
                WHERE id = ?
                  AND (status = 'reserved_dirty' OR status NOT IN
                      ('maintenance', 'out_of_order', 'dirty', 'cleaning'))
                """, status, statusNotes, roomId);
    }

    /** {@code ensure_checkout_cleaning_task}. */
    private void ensureCheckoutCleaningTask(long roomId, long createdBy) {
        Boolean open = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM housekeeping_tasks
                    WHERE room_id = ? AND task_type = 'checkout_clean'
                      AND status IN ('pending', 'in_progress'))
                """, Boolean.class, roomId);
        if (Boolean.TRUE.equals(open)) {
            return;
        }
        jdbc.update("""
                INSERT INTO housekeeping_tasks
                    (room_id, task_type, priority, status, created_by, notes)
                VALUES (?, 'checkout_clean', 'normal', 'pending', ?,
                    'Auto-created on checkout')
                """, roomId, createdBy);
    }

    /** {@code backfill_booking_posted_nights}. */
    private int backfillBookingPostedNights(long bookingId, long postedBy) {
        Map<String, Object> booking = jdbc.queryForMap("""
                SELECT b.check_in_date, b.check_out_date, b.room_rate,
                       COALESCE(b.daily_rates, '{}'::jsonb) AS daily_rates,
                       COALESCE(b.is_tourist, false) AS is_tourist,
                       COALESCE(b.tourism_tax_amount, 0) AS tourism_tax_amount,
                       COALESCE(b.extra_bed_charge, 0) AS extra_bed_charge,
                       b.status
                FROM bookings b WHERE b.id = ?
                """, bookingId);

        String status = String.valueOf(booking.get("status"));
        if (List.of("pending", "confirmed", "void", "no_show", "voided")
                .contains(status)) {
            return 0;
        }

        LocalDate checkIn = toLocalDate(booking.get("check_in_date"));
        LocalDate checkOut = toLocalDate(booking.get("check_out_date"));
        BigDecimal roomRate = dec(booking.get("room_rate"));
        Object dailyRates = booking.get("daily_rates");
        boolean isTourist = Boolean.TRUE.equals(booking.get("is_tourist"));
        BigDecimal tourismTaxAmount = dec(booking.get("tourism_tax_amount"));
        BigDecimal extraBedFull = dec(booking.get("extra_bed_charge"));

        BigDecimal taxRatePct = settings.getPositiveDecimal("service_tax_rate",
                new BigDecimal("8"));
        BigDecimal divisor = BigDecimal.ONE
                .add(taxRatePct.divide(new BigDecimal("100")));

        boolean hourly = checkIn.equals(checkOut);
        long nightsTotal = Math.max(checkOut.toEpochDay() - checkIn.toEpochDay(), 1);
        BigDecimal tourismPerNight = isTourist
                && tourismTaxAmount.compareTo(BigDecimal.ZERO) > 0
                ? (hourly ? tourismTaxAmount
                        : tourismTaxAmount.divide(BigDecimal.valueOf(nightsTotal),
                                2, RoundingMode.HALF_UP))
                : BigDecimal.ZERO;
        BigDecimal ebChargePerNight = BigDecimal.ZERO;
        BigDecimal ebTaxPerNight = BigDecimal.ZERO;
        if (extraBedFull.compareTo(BigDecimal.ZERO) > 0) {
            ebChargePerNight = extraBedFull.divide(divisor, 2, RoundingMode.HALF_UP);
            ebTaxPerNight = extraBedFull.subtract(ebChargePerNight);
        }

        LocalDate iterEnd = hourly ? checkIn.plusDays(1) : checkOut;
        int inserted = 0;
        for (LocalDate date = checkIn; date.isBefore(iterEnd);
                date = date.plusDays(1)) {
            List<Long> runRows = jdbc.queryForList(
                    "SELECT id FROM night_audit_runs WHERE audit_date = ?"
                            + " AND status = 'completed' LIMIT 1",
                    Long.class, date);
            Long runId = runRows.isEmpty() ? null : runRows.get(0);
            if (runId == null) {
                continue;
            }
            BigDecimal nightRate = roomRate;
            Map<String, Object> dailyRatesMap = parseDailyRates(dailyRates);
            if (dailyRatesMap != null) {
                Object value = dailyRatesMap.get(date.toString());
                if (value instanceof Number n) {
                    nightRate = new BigDecimal(n.toString());
                } else if (value instanceof String s && !s.isBlank()) {
                    nightRate = new BigDecimal(s.trim());
                }
            }
            BigDecimal roomCharge = nightRate.divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal serviceTax = nightRate.subtract(roomCharge);
            BigDecimal nightTotal = nightRate.add(extraBedFull).add(tourismPerNight);

            int rows = jdbc.update("""
                    INSERT INTO night_audit_posted_nights
                        (booking_id, audit_date, room_rate, room_charge, service_tax,
                         tourism_tax, extra_bed_charge, extra_bed_tax, total_posted,
                         audit_run_id, posted_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (booking_id, audit_date) DO NOTHING
                    """, bookingId, date, nightRate, roomCharge, serviceTax,
                    tourismPerNight, ebChargePerNight, ebTaxPerNight, nightTotal,
                    runId, postedBy);
            if (rows > 0) {
                jdbc.update("""
                        UPDATE night_audit_runs
                        SET total_bookings_posted = COALESCE(total_bookings_posted, 0) + 1,
                            total_revenue = COALESCE(total_revenue, 0) + ?
                        WHERE id = ?
                        """, nightTotal, runId);
                inserted++;
            }
        }
        return inserted;
    }

    /** {@code ensure_checkout_balance_resolved}. */
    private void ensureCheckoutBalanceResolved(long bookingId,
            Map<String, Object> existing, BookingUpdate input, String newStatus,
            BigDecimal newTotalAmount) {
        boolean checkoutTransition = List.of("checked_out", "completed")
                .contains(newStatus)
                && !List.of("checked_out", "completed")
                        .contains(String.valueOf(existing.get("status")));
        if (!checkoutTransition) {
            return;
        }
        BigDecimal totalAmount = newTotalAmount != null ? newTotalAmount
                : dec(existing.get("total_amount"));
        BigDecimal totalPaid = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount) FILTER (
                    WHERE status = 'completed'
                      AND COALESCE(payment_type, 'booking') != 'refund'), 0)
                FROM payments WHERE booking_id = ?
                """, BigDecimal.class, bookingId);
        BigDecimal balanceDue = checkoutBalanceDue(totalAmount, totalPaid);

        Long finalCompanyId = input.companyId() != null ? input.companyId()
                : existing.get("company_id") == null ? null
                        : ((Number) existing.get("company_id")).longValue();
        String companyName = input.companyName() != null ? input.companyName()
                : existing.get("company_name") == null ? null
                        : String.valueOf(existing.get("company_name"));
        boolean companyBilling = finalCompanyId != null
                || (companyName != null && !companyName.trim().isEmpty());
        if (balanceDue.compareTo(BigDecimal.ZERO) > 0 && !companyBilling) {
            throw ApiError.badRequest(
                    "Collect full payment before checkout. Balance due: "
                            + balanceDue.setScale(2));
        }
    }

    /** {@code checkout_balance_due}. */
    static BigDecimal checkoutBalanceDue(BigDecimal totalAmount,
            BigDecimal totalPaid) {
        return totalAmount.compareTo(totalPaid) > 0
                ? totalAmount.subtract(totalPaid) : BigDecimal.ZERO;
    }

    /** {@code sanitize_ota_reference}. */
    static String sanitizeOtaReference(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = Sanitizer.sanitizeText(value).trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // upstream `chars().take(100)` counts Unicode scalars, not UTF-16 units
        return trimmed.codePoints().limit(100)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint,
                        StringBuilder::append)
                .toString();
    }

    // ------------------------- small utilities ----------------------------

    private Map<String, Object> fetchBooking(long bookingId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM bookings WHERE id = ?", bookingId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Booking not found");
        }
        return rows.get(0);
    }

    static LocalDate parseDateFlexible(String value, String field) {
        String datePart = value.contains("T") ? value.substring(0, value.indexOf('T'))
                : value;
        try {
            return LocalDate.parse(datePart.trim());
        } catch (DateTimeParseException e) {
            throw ApiError.badRequest(
                    "Invalid " + field + " date. Use YYYY-MM-DD");
        }
    }

    static LocalDateTime parseDateTimeFlexible(String value) {
        String v = value.trim();
        if (v.isEmpty()) {
            throw ApiError.badRequest("Invalid actual checkout date. Use"
                    + " YYYY-MM-DD or an ISO date-time");
        }
        try {
            return OffsetDateTime.parse(v).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        if (v.contains("T") || v.contains(" ")) {
            String normalized = v.replace(' ', 'T');
            for (DateTimeFormatter fmt : new DateTimeFormatter[] {
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")}) {
                try {
                    return LocalDateTime.parse(normalized, fmt);
                } catch (DateTimeParseException ignored) {
                }
            }
            try {
                return LocalDate.parse(normalized.substring(0, normalized.indexOf('T')))
                        .atTime(12, 0);
            } catch (DateTimeParseException e) {
                throw ApiError.badRequest("Invalid actual checkout date. Use"
                        + " YYYY-MM-DD or an ISO date-time");
            }
        }
        try {
            return LocalDate.parse(v).atTime(12, 0);
        } catch (DateTimeParseException e) {
            throw ApiError.badRequest("Invalid actual checkout date. Use"
                    + " YYYY-MM-DD or an ISO date-time");
        }
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    private Map<String, Object> parseDailyRates(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof String s && !s.isBlank()) {
                return objectMapper.readValue(s,
                        new tools.jackson.core.type.TypeReference<>() {
                        });
            }
            if (value instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                return cast;
            }
            return objectMapper.readValue(String.valueOf(value),
                    new tools.jackson.core.type.TypeReference<>() {
                    });
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal dec(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal d) {
            return d;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static BigDecimal decOrNull(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }
}
