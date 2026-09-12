package com.hotelapp.bookings;

import static com.hotelapp.rates.RatesController.message;
import static com.hotelapp.rates.RatesController.num;
import static com.hotelapp.rates.RatesController.numD;
import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.AfterCommit;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.web.Page;
import com.hotelapp.email.BookingEmails;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingsController {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final BookingRelease bookingRelease;
    private final BookingEmails bookingEmails;
    private final BookingLifecycle lifecycle;

    public BookingsController(JdbcTemplate jdbc, AuditWriter audit,
            BookingRelease bookingRelease, BookingEmails bookingEmails,
            BookingLifecycle lifecycle) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.bookingRelease = bookingRelease;
        this.bookingEmails = bookingEmails;
        this.lifecycle = lifecycle;
    }

    /** Run {@code task} after the surrounding transaction commits. */
    private static void afterCommit(Runnable task) {
        AfterCommit.run(task);
    }

    @GetMapping("/api/bookings")
    public Map<String, Object> list(@RequestParam Map<String, String> q) {
        long userId = CurrentUser.require().userId();
        gate(userId);
        long page = Page.page(q);
        long size = Page.pageSize(q);
        var clauses = new java.util.ArrayList<String>();
        var args = new java.util.ArrayList<Object>();
        if (notBlank(q.get("status"))) {
            clauses.add("b.status = ?");
            args.add(q.get("status"));
        }
        if (notBlank(q.get("search"))) {
            clauses.add("(b.booking_number ILIKE ? OR b.guest_name ILIKE ?)");
            args.add(like(q.get("search")));
            args.add(like(q.get("search")));
        }
        if (notBlank(q.get("check_in_date"))) {
            clauses.add("b.check_in_date >= CAST(? AS date)");
            args.add(q.get("check_in_date"));
        }
        String where = clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings b " + where, Long.class, args.toArray());
        List<Map<String, Object>> data = jdbc.queryForList("""
                SELECT b.*, r.room_number FROM bookings b
                LEFT JOIN rooms r ON r.id = b.room_id
                """ + where + " ORDER BY b.created_at DESC LIMIT ? OFFSET ?",
                append(args, size, Page.offset(page, size)));
        return Page.of(data, total == null ? 0 : total, page, size);
    }

    @PostMapping("/api/bookings")
    @Transactional
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gatePermission(userId, "bookings:create");
        Number guestId = num(body, "guest_id");
        Number roomId = num(body, "room_id");
        String checkIn = str(body, "check_in_date");
        String checkOut = str(body, "check_out_date");
        if (guestId == null || roomId == null || checkIn == null || checkOut == null) {
            throw ApiError.badRequest(
                    "Guest ID, room ID, check-in date and check-out date are required");
        }
        LocalDate in = LocalDate.parse(checkIn);
        LocalDate out = LocalDate.parse(checkOut);
        if (!out.isAfter(in)) {
            throw ApiError.badRequest("Check-out date must be after check-in date");
        }
        // Only rooms under maintenance or out of order are blocked outright.
        String roomStatus = jdbc.queryForObject(
                "SELECT COALESCE(status, 'available') FROM rooms WHERE id = ?",
                String.class, roomId.longValue());
        if ("maintenance".equals(roomStatus) || "out_of_order".equals(roomStatus)) {
            throw ApiError.badRequest(
                    "Room is not available - currently " + roomStatus.replace("_", " "));
        }
        long overlapping = jdbc.queryForObject("""
                SELECT COUNT(*) FROM bookings
                WHERE room_id = ?
                  AND status IN ('reserved','confirmed','checked_in','auto_checked_in',
                                 'pending','pending_payment','pending_confirmation')
                  AND status != 'voided'
                  AND check_in_date < CAST(? AS date) AND check_out_date > CAST(? AS date)
                """, Long.class, roomId.longValue(), out, in);
        if (overlapping > 0) {
            throw ApiError.conflict("Room is not available for the selected dates");
        }
        BigDecimal roomRate = decOrNull(body.get("room_rate")) == null
                ? jdbc.queryForObject(
                        "SELECT COALESCE(base_price, 0) FROM rooms WHERE id = ?",
                        BigDecimal.class, roomId.longValue())
                : decOrNull(body.get("room_rate"));
        long nights = java.time.temporal.ChronoUnit.DAYS.between(in, out);
        BigDecimal subtotal = roomRate.multiply(BigDecimal.valueOf(nights));
        BigDecimal taxRate = settingDecimal("tax_rate", new BigDecimal("0.10"));
        BigDecimal taxAmount = subtotal.multiply(taxRate);
        String bookingNumber = "BK-" + LocalDate.now() + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Long id = jdbc.queryForObject("""
                INSERT INTO bookings (booking_number, guest_id, room_id, check_in_date,
                    check_out_date, adults, children, status, source, room_rate, nights,
                    subtotal, tax_amount, total_amount, payment_status, remarks,
                    booking_channel_id, market_code, created_by)
                VALUES (?, ?, ?, CAST(? AS date), CAST(? AS date), COALESCE(?,1), COALESCE(?,0),
                        'confirmed', COALESCE(?,'direct'), ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?)
                RETURNING id
                """, Long.class, bookingNumber, guestId.longValue(), roomId.longValue(),
                checkIn, checkOut, num(body, "adults"), num(body, "children"),
                str(body, "source"), roomRate, nights, subtotal, taxAmount,
                subtotal.add(taxAmount),
                str(body, "payment_status") == null ? "unpaid" : str(body, "payment_status"),
                str(body, "remarks"),
                num(body, "booking_channel_id"), str(body, "market_code"), userId);
        // A confirmed booking reserves the room. A dirty room stays flagged
        // for housekeeping — reserved_dirty blocks check-in until cleared.
        boolean needsCleaning = List.of("dirty", "cleaning", "reserved_dirty")
                .contains(roomStatus);
        LocalDate today = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
        String reservedStatus = needsCleaning ? "reserved_dirty" : "reserved";
        jdbc.update("UPDATE rooms SET status = ?, status_notes = ? WHERE id = ?",
                reservedStatus,
                "Booking #" + bookingNumber + " - " + (needsCleaning
                        ? "Reservation created, room needs cleaning before check-in"
                        : in.equals(today) ? "Reservation arriving today"
                        : "Future reservation"),
                roomId.longValue());
        history(id, userId, "created", "Booking created as " + bookingNumber);
        audit.event(userId, "booking_created", "booking", id,
                Map.of("booking_number", bookingNumber));
        // Staff-created bookings are inserted `confirmed`, so this is the
        // booking-confirmed trigger for the front-desk path. Best-effort and
        // post-commit: a mail failure must not fail the booking creation.
        afterCommit(() -> bookingEmails.tryQueueBookingConfirmationEmail(id));
        return one(id);
    }

    @GetMapping("/api/bookings/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        gate(CurrentUser.require().userId());
        return one(id);
    }

    @PatchMapping("/api/bookings/{id}")
    public Map<String, Object> update(@PathVariable long id,
            @RequestBody BookingUpdate input) {
        long userId = CurrentUser.require().userId();
        return lifecycle.updateBooking(userId, id, input);
    }

    @PutMapping("/api/bookings/{id}")
    public Map<String, Object> updateViaPut(@PathVariable long id,
            @RequestBody BookingUpdate input) {
        return update(id, input);
    }

    @DeleteMapping("/api/bookings/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gatePermission(userId, "bookings:delete");
        one(id);
        jdbc.update("DELETE FROM bookings WHERE id = ?", id);
        audit.event(userId, "booking_deleted", "booking", id, null);
        return message("Booking deleted successfully");
    }

    @PostMapping("/api/bookings/{id}/checkin")
    @Transactional
    public Map<String, Object> checkin(@PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gatePermission(userId, "bookings:update");
        Map<String, Object> booking = one(id);
        String status = str(booking, "status");
        if ("checked_in".equals(status)) {
            throw ApiError.conflict("Booking is already checked in");
        }
        if (!List.of("reserved", "confirmed").contains(status)) {
            throw ApiError.conflict("Only reserved or confirmed bookings can be checked in");
        }
        jdbc.update("""
                UPDATE bookings SET status = 'checked_in', actual_check_in = NOW() WHERE id = ?
                """, id);
        Number roomId = (Number) booking.get("room_id");
        if (roomId != null) {
            jdbc.update("UPDATE rooms SET status = 'occupied' WHERE id = ?", roomId.longValue());
        }
        history(id, userId, "checked_in", "Guest checked in");
        audit.event(userId, "guest_checked_in", "booking", id, null);
        return one(id);
    }

    @PostMapping("/api/bookings/void")
    public Map<String, Object> voidBooking(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gatePermission(userId, "bookings:update");
        Number bookingId = num(body, "booking_id");
        if (bookingId == null) {
            throw ApiError.badRequest("Booking ID is required");
        }
        Map<String, Object> booking = one(bookingId.longValue());
        if ("void".equals(str(booking, "status"))) {
            throw ApiError.conflict("Booking is already voided");
        }
        jdbc.update("""
                UPDATE bookings SET status = 'void', cancellation_reason = ?, cancelled_at = NOW(),
                    cancelled_by = ? WHERE id = ?
                """, str(body, "reason"), userId, bookingId.longValue());
        Number roomId = (Number) booking.get("room_id");
        if ("checked_in".equals(str(booking, "status")) && roomId != null) {
            jdbc.update("UPDATE rooms SET status = 'available' WHERE id = ?",
                    roomId.longValue());
        }
        history(bookingId.longValue(), userId, "voided", str(body, "reason"));
        audit.event(userId, "booking_voided", "booking", bookingId.longValue(), null);
        return message("Booking voided successfully");
    }

    /**
     * Release the room held by an unpaid booking. Same permission as voiding —
     * this is a narrower, reason-required form of the same override, not a
     * wider one. ({@code release_booking} upstream.)
     */
    @PostMapping("/api/bookings/{id}/release")
    public Map<String, Object> release(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gatePermission(userId, "bookings:update");
        return bookingRelease.releasePendingPaymentBooking(
                userId, id, str(body, "reason"));
    }

    @PostMapping("/api/bookings/{id}/reactivate")
    public Map<String, Object> reactivate(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gatePermission(userId, "bookings:update");
        Map<String, Object> booking = one(id);
        if (!"void".equals(str(booking, "status"))) {
            throw ApiError.conflict("Only voided bookings can be reactivated");
        }
        Number roomId = (Number) booking.get("room_id");
        long conflicts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM bookings
                WHERE room_id = ? AND id <> ? AND status NOT IN ('cancelled','void')
                  AND check_in_date < check_out_date AND check_out_date > check_in_date
                  AND ((check_in_date <= CURRENT_DATE AND check_out_date > CURRENT_DATE))
                """, Long.class, roomId, id);
        if (conflicts > 0) {
            throw ApiError.conflict("Room is no longer available for the original dates");
        }
        jdbc.update("""
                UPDATE bookings SET status = 'reserved', cancellation_reason = NULL,
                    cancelled_at = NULL, cancelled_by = NULL WHERE id = ?
                """, id);
        history(id, userId, "reactivated", "Voided booking reactivated");
        audit.event(userId, "booking_reactivated", "booking", id, null);
        return message("Booking reactivated successfully");
    }

    @GetMapping("/api/bookings/{id}/timeline")
    public List<Map<String, Object>> timeline(@PathVariable long id) {
        one(id);
        return jdbc.queryForList("""
                SELECT bh.*, u.username AS performed_by_username FROM booking_history bh
                LEFT JOIN users u ON u.id = bh.performed_by
                WHERE bh.booking_id = ? ORDER BY bh.created_at
                """, id);
    }

    @GetMapping("/api/bookings/stats")
    public Map<String, Object> stats() {
        gate(CurrentUser.require().userId());
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS total_bookings,
                       COUNT(*) FILTER (WHERE status = 'reserved') AS reserved,
                       COUNT(*) FILTER (WHERE status = 'checked_in') AS checked_in,
                       COUNT(*) FILTER (WHERE status = 'checked_out') AS checked_out,
                       COUNT(*) FILTER (WHERE status = 'void') AS voided,
                       COUNT(*) FILTER (WHERE is_complimentary) AS complimentary,
                       COALESCE(SUM(total_amount) FILTER (
                           WHERE status IN ('reserved','checked_in','checked_out')), 0)
                           AS total_revenue
                FROM bookings
                """);
    }

    @GetMapping("/api/bookings/complimentary")
    public List<Map<String, Object>> complimentaryList() {
        gate(CurrentUser.require().userId());
        return jdbc.queryForList(
                "SELECT * FROM bookings WHERE is_complimentary ORDER BY created_at DESC");
    }

    @PostMapping("/api/bookings/{id}/complimentary")
    public Map<String, Object> markComplimentary(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gatePermission(userId, "bookings:update");
        one(id);
        jdbc.update("""
                UPDATE bookings SET is_complimentary = true, complimentary_reason = ?,
                    complimentary_start_date = CAST(? AS date),
                    complimentary_end_date = CAST(? AS date),
                    complimentary_nights = COALESCE(?, nights) WHERE id = ?
                """, str(body, "reason"), str(body, "start_date"),
                str(body, "end_date"), num(body, "nights"), id);
        history(id, userId, "complimentary", str(body, "reason"));
        audit.event(userId, "complimentary_marked", "booking", id, null);
        return one(id);
    }

    @PatchMapping("/api/bookings/{id}/complimentary")
    public Map<String, Object> updateComplimentary(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        return markComplimentary(id, body);
    }

    @DeleteMapping("/api/bookings/{id}/complimentary")
    public Map<String, Object> removeComplimentary(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gatePermission(userId, "bookings:update");
        one(id);
        jdbc.update("""
                UPDATE bookings SET is_complimentary = false, complimentary_reason = NULL,
                    complimentary_start_date = NULL, complimentary_end_date = NULL,
                    complimentary_nights = NULL WHERE id = ?
                """, id);
        audit.event(userId, "complimentary_removed", "booking", id, null);
        return one(id);
    }

    @PostMapping("/api/bookings/{id}/convert-credits")
    public Map<String, Object> convertCredits(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gatePermission(userId, "bookings:update");
        Map<String, Object> booking = one(id);
        Number guestId = (Number) booking.get("guest_id");
        Number roomTypeId = jdbc.queryForObject(
                "SELECT room_type_id FROM rooms WHERE id = ?", Number.class,
                booking.get("room_id"));
        Integer credited = jdbc.queryForObject("""
                INSERT INTO guest_complimentary_credits (guest_id, room_type_id, credit_nights)
                VALUES (?, ?, 1)
                ON CONFLICT (guest_id, room_type_id)
                DO UPDATE SET credit_nights = guest_complimentary_credits.credit_nights + 1
                RETURNING credit_nights
                """, Integer.class, guestId, roomTypeId);
        audit.event(userId, "complimentary_converted_to_credit", "booking", id,
                Map.of("credit_nights", credited));
        return message("Converted to complimentary credit successfully");
    }

    @PostMapping("/api/bookings/book-with-credits")
    public Map<String, Object> bookWithCredits(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gatePermission(userId, "bookings:create");
        Number guestId = num(body, "guest_id");
        Number roomTypeId = num(body, "room_type_id");
        if (guestId == null || roomTypeId == null) {
            throw ApiError.badRequest("Guest ID and room type ID are required");
        }
        Integer updated = jdbc.queryForObject("""
                UPDATE guest_complimentary_credits
                SET credit_nights = credit_nights - 1
                WHERE guest_id = ? AND room_type_id = ? AND credit_nights > 0
                RETURNING credit_nights
                """, Integer.class, guestId.longValue(), roomTypeId.longValue());
        if (updated == null) {
            throw ApiError.badRequest("No complimentary credits available for this room type");
        }
        Map<String, Object> booking = create(withoutCreditsFields(body));
        audit.event(userId, "booking_created_with_credits", "booking",
                ((Number) booking.get("id")).longValue(), null);
        return booking;
    }

    private Map<String, Object> withoutCreditsFields(Map<String, Object> body) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.putIfAbsent("room_rate", BigDecimal.ZERO);
        copy.put("room_rate", BigDecimal.ZERO);
        copy.put("total_override", true);
        return copy;
    }

    @GetMapping("/api/bookings/checkin-advisory")
    public Map<String, Object> advisory() {
        gate(CurrentUser.require().userId());
        return jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE b.check_in_date = CURRENT_DATE
                            AND b.status IN ('reserved','confirmed')) AS arriving_today,
                       COUNT(*) FILTER (WHERE b.check_out_date < CURRENT_DATE
                            AND b.status = 'checked_in') AS overdue_checkout,
                       COUNT(*) FILTER (WHERE b.check_in_date = CURRENT_DATE
                            AND b.status IN ('reserved','confirmed')
                            AND (b.pre_checkin_completed IS NOT TRUE)) AS missing_pre_checkin
                FROM bookings b
                """);
    }

    @GetMapping("/api/bookings/{id}/checkin-advisory")
    public Map<String, Object> bookingAdvisory(@PathVariable long id) {
        Map<String, Object> booking = one(id);
        Map<String, Object> advisory = new LinkedHashMap<>();
        advisory.put("can_check_in", List.of("reserved", "confirmed")
                .contains(str(booking, "status")));
        advisory.put("pre_checkin_completed", booking.get("pre_checkin_completed"));
        advisory.put("status", booking.get("status"));
        advisory.put("warnings", List.of());
        return advisory;
    }

    @GetMapping("/api/bookings/{id}/auto-checkin-eligibility")
    public Map<String, Object> autoCheckinEligibility(@PathVariable long id) {
        Map<String, Object> booking = one(id);
        boolean eligible = "reserved".equals(str(booking, "status"))
                && Boolean.TRUE.equals(booking.get("pre_checkin_completed"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eligible", eligible);
        body.put("reasons", eligible ? List.of() : List.of(
                "Booking must be reserved with completed pre-check-in"));
        return body;
    }

    @PostMapping("/api/bookings/{id}/auto-checkin")
    public Map<String, Object> autoCheckin(@PathVariable long id) {
        return checkin(id, null);
    }

    @GetMapping("/api/guests/credits")
    public Map<String, Object> guestsWithCredits() {
        gatePermission(CurrentUser.require().userId(), "guests:read");
        List<Map<String, Object>> credits = jdbc.queryForList("""
                SELECT gc.guest_id, g.nick_name AS guest_name, g.email,
                       gc.room_type_id, rt.name AS room_type_name, rt.code AS room_type_code,
                       gc.nights_available, gc.notes
                FROM guest_complimentary_credits gc
                INNER JOIN guests g ON gc.guest_id = g.id
                INNER JOIN room_types rt ON gc.room_type_id = rt.id
                WHERE gc.nights_available > 0
                ORDER BY g.nick_name, rt.name
                """);
        return Map.of("credits", credits);
    }

    @PostMapping("/api/guests/credits")
    public Map<String, Object> addCredits(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gatePermission(userId, "guests:manage");
        Number guestId = num(body, "guest_id");
        Number roomTypeId = num(body, "room_type_id");
        Number nights = num(body, "nights");
        if (guestId == null || roomTypeId == null || nights == null) {
            throw ApiError.badRequest("Guest ID, room type ID and nights are required");
        }
        String reasonInput = str(body, "reason");
        if (reasonInput == null) {
            reasonInput = str(body, "notes");
        }
        String reason = creditReason(reasonInput);
        Boolean guestExists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM guests WHERE id = ?)",
                Boolean.class, guestId.longValue());
        if (!Boolean.TRUE.equals(guestExists)) {
            throw ApiError.notFound("Guest with id " + guestId + " not found");
        }
        Boolean roomTypeExists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM room_types WHERE id = ?)",
                Boolean.class, roomTypeId.longValue());
        if (!Boolean.TRUE.equals(roomTypeExists)) {
            throw ApiError.notFound("Room type with id " + roomTypeId + " not found");
        }
        if (nights.intValue() <= 0) {
            throw ApiError.badRequest("Nights must be greater than 0");
        }
        jdbc.update("""
                INSERT INTO guest_complimentary_credits (guest_id, room_type_id,
                    nights_available, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (guest_id, room_type_id)
                DO UPDATE SET nights_available = guest_complimentary_credits.nights_available + ?,
                              notes = ?, updated_at = CURRENT_TIMESTAMP
                """, guestId.longValue(), roomTypeId.longValue(), nights.intValue(), reason,
                nights.intValue(), reason);
        Map<String, Object> credit = jdbc.queryForMap("""
                SELECT gc.guest_id, g.nick_name AS guest_name, gc.room_type_id,
                       rt.name AS room_type_name, gc.nights_available
                FROM guest_complimentary_credits gc
                INNER JOIN guests g ON gc.guest_id = g.id
                INNER JOIN room_types rt ON gc.room_type_id = rt.id
                WHERE gc.guest_id = ? AND gc.room_type_id = ?
                """, guestId.longValue(), roomTypeId.longValue());
        audit.event(userId, "guest_complimentary_credits_granted", "guest",
                guestId.longValue(), Map.of(
                        "guest_id", guestId.longValue(),
                        "room_type_id", roomTypeId.longValue(),
                        "room_type_name", credit.get("room_type_name"),
                        "nights_added", nights.intValue(),
                        "nights_available", credit.get("nights_available"),
                        "reason", reason));
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Added " + nights.intValue() + " nights to guest credits");
        Map<String, Object> creditOut = new java.util.LinkedHashMap<>(credit);
        creditOut.put("reason", reason);
        creditOut.put("notes", reason);
        out.put("credit", creditOut);
        return out;
    }

    @PatchMapping("/api/guests/{guestId}/credits/{roomTypeId}")
    public Map<String, Object> updateCredits(@PathVariable long guestId,
            @PathVariable long roomTypeId, @RequestBody Map<String, Object> body) {
        gatePermission(CurrentUser.require().userId(), "guests:manage");
        Boolean creditExists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM guest_complimentary_credits"
                        + " WHERE guest_id = ? AND room_type_id = ?)",
                Boolean.class, guestId, roomTypeId);
        if (!Boolean.TRUE.equals(creditExists)) {
            throw ApiError.notFound("Credit record not found for guest " + guestId
                    + " and room type " + roomTypeId);
        }
        Number nightsAvailable = num(body, "nights_available");
        String notes = str(body, "notes");
        if (nightsAvailable != null && nightsAvailable.intValue() < 0) {
            throw ApiError.badRequest("Nights available cannot be negative");
        }
        if (nightsAvailable == null && notes == null) {
            throw ApiError.badRequest("No fields to update");
        }
        if (nightsAvailable != null && notes != null) {
            jdbc.update("UPDATE guest_complimentary_credits"
                            + " SET nights_available = ?, notes = ?,"
                            + " updated_at = CURRENT_TIMESTAMP"
                            + " WHERE guest_id = ? AND room_type_id = ?",
                    nightsAvailable.intValue(), notes, guestId, roomTypeId);
        } else if (nightsAvailable != null) {
            jdbc.update("UPDATE guest_complimentary_credits"
                            + " SET nights_available = ?, updated_at = CURRENT_TIMESTAMP"
                            + " WHERE guest_id = ? AND room_type_id = ?",
                    nightsAvailable.intValue(), guestId, roomTypeId);
        } else {
            jdbc.update("UPDATE guest_complimentary_credits"
                            + " SET notes = ?, updated_at = CURRENT_TIMESTAMP"
                            + " WHERE guest_id = ? AND room_type_id = ?",
                    notes, guestId, roomTypeId);
        }
        Map<String, Object> credit = jdbc.queryForMap("""
                SELECT gc.guest_id, g.nick_name AS guest_name, gc.room_type_id,
                       rt.name AS room_type_name, gc.nights_available, gc.notes
                FROM guest_complimentary_credits gc
                INNER JOIN guests g ON gc.guest_id = g.id
                INNER JOIN room_types rt ON gc.room_type_id = rt.id
                WHERE gc.guest_id = ? AND gc.room_type_id = ?
                """, guestId, roomTypeId);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Credits updated successfully");
        out.put("credit", credit);
        return out;
    }

    @DeleteMapping("/api/guests/{guestId}/credits/{roomTypeId}")
    public Map<String, Object> deleteCredits(@PathVariable long guestId,
            @PathVariable long roomTypeId) {
        gatePermission(CurrentUser.require().userId(), "guests:manage");
        List<Map<String, Object>> credit = jdbc.queryForList("""
                SELECT gc.nights_available, g.nick_name AS guest_name,
                       rt.name AS room_type_name
                FROM guest_complimentary_credits gc
                INNER JOIN guests g ON gc.guest_id = g.id
                INNER JOIN room_types rt ON gc.room_type_id = rt.id
                WHERE gc.guest_id = ? AND gc.room_type_id = ?
                """, guestId, roomTypeId);
        if (credit.isEmpty()) {
            throw ApiError.notFound("Credit record not found for guest " + guestId
                    + " and room type " + roomTypeId);
        }
        Object nightsDeleted = credit.get(0).get("nights_available");
        Object guestName = credit.get(0).get("guest_name");
        Object roomTypeName = credit.get(0).get("room_type_name");
        jdbc.update("DELETE FROM guest_complimentary_credits"
                        + " WHERE guest_id = ? AND room_type_id = ?",
                guestId, roomTypeId);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Deleted " + nightsDeleted + " nights of " + roomTypeName
                + " credits for " + guestName);
        Map<String, Object> deleted = new java.util.LinkedHashMap<>();
        deleted.put("guest_id", guestId);
        deleted.put("guest_name", guestName);
        deleted.put("room_type_id", roomTypeId);
        deleted.put("room_type_name", roomTypeName);
        deleted.put("nights_deleted", nightsDeleted);
        out.put("deleted", deleted);
        return out;
    }

    private static String creditReason(String reason) {
        String cleaned = reason == null
                ? "" : com.hotelapp.core.text.Sanitizer.sanitizeNotes(reason).trim();
        if (cleaned.isEmpty()) {
            throw ApiError.badRequest(
                    "A reason is required when granting complimentary credits");
        }
        if (cleaned.codePointCount(0, cleaned.length()) > 500) {
            throw ApiError.badRequest(
                    "Complimentary credit reason must be 500 characters or fewer");
        }
        return cleaned;
    }

    private void gate(long userId) {
        // read endpoints require bookings:read like the Rust router gates
        com.hotelapp.core.security.PermissionGateHelper.check(userId, "bookings:read");
    }

    private void gatePermission(long userId, String permission) {
        com.hotelapp.core.security.PermissionGateHelper.check(userId, permission);
    }

    private void history(long bookingId, long userId, String action, String description) {
        jdbc.update("""
                INSERT INTO booking_history (booking_id, changed_field, old_value, new_value,
                    changed_by, notes)
                VALUES (?, ?, NULL, ?, ?, ?)
                """, bookingId, action, action, userId, description);
    }

    private BigDecimal settingDecimal(String key, BigDecimal fallback) {
        try {
            String value = jdbc.queryForObject(
                    "SELECT value FROM system_settings WHERE key = ?", String.class, key);
            return value == null ? fallback : new BigDecimal(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private Map<String, Object> one(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM bookings WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Booking not found");
        }
        return rows.get(0);
    }

    static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    static String like(String value) {
        return "%" + value + "%";
    }

    static Object[] append(java.util.List<Object> base, Object... extra) {
        base.addAll(java.util.Arrays.asList(extra));
        return base.toArray();
    }

    static BigDecimal decOrNull(Object value) {
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (value instanceof String s && !s.isBlank()) {
            return new BigDecimal(s.trim());
        }
        return null;
    }
}
