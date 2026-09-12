package com.hotelapp.portal;

import com.hotelapp.communications.GuestComms;
import com.hotelapp.consent.Consents;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.text.Sanitizer;
import com.hotelapp.email.AccountEmails;
import com.hotelapp.portal.PortalModels.AutoCheckinResponse;
import com.hotelapp.portal.PortalModels.ConsentAcceptance;
import com.hotelapp.portal.PortalModels.GuestBookingCancellationRequest;
import com.hotelapp.portal.PortalModels.GuestPortalBenefitsResponse;
import com.hotelapp.portal.PortalModels.GuestPortalBookingResponse;
import com.hotelapp.portal.PortalModels.GuestPortalBookingSummary;
import com.hotelapp.portal.PortalModels.GuestPortalBookingView;
import com.hotelapp.portal.PortalModels.GuestPortalClaimAccountRequest;
import com.hotelapp.portal.PortalModels.GuestPortalClaimAccountResponse;
import com.hotelapp.portal.PortalModels.GuestPortalCreditsResponse;
import com.hotelapp.portal.PortalModels.GuestPortalGuestView;
import com.hotelapp.portal.PortalModels.GuestPortalLoginResponse;
import com.hotelapp.portal.PortalModels.GuestPortalMeResponse;
import com.hotelapp.portal.PortalModels.GuestPortalMembership;
import com.hotelapp.portal.PortalModels.GuestPortalMembershipResponse;
import com.hotelapp.portal.PortalModels.GuestPortalPage;
import com.hotelapp.portal.PortalModels.GuestPortalPointsActivity;
import com.hotelapp.portal.PortalModels.GuestPortalProfileUpdate;
import com.hotelapp.portal.PortalModels.GuestPortalReward;
import com.hotelapp.portal.PortalModels.GuestPortalRoomTypeCredit;
import com.hotelapp.portal.PortalModels.GuestPortalTierBenefit;
import com.hotelapp.portal.PortalModels.GuestPortalTransaction;
import com.hotelapp.portal.PortalModels.GuestPortalVerifyRequest;
import com.hotelapp.portal.PortalModels.GuestPortalVerifyResponse;
import com.hotelapp.portal.PortalModels.GuestUpdateInput;
import com.hotelapp.portal.PortalModels.PaymentActionResponse;
import com.hotelapp.portal.PortalModels.PaypalCaptureRequest;
import com.hotelapp.portal.PortalModels.PaypalCreateOrderResponse;
import com.hotelapp.portal.PortalModels.PreCheckInUpdateRequest;
import com.hotelapp.portal.PortalModels.SessionPaypalCaptureRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Port of {@code services/guest_portal.rs} — verification, token-authenticated
 * booking reads/writes, session login/logout, the {@code me} hub, claim-account
 * and the guest payment entry points.
 */
@Component
public class PortalService {

    private static final Logger log = LoggerFactory.getLogger(PortalService.class);

    private static final String VERIFY_BOOKING_FAILURE =
            "Unable to verify booking details. Please check the booking number and name.";
    private static final long SESSION_TTL_HOURS = 24;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");
    private static final List<String> WEAK_PASSWORDS = List.of(
            "password", "password123", "12345678", "qwerty123", "abc123456",
            "password1", "welcome123", "admin123", "letmein123", "monkey123");
    private static final Set<String> CANCELLABLE_STATUSES =
            Set.of("pending", "pending_payment", "pending_confirmation", "confirmed");

    private final JdbcTemplate jdbc;
    private final PortalBookingOps bookings;
    private final PortalPayments payments;
    private final PortalPaymentTx paymentTx;
    private final PortalAccountTx accountTx;
    private final AutoCheckin autoCheckin;
    private final Consents consents;
    private final GuestComms comms;
    private final AccountEmails accountEmails;
    private final AuditWriter audit;
    private final com.hotelapp.core.web.ClientIp clientIp;

    public PortalService(JdbcTemplate jdbc, PortalBookingOps bookings, PortalPayments payments,
            PortalPaymentTx paymentTx, PortalAccountTx accountTx, AutoCheckin autoCheckin,
            Consents consents, GuestComms comms, AccountEmails accountEmails, AuditWriter audit,
            com.hotelapp.core.web.ClientIp clientIp) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.payments = payments;
        this.paymentTx = paymentTx;
        this.accountTx = accountTx;
        this.autoCheckin = autoCheckin;
        this.consents = consents;
        this.comms = comms;
        this.accountEmails = accountEmails;
        this.audit = audit;
        this.clientIp = clientIp;
    }

    // ------------------------------------------------------------------
    // Verify + token-authenticated booking surface
    // ------------------------------------------------------------------

    /** {@code verify_guest_booking}. */
    public GuestPortalVerifyResponse verifyGuestBooking(GuestPortalVerifyRequest request) {
        Map<String, Object> booking = findEligibleBookingByNumber(request.bookingNumber());
        if (booking == null) {
            throw verifyBookingFailure();
        }
        Map<String, Object> guest = findGuest(((Number) booking.get("guest_id")).longValue());
        if (!guestNameMatches(String.valueOf(guest.get("nick_name")), request.name())) {
            throw verifyBookingFailure();
        }

        LocalDateRange dates = bookingDates(booking);
        long daysUntilCheckin =
                java.time.temporal.ChronoUnit.DAYS.between(bookings.hotelToday(), dates.checkIn());
        if (daysUntilCheckin < 0) {
            throw ApiError.badRequest("Check-in date has passed. Please check in at reception.");
        }
        if (daysUntilCheckin > 7) {
            throw ApiError.badRequest("Pre-check-in is only available 7 days before arrival.");
        }

        String token = PortalAuth.generateSessionToken();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(48);
        jdbc.update("UPDATE bookings SET pre_checkin_token = ?, pre_checkin_token_expires_at = ? "
                        + "WHERE id = ?",
                PortalAuth.persistBookingAccessToken(token), expiresAt, booking.get("id"));
        audit.event(null, "guest_portal.precheckin_token_issued", "booking",
                ((Number) booking.get("id")).longValue(),
                Map.of("guest_id", booking.get("guest_id")));

        return new GuestPortalVerifyResponse(token,
                expiresAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                String.valueOf(booking.get("id")));
    }

    private static ApiError verifyBookingFailure() {
        return ApiError.unauthorized(VERIFY_BOOKING_FAILURE);
    }

    private static String normalizePersonName(String value) {
        return String.join(" ", value.trim().split("\\s+")).toLowerCase();
    }

    private static boolean guestNameMatches(String storedNickName, String requestedName) {
        String requested = normalizePersonName(requestedName);
        if (requested.isEmpty()) {
            return false;
        }
        return normalizePersonName(storedNickName).equals(requested);
    }

    /** {@code require_valid_token} — lookup + hash/plaintext match + expiry. */
    public Map<String, Object> requireValidToken(String token) {
        Map<String, Object> booking = findBookingByToken(token);
        if (booking == null) {
            throw ApiError.notFound("Invalid or expired token");
        }
        Object stored = booking.get("pre_checkin_token");
        String storedToken = stored == null ? "" : stored.toString();
        if (!PortalAuth.bookingAccessTokenMatches(token, storedToken)) {
            throw ApiError.notFound("Invalid or expired token");
        }
        Object expiresAt = booking.get("pre_checkin_token_expires_at");
        if (expiresAt == null) {
            throw ApiError.unauthorized("Invalid token");
        }
        OffsetDateTime expiry = toOffsetDateTime(expiresAt);
        if (expiry.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw ApiError.unauthorized("Token has expired");
        }
        return booking;
    }

    /** {@code get_booking_by_token}. */
    public GuestPortalBookingResponse getBookingByToken(String token) {
        return portalResponse(requireValidToken(token));
    }

    /** {@code submit_precheckin_update}. */
    @Transactional
    public GuestPortalBookingResponse submitPrecheckinUpdate(String token,
            PreCheckInUpdateRequest request) {
        Map<String, Object> booking = requireValidToken(token);
        if (request == null || request.guestUpdate() == null) {
            throw ApiError.badRequest("Missing required field: guest_update");
        }
        GuestUpdateInput update = request.guestUpdate();
        String email = update.email();
        if (email != null) {
            String trimmed = email.trim();
            if (trimmed.isEmpty()) {
                update = withEmail(update, "");
            } else if (EMAIL_PATTERN.matcher(trimmed).matches()) {
                update = withEmail(update, trimmed);
            } else {
                update = withEmail(update, null);
            }
        }

        long guestId = ((Number) booking.get("guest_id")).longValue();
        updateGuestPrecheckin(guestId, update);
        updateBookingPrecheckin(((Number) booking.get("id")).longValue(),
                request.marketCode(), request.specialRequests());

        audit.event(null, "guest_portal.precheckin_submitted", "booking",
                ((Number) booking.get("id")).longValue(),
                Map.of("guest_id", booking.get("guest_id")));

        return portalResponse(bookings.fetchBooking(((Number) booking.get("id")).longValue()));
    }

    private static GuestUpdateInput withEmail(GuestUpdateInput input, String email) {
        return new GuestUpdateInput(input.firstName(), input.lastName(), email, input.phone(),
                input.title(), input.altPhone(), input.icNumber(), input.nationality(),
                input.addressLine1(), input.city(), input.stateProvince(), input.postalCode(),
                input.country(), input.isActive(), input.guestType(), input.tourismType(),
                input.discountPercentage(), input.companyName());
    }

    /** {@code auto_checkin_by_token}. */
    public AutoCheckinResponse autoCheckinByToken(String token) {
        Map<String, Object> booking = requireValidToken(token);
        AutoCheckinResponse response = autoCheckin.autoCheckinForGuestPortal(
                ((Number) booking.get("id")).longValue());
        audit.event(null, "guest_portal.auto_checkin", "booking",
                ((Number) booking.get("id")).longValue(),
                Map.of("guest_id", booking.get("guest_id")));
        return response;
    }

    // ------------------------------------------------------------------
    // Claim account
    // ------------------------------------------------------------------

    /** {@code claim_booking_account}. */
    public GuestPortalClaimAccountResponse claimBookingAccount(String token,
            GuestPortalClaimAccountRequest request, HttpServletRequest httpRequest) {
        Map<String, Object> booking = requireValidToken(token);
        Map<String, Object> guest = findGuest(((Number) booking.get("guest_id")).longValue());

        boolean bookingNumberMatches = request.bookingNumber() != null
                && request.bookingNumber().trim().equalsIgnoreCase(
                        String.valueOf(booking.get("booking_number")).trim());
        if (!bookingNumberMatches
                || !guestNameMatches(String.valueOf(guest.get("nick_name")), request.guestName())) {
            throw verifyBookingFailure();
        }

        String email = request.email() == null ? null
                : Sanitizer.sanitizeEmail(request.email());
        if (email != null && email.isEmpty()) {
            email = null;
        }
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();

        // Model validation — mirrors the validator derives on the request.
        if (request.bookingNumber() == null || request.bookingNumber().isBlank()
                || request.bookingNumber().length() > 50) {
            throw ApiError.badRequest("Booking number is required");
        }
        if (request.guestName() == null || request.guestName().isBlank()
                || request.guestName().length() > 200) {
            throw ApiError.badRequest("Guest name is required");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw ApiError.badRequest(
                    "Username may only contain letters, digits, dots, underscores and dashes");
        }
        if (username.length() < 3 || username.length() > 50) {
            throw ApiError.badRequest("Username must be between 3 and 50 characters");
        }
        if (password.length() < 8 || password.length() > 100) {
            throw ApiError.badRequest("Password must be at least 8 characters long");
        }
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw ApiError.badRequest("Invalid email format");
        }
        validatePassword(password);

        List<ConsentAcceptance> acceptances =
                request.consents() == null ? List.of() : request.consents();
        Consents.validateLocales(acceptances);
        Consents.requireConsents(acceptances, Consents.REGISTRATION_REQUIRED);

        if (usernameOrEmailExists(username, email)) {
            throw ApiError.badRequest("Username or email already exists");
        }

        String passwordHash = new BCryptPasswordEncoder(12).encode(password);
        String accountEmail = email != null ? email : username + "@no-email.invalid";
        boolean emailVerificationRequired = email != null;

        long guestId = ((Number) booking.get("guest_id")).longValue();
        Consents.Context context = new Consents.Context(
                clientIp.extract(httpRequest), httpRequest.getHeader("User-Agent"));

        PortalAccountTx.ClaimOutcome outcome = accountTx.claim(guestId, username, accountEmail,
                passwordHash, String.valueOf(guest.get("nick_name")),
                guest.get("phone") == null ? null : guest.get("phone").toString(),
                !emailVerificationRequired, acceptances, context);

        comms.recordSignupMarketingConsent(guestId,
                Boolean.TRUE.equals(request.marketingOptIn()), "guest_portal_claim",
                Consents.Document.PRIVACY_NOTICE.currentVersion(), context.ipAddress(),
                context.userAgent());

        if (emailVerificationRequired) {
            accountEmails.trySendEmailVerification(outcome.userId());
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("guest_id", guestId);
        details.put("user_id", outcome.userId());
        details.put("upgraded_anchor_account", outcome.upgradedAnchorAccount());
        audit.event(outcome.userId(), "guest_portal.account_claimed", "booking",
                ((Number) booking.get("id")).longValue(), details,
                context.ipAddress(), context.userAgent());

        GuestPortalLoginResponse session = createAuthenticatedGuestPortalSession(
                outcome.userId(), context.ipAddress(), context.userAgent());

        return new GuestPortalClaimAccountResponse(session, outcome.username(),
                emailVerificationRequired);
    }

    /** {@code AuthService::validate_password}. */
    private static void validatePassword(String password) {
        if (password.length() < 8) {
            throw ApiError.badRequest("Password must be at least 8 characters long");
        }
        if (password.length() > 128) {
            throw ApiError.badRequest("Password must not exceed 128 characters");
        }
        if (!UPPERCASE.matcher(password).find()) {
            throw ApiError.badRequest("Password must contain at least one uppercase letter");
        }
        if (!LOWERCASE.matcher(password).find()) {
            throw ApiError.badRequest("Password must contain at least one lowercase letter");
        }
        if (!DIGIT.matcher(password).find()) {
            throw ApiError.badRequest("Password must contain at least one number");
        }
        if (!SPECIAL.matcher(password).find()) {
            throw ApiError.badRequest("Password must contain at least one special character");
        }
        String lower = password.toLowerCase();
        for (String weak : WEAK_PASSWORDS) {
            if (lower.contains(weak)) {
                throw ApiError.badRequest("Password is too common or weak");
            }
        }
    }

    private boolean usernameOrEmailExists(String username, String email) {
        Long existing = jdbc.query(
                "SELECT id FROM users WHERE username = ? OR (? IS NOT NULL AND email = ?) LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, username, email, email);
        return existing != null;
    }

    // ------------------------------------------------------------------
    // Session lifecycle
    // ------------------------------------------------------------------

    /** {@code create_authenticated_guest_portal_session}. */
    public GuestPortalLoginResponse createAuthenticatedGuestPortalSession(long userId,
            String ipAddress, String userAgent) {
        Long guestId = findGuestIdForAuthenticatedUser(userId);
        if (guestId == null) {
            throw ApiError.forbidden("Guest account required");
        }
        String token = PortalAuth.generateSessionToken();
        String tokenHash = PortalAuth.hashSessionToken(token);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusHours(SESSION_TTL_HOURS);
        jdbc.update("INSERT INTO guest_portal_sessions (guest_id, token_hash, expires_at) "
                + "VALUES (?, ?, ?)", guestId, tokenHash, expiresAt);

        GuestPortalGuestView guest = findGuestView(guestId);
        audit.event(userId, "guest_portal.login", "guest", guestId, null,
                ipAddress, userAgent);
        return new GuestPortalLoginResponse(token, expiresAt, guest);
    }

    /** {@code logout_guest_session}. */
    public void logoutGuestSession(String token) {
        String tokenHash = PortalAuth.hashSessionToken(token);
        Long guestId = resolveGuestSessionToken(token);
        jdbc.update("DELETE FROM guest_portal_sessions WHERE token_hash = ?", tokenHash);
        audit.event(null, "guest_portal.logout", "guest", guestId, null);
    }

    /** {@code require_guest_session_token} — hash lookup + expiry + touch. */
    public Long resolveGuestSessionToken(String token) {
        String tokenHash = PortalAuth.hashSessionToken(token);
        Long guestId = jdbc.query(
                "SELECT guest_id FROM guest_portal_sessions WHERE token_hash = ? "
                        + "AND expires_at > CURRENT_TIMESTAMP LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, tokenHash);
        if (guestId == null) {
            throw ApiError.unauthorized("Invalid or expired guest session");
        }
        try {
            jdbc.update("UPDATE guest_portal_sessions SET last_used_at = CURRENT_TIMESTAMP "
                    + "WHERE token_hash = ?", tokenHash);
        } catch (Exception ignored) {
        }
        return guestId;
    }

    private Long findGuestIdForAuthenticatedUser(long userId) {
        List<Long> rows = jdbc.query("""
                SELECT guest_id FROM users WHERE id = ? AND user_type::text = 'guest'
                AND guest_id IS NOT NULL AND is_active = true
                """, (rs, i) -> rs.getLong(1), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ------------------------------------------------------------------
    // Me hub
    // ------------------------------------------------------------------

    /** {@code get_me}. */
    public GuestPortalMeResponse getMe(long guestId) {
        GuestPortalGuestView guest = findGuestView(guestId);
        List<String> missing = missingProfileFields(guestId);
        return new GuestPortalMeResponse(guest, missing.isEmpty(), missing);
    }

    /** {@code update_my_profile} — normalize_and_validate + update_contact_profile. */
    @Transactional
    public GuestPortalMeResponse updateMyProfile(long guestId, GuestPortalProfileUpdate input) {
        GuestPortalProfileUpdate normalized = normalizeAndValidateProfile(input);
        String nickName = normalized.firstName() + " " + normalized.lastName();
        try {
            jdbc.update("""
                    UPDATE guests
                    SET first_name = ?, last_name = ?, nick_name = ?, phone = ?, alt_phone = ?,
                        title = ?, nationality = ?, address_line_1 = ?, city = ?, state = ?,
                        postal_code = ?, country = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, normalized.firstName(), normalized.lastName(), nickName,
                    normalized.phone(), normalized.altPhone(), normalized.title(),
                    normalized.nationality(), normalized.addressLine1(), normalized.city(),
                    normalized.stateProvince(), normalized.postalCode(), normalized.country(),
                    guestId);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw ApiError.conflict(
                    "Another guest profile already uses this name. "
                            + "Please contact the hotel for help.");
        }
        jdbc.update("UPDATE users SET full_name = ?, phone = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE guest_id = ?", nickName, normalized.phone(), guestId);
        try {
            audit.event(null, "guest_profile_updated", "guest", guestId, null);
        } catch (Exception ignored) {
        }
        return getMe(guestId);
    }

    /**
     * {@code GuestPortalProfileUpdate::normalize_and_validate} — the derives run
     * on the raw input, the values are sanitized, then the derives run again so
     * sanitizing can never smuggle in a violating value.
     */
    private static GuestPortalProfileUpdate normalizeAndValidateProfile(
            GuestPortalProfileUpdate input) {
        if (input == null) {
            throw ApiError.badRequest("required");
        }
        validateProfileFields(input.firstName(), input.lastName(), input.phone(),
                input.altPhone(), input.title(), input.nationality(), input.addressLine1(),
                input.city(), input.stateProvince(), input.postalCode(), input.country());
        GuestPortalProfileUpdate sanitized = new GuestPortalProfileUpdate(
                Sanitizer.sanitizeGuestName(input.firstName()),
                Sanitizer.sanitizeGuestName(input.lastName()),
                Sanitizer.sanitizePhone(input.phone()),
                takeOptional(input.altPhone(), v -> Sanitizer.sanitizePhone(v.trim())),
                takeOptional(input.title(), v -> truncateChars(Sanitizer.sanitizeText(v.trim()), 20)),
                takeOptional(input.nationality(),
                        v -> truncateChars(Sanitizer.sanitizeText(v.trim()), 100)),
                takeOptional(input.addressLine1(),
                        v -> truncateChars(Sanitizer.sanitizeText(v.trim()), 255)),
                takeOptional(input.city(),
                        v -> truncateChars(Sanitizer.sanitizeText(v.trim()), 100)),
                takeOptional(input.stateProvince(),
                        v -> truncateChars(Sanitizer.sanitizeText(v.trim()), 100)),
                takeOptional(input.postalCode(),
                        v -> truncateChars(Sanitizer.sanitizeText(v.trim()), 20)),
                takeOptional(input.country(),
                        v -> truncateChars(Sanitizer.sanitizeText(v.trim()), 100)));
        validateProfileFields(sanitized.firstName(), sanitized.lastName(), sanitized.phone(),
                sanitized.altPhone(), sanitized.title(), sanitized.nationality(),
                sanitized.addressLine1(), sanitized.city(), sanitized.stateProvince(),
                sanitized.postalCode(), sanitized.country());
        return sanitized;
    }

    private static void validateProfileFields(String firstName, String lastName, String phone,
            String altPhone, String title, String nationality, String addressLine1,
            String city, String stateProvince, String postalCode, String country) {
        if (firstName != null && firstName.codePointCount(0, firstName.length()) > 50) {
            throw ApiError.badRequest("First name must be at most 50 characters");
        }
        if (firstName == null || Sanitizer.sanitizeGuestName(firstName).isEmpty()) {
            throw ApiError.badRequest("required");
        }
        if (lastName != null && lastName.codePointCount(0, lastName.length()) > 50) {
            throw ApiError.badRequest("Last name must be at most 50 characters");
        }
        if (lastName == null || Sanitizer.sanitizeGuestName(lastName).isEmpty()) {
            throw ApiError.badRequest("required");
        }
        validateGuestPhone(phone);
        if (altPhone != null && !altPhone.trim().isEmpty()) {
            validateGuestPhone(altPhone);
        }
        requireMaxChars(title, 20, "Title is too long");
        requireMaxChars(nationality, 100, "Nationality is too long");
        requireMaxChars(addressLine1, 255, "Address is too long");
        requireMaxChars(city, 100, "City is too long");
        requireMaxChars(stateProvince, 100, "State or province is too long");
        requireMaxChars(postalCode, 20, "Postal code is too long");
        requireMaxChars(country, 100, "Country is too long");
    }

    /** {@code validate_guest_phone} — raw-shape check, then sanitized digits 8–15. */
    private static void validateGuestPhone(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()
                || !trimmed.chars().allMatch(c -> Character.isDigit(c)
                        || c == '+' || c == ' ' || c == '-' || c == '(' || c == ')')
                || trimmed.chars().filter(c -> c == '+').count() > 1
                || trimmed.indexOf('+') > 0) {
            throw ApiError.badRequest("invalid_phone");
        }
        String digits = Sanitizer.sanitizePhone(trimmed);
        String digitPart = digits.startsWith("+") ? digits.substring(1) : digits;
        if (digitPart.length() < 8 || digitPart.length() > 15
                || !digitPart.chars().allMatch(Character::isDigit)) {
            throw ApiError.badRequest("invalid_phone");
        }
    }

    private static void requireMaxChars(String value, int max, String message) {
        if (value != null && value.codePointCount(0, value.length()) > max) {
            throw ApiError.badRequest(message);
        }
    }

    /** {@code take_optional} — a blank result clears the column to NULL. */
    private static String takeOptional(String value, java.util.function.Function<String,
            String> transform) {
        if (value == null) {
            return null;
        }
        String result = transform.apply(value);
        return result.isEmpty() ? null : result;
    }

    /** Truncate by CHARACTER (code point), matching upstream `chars().take(n)`. */
    private static String truncateChars(String value, int max) {
        long count = value.codePointCount(0, value.length());
        if (count <= max) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, max));
    }

    /** {@code profile::completion_for_guest} — (first,last,phone) presence. */
    public List<String> missingProfileFields(long guestId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT first_name, last_name, phone FROM guests WHERE id = ? "
                        + "AND deleted_at IS NULL", guestId);
        List<String> missing = new ArrayList<>();
        if (rows.isEmpty()) {
            missing.add("first_name");
            missing.add("last_name");
            missing.add("phone");
            return missing;
        }
        Map<String, Object> row = rows.get(0);
        if (row.get("first_name") == null || row.get("first_name").toString().trim().isEmpty()) {
            missing.add("first_name");
        }
        if (row.get("last_name") == null || row.get("last_name").toString().trim().isEmpty()) {
            missing.add("last_name");
        }
        if (row.get("phone") == null || row.get("phone").toString().trim().isEmpty()) {
            missing.add("phone");
        }
        return missing;
    }

    /** {@code get_my_bookings}. */
    public GuestPortalPage<GuestPortalBookingSummary> getMyBookings(long guestId, long limit,
            long offset, String search) {
        String searchTerm = search == null || search.trim().isEmpty() ? null
                : "%" + search.trim().replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_") + "%";
        String predicate = "(? IS NULL OR b.booking_number ILIKE ? OR b.status ILIKE ? "
                + "OR b.check_in_date::text ILIKE ? OR b.check_out_date::text ILIKE ?)";

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings b WHERE b.guest_id = ? AND " + predicate,
                Long.class, guestId, searchTerm, searchTerm, searchTerm, searchTerm,
                searchTerm);

        List<GuestPortalBookingSummary> items = jdbc.query("""
                SELECT b.id, b.booking_number, b.check_in_date, b.check_out_date, b.status,
                       b.total_amount,
                       (SELECT cp.id FROM payments cp WHERE cp.booking_id = b.id
                           AND cp.status = 'completed' ORDER BY cp.id DESC LIMIT 1)
                           AS completed_payment_id,
                       (SELECT cp.payment_method FROM payments cp WHERE cp.booking_id = b.id
                           AND cp.status = 'completed' ORDER BY cp.id DESC LIMIT 1)
                           AS completed_payment_method,
                       (SELECT cp.amount FROM payments cp WHERE cp.booking_id = b.id
                           AND cp.status = 'completed' ORDER BY cp.id DESC LIMIT 1)
                           AS completed_payment_amount,
                       EXISTS(SELECT 1 FROM voucher_redemptions vr
                           JOIN promotions p ON p.id = vr.promotion_id
                           WHERE vr.booking_id = b.id AND vr.status = 'applied'
                             AND p.is_cancellable = false) AS has_non_cancellable_voucher,
                       (SELECT rp.failure_reason FROM payments rp WHERE rp.booking_id = b.id
                           AND rp.status = 'void'
                           ORDER BY rp.processed_at DESC, rp.id DESC LIMIT 1)
                           AS payment_rejection_reason,
                       (SELECT p.id FROM payments p
                           JOIN payment_receipt_requests pr ON pr.payment_id = p.id
                           WHERE p.booking_id = b.id AND p.status = 'pending'
                             AND p.payment_method = 'bank_transfer' AND pr.uploaded_at IS NULL
                           ORDER BY pr.requested_at DESC, p.id DESC LIMIT 1)
                           AS receipt_request_payment_id,
                       (SELECT pr.request_message FROM payments p
                           JOIN payment_receipt_requests pr ON pr.payment_id = p.id
                           WHERE p.booking_id = b.id AND p.status = 'pending'
                             AND pr.uploaded_at IS NULL
                           ORDER BY pr.requested_at DESC LIMIT 1)
                           AS receipt_request_message,
                       EXISTS(SELECT 1 FROM payments p
                           JOIN payment_receipt_requests pr ON pr.payment_id = p.id
                           WHERE p.booking_id = b.id AND p.status = 'pending'
                             AND pr.uploaded_at IS NOT NULL) AS receipt_uploaded
                FROM bookings b
                WHERE b.guest_id = ? AND """ + predicate + """
                ORDER BY b.check_in_date DESC, b.id DESC LIMIT ? OFFSET ?
                """, (rs, i) -> {
                    String cancellationReason = rs.getBoolean("has_non_cancellable_voucher")
                            ? "This booking uses a non-cancellable voucher." : null;
                    return new GuestPortalBookingSummary(
                            rs.getLong("id"), rs.getString("booking_number"),
                            String.valueOf(rs.getDate("check_in_date")),
                            String.valueOf(rs.getDate("check_out_date")),
                            rs.getString("status"), rs.getBigDecimal("total_amount"),
                            (Long) rs.getObject("completed_payment_id"),
                            rs.getString("completed_payment_method"),
                            rs.getBigDecimal("completed_payment_amount"),
                            false, cancellationReason,
                            rs.getString("payment_rejection_reason"),
                            (Long) rs.getObject("receipt_request_payment_id"),
                            rs.getString("receipt_request_message"),
                            rs.getBoolean("receipt_uploaded"));
                }, guestId, searchTerm, searchTerm, searchTerm, searchTerm, searchTerm,
                limit, offset);

        List<GuestPortalBookingSummary> resolved = new ArrayList<>(items.size());
        for (GuestPortalBookingSummary booking : items) {
            String reason = booking.cancellationUnavailableReason();
            if (reason == null && !CANCELLABLE_STATUSES.contains(booking.status())) {
                reason = "Only upcoming bookings can be cancelled online.";
            }
            resolved.add(new GuestPortalBookingSummary(booking.id(), booking.bookingNumber(),
                    booking.checkInDate(), booking.checkOutDate(), booking.status(),
                    booking.totalAmount(), booking.completedPaymentId(),
                    booking.completedPaymentMethod(), booking.completedPaymentAmount(),
                    reason == null, reason, booking.paymentRejectionReason(),
                    booking.receiptRequestPaymentId(), booking.receiptRequestMessage(),
                    booking.receiptUploaded()));
        }
        return new GuestPortalPage<>(resolved, total == null ? 0 : total);
    }

    /** {@code cancel_my_booking}. */
    public Map<String, Object> cancelMyBooking(long guestId, long bookingId,
            GuestBookingCancellationRequest request) {
        String reason = request == null || request.reason() == null ? null
                : request.reason().trim();
        if (reason != null && reason.isEmpty()) {
            reason = null;
        }
        if (reason != null && reason.codePointCount(0, reason.length()) > 1_000) {
            throw ApiError.badRequest(
                    "Cancellation reason must be 1,000 characters or fewer");
        }
        GuestPortalBookingSummary booking = getMyBookings(guestId, 10_000, 0, null)
                .items().stream()
                .filter(b -> b.id() == bookingId)
                .findFirst()
                .orElse(null);
        if (booking == null) {
            throw ApiError.notFound("Booking not found");
        }
        if (!booking.canCancel()) {
            throw ApiError.conflict(booking.cancellationUnavailableReason() != null
                    ? booking.cancellationUnavailableReason()
                    : "This booking cannot be cancelled.");
        }
        Long userId = bookings.findGuestUserId(guestId);
        if (userId == null) {
            throw ApiError.forbidden("No active guest account is linked to this booking.");
        }
        return paymentTx.cancelPendingBookingByGuest(userId, bookingId, reason);
    }

    /** {@code get_my_transactions}. */
    public GuestPortalPage<GuestPortalTransaction> getMyTransactions(long guestId, long limit,
            long offset) {
        String union = """
                SELECT 'payment' AS kind, p.created_at AS occurred_at, p.amount AS amount,
                       p.payment_method AS method, p.transaction_id AS reference,
                       NULL AS invoice_number, b.booking_number AS booking_number,
                       p.status AS status
                FROM payments p JOIN bookings b ON b.id = p.booking_id
                WHERE b.guest_id = ?
                UNION ALL
                SELECT 'invoice' AS kind, i.created_at AS occurred_at, i.total_amount AS amount,
                       NULL AS method, NULL AS reference,
                       i.invoice_number AS invoice_number, b2.booking_number AS booking_number,
                       i.status AS status
                FROM invoices i JOIN bookings b2 ON b2.id = i.booking_id
                WHERE i.bill_to_guest_id = ?
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + union + ") t",
                Long.class, guestId, guestId);
        List<GuestPortalTransaction> items = jdbc.query(
                "SELECT * FROM (" + union + ") t ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> new GuestPortalTransaction(
                        rs.getString("kind"), rs.getObject("occurred_at"),
                        rs.getBigDecimal("amount"), rs.getString("method"),
                        rs.getString("reference"), rs.getString("invoice_number"),
                        rs.getString("booking_number"), rs.getString("status")),
                guestId, guestId, limit, offset);
        return new GuestPortalPage<>(items, total == null ? 0 : total);
    }

    /** {@code get_my_membership}. */
    public GuestPortalMembershipResponse getMyMembership(long guestId) {
        GuestPortalMembership membership = findMembership(guestId);
        List<GuestPortalPointsActivity> activity = membership == null
                ? List.of() : recentPointsActivity(guestId);
        return new GuestPortalMembershipResponse(membership, activity);
    }

    /** {@code get_my_benefits}. */
    public GuestPortalBenefitsResponse getMyBenefits(long guestId) {
        GuestPortalMembership membership = findMembership(guestId);
        List<GuestPortalTierBenefit> tierBenefits = membership == null
                ? List.of() : tierBenefits(guestId);
        int pointsBalance = membership == null ? 0 : membership.pointsBalance();
        return new GuestPortalBenefitsResponse(tierBenefits, rewards(pointsBalance));
    }

    /** {@code get_my_credits}. */
    public GuestPortalCreditsResponse getMyCredits(long guestId) {
        List<GuestPortalRoomTypeCredit> credits = complimentaryCredits(guestId);
        int total = credits.stream().mapToInt(GuestPortalRoomTypeCredit::nightsAvailable).sum();
        return new GuestPortalCreditsResponse(total, credits);
    }

    // ------------------------------------------------------------------
    // Payment entry points
    // ------------------------------------------------------------------

    /** {@code resolve_owned_booking} — the session-scoped booking guard. */
    private Map<String, Object> resolveOwnedBooking(long guestId, long bookingId) {
        Map<String, Object> booking = bookings.fetchBooking(bookingId);
        if (((Number) booking.get("guest_id")).longValue() != guestId) {
            throw ApiError.forbidden("This booking does not belong to you.");
        }
        return booking;
    }

    public PaymentActionResponse sessionBankTransfer(long guestId, long bookingId) {
        return payments.createBankTransferClaim(resolveOwnedBooking(guestId, bookingId));
    }

    public void sessionUploadPaymentReceipt(long guestId, long paymentId, byte[] bytes) {
        Map<String, Object> payment = payments.getPaymentForReview(paymentId);
        if (payment == null) {
            throw ApiError.notFound("Payment not found.");
        }
        Object paymentGuestId = payment.get("guest_id");
        if (paymentGuestId == null
                || ((Number) paymentGuestId).longValue() != guestId) {
            throw ApiError.forbidden("This payment does not belong to you.");
        }
        payments.savePaymentReceipt(paymentId, bytes);
    }

    public PaypalCreateOrderResponse sessionCreatePaypalOrder(long guestId, long bookingId) {
        return payments.createPaypalOrder(resolveOwnedBooking(guestId, bookingId));
    }

    public PaymentActionResponse sessionCapturePaypal(long guestId,
            SessionPaypalCaptureRequest input) {
        Map<String, Object> booking = resolveOwnedBooking(guestId, input.bookingId());
        return payments.capturePaypalPayment(booking, input.orderId(), input.paymentId());
    }

    public PaymentActionResponse tokenBankTransfer(String token) {
        return payments.createBankTransferClaim(requireValidToken(token));
    }

    public void tokenUploadPaymentReceipt(String token, long paymentId, byte[] bytes) {
        Map<String, Object> booking = requireValidToken(token);
        Map<String, Object> payment = payments.getPaymentForReview(paymentId);
        if (payment == null) {
            throw ApiError.notFound("Payment not found.");
        }
        if (((Number) payment.get("booking_id")).longValue()
                != ((Number) booking.get("id")).longValue()) {
            throw ApiError.forbidden("This payment does not belong to this booking.");
        }
        payments.savePaymentReceipt(paymentId, bytes);
    }

    public PaypalCreateOrderResponse tokenCreatePaypalOrder(String token) {
        return payments.createPaypalOrder(requireValidToken(token));
    }

    public PaymentActionResponse tokenCapturePaypal(String token, PaypalCaptureRequest input) {
        Map<String, Object> booking = requireValidToken(token);
        return payments.capturePaypalPayment(booking, input.orderId(), input.paymentId());
    }

    // ------------------------------------------------------------------
    // SQL helpers (portal repositories)
    // ------------------------------------------------------------------

    private static final String BOOKING_SELECT =
            "SELECT id, booking_number, guest_id, room_id, check_in_date, check_out_date, "
                    + "room_rate, subtotal, tax_amount, discount_amount, total_amount, currency, "
                    + "status, payment_status, adults, children, special_requests, remarks, source, "
                    + "market_code, discount_percentage, rate_override_weekday, "
                    + "rate_override_weekend, pre_checkin_completed, pre_checkin_completed_at, "
                    + "pre_checkin_token, pre_checkin_token_expires_at, created_by, created_at, "
                    + "updated_at FROM bookings";

    private Map<String, Object> findEligibleBookingByNumber(String bookingNumber) {
        List<Map<String, Object>> rows = jdbc.queryForList(BOOKING_SELECT
                + " WHERE booking_number = ? AND status IN "
                + "('confirmed', 'pending', 'pending_payment', 'pending_confirmation')",
                bookingNumber);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findBookingByToken(String token) {
        String hashed = PortalAuth.persistBookingAccessToken(token);
        List<Map<String, Object>> rows = jdbc.queryForList(BOOKING_SELECT
                + " WHERE pre_checkin_token = ? "
                + "OR (pre_checkin_token = ? AND pre_checkin_token NOT LIKE 'sha256:%')",
                hashed, token);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findGuest(long guestId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, nick_name, first_name, last_name, email, phone, ic_number,
                       nationality, address_line_1 AS address_line1, city,
                       state AS state_province, postal_code, country, title, alt_phone,
                       is_active, guest_type, tourism_type,
                       COALESCE(discount_percentage, 0) AS discount_percentage, company_name,
                       COALESCE(complimentary_nights_credit, 0) AS complimentary_nights_credit,
                       created_at, updated_at
                FROM guests WHERE id = ?
                """, guestId);
        if (rows.isEmpty()) {
            throw ApiError.internal("Failed to fetch guest");
        }
        return rows.get(0);
    }

    private GuestPortalGuestView findGuestView(long guestId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT nick_name, first_name, last_name, title, email, phone, alt_phone,
                       ic_number, nationality, address_line_1 AS address_line1, city,
                       state AS state_province, postal_code, country
                FROM guests WHERE id = ?
                """, guestId);
        if (rows.isEmpty()) {
            throw ApiError.internal("Failed to fetch guest profile");
        }
        Map<String, Object> row = rows.get(0);
        return new GuestPortalGuestView(
                str(row.get("nick_name")), str(row.get("first_name")), str(row.get("last_name")),
                str(row.get("title")), str(row.get("email")), str(row.get("phone")),
                str(row.get("alt_phone")), str(row.get("ic_number")), str(row.get("nationality")),
                str(row.get("address_line1")), str(row.get("city")),
                str(row.get("state_province")), str(row.get("postal_code")), str(row.get("country")));
    }

    private GuestPortalBookingResponse portalResponse(Map<String, Object> booking) {
        Map<String, Object> guest =
                findGuest(((Number) booking.get("guest_id")).longValue());
        var ekycSummary = autoCheckin.autoCheckinEligibility(
                ((Number) booking.get("id")).longValue());
        Map<String, Object> receipt = jdbc.queryForMap("""
                SELECT
                    (SELECT p.id FROM payments p
                        JOIN payment_receipt_requests pr ON pr.payment_id = p.id
                        WHERE p.booking_id = ? AND p.status = 'pending'
                          AND p.payment_method = 'bank_transfer' AND pr.uploaded_at IS NULL
                        ORDER BY pr.requested_at DESC, p.id DESC LIMIT 1)
                        AS receipt_request_payment_id,
                    (SELECT pr.request_message FROM payments p
                        JOIN payment_receipt_requests pr ON pr.payment_id = p.id
                        WHERE p.booking_id = ? AND p.status = 'pending'
                          AND pr.uploaded_at IS NULL
                        ORDER BY pr.requested_at DESC LIMIT 1)
                        AS receipt_request_message,
                    EXISTS(
                        SELECT 1 FROM payments p
                        JOIN payment_receipt_requests pr ON pr.payment_id = p.id
                        WHERE p.booking_id = ? AND p.status = 'pending'
                          AND pr.uploaded_at IS NOT NULL
                    ) AS receipt_uploaded
                """, booking.get("id"), booking.get("id"), booking.get("id"));

        return new GuestPortalBookingResponse(bookingView(booking), guestView(guest), ekycSummary,
                (Long) receipt.get("receipt_request_payment_id"),
                (String) receipt.get("receipt_request_message"),
                Boolean.TRUE.equals(receipt.get("receipt_uploaded")));
    }

    private static GuestPortalBookingView bookingView(Map<String, Object> booking) {
        return new GuestPortalBookingView(
                ((Number) booking.get("id")).longValue(),
                str(booking.get("booking_number")),
                String.valueOf(booking.get("check_in_date")),
                String.valueOf(booking.get("check_out_date")),
                str(booking.get("status")),
                (Integer) booking.get("adults"),
                (Integer) booking.get("children"),
                str(booking.get("special_requests")),
                str(booking.get("market_code")),
                (Boolean) booking.get("pre_checkin_completed"),
                booking.get("pre_checkin_completed_at"));
    }

    private static GuestPortalGuestView guestView(Map<String, Object> guest) {
        return new GuestPortalGuestView(
                str(guest.get("nick_name")), str(guest.get("first_name")),
                str(guest.get("last_name")), str(guest.get("title")), str(guest.get("email")),
                str(guest.get("phone")), str(guest.get("alt_phone")), str(guest.get("ic_number")),
                str(guest.get("nationality")), str(guest.get("address_line1")),
                str(guest.get("city")), str(guest.get("state_province")),
                str(guest.get("postal_code")), str(guest.get("country")));
    }

    private void updateGuestPrecheckin(long guestId, GuestUpdateInput update) {
        List<String> parts = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        addUpdate(parts, values, "first_name", update.firstName());
        addUpdate(parts, values, "last_name", update.lastName());
        if (update.email() != null) {
            if (update.email().isEmpty()) {
                parts.add("email = NULL");
            } else {
                addUpdate(parts, values, "email", update.email());
            }
        }
        addUpdate(parts, values, "phone", update.phone());
        addUpdate(parts, values, "alt_phone", update.altPhone());
        addUpdate(parts, values, "nationality", update.nationality());
        addUpdate(parts, values, "address_line_1", update.addressLine1());
        addUpdate(parts, values, "city", update.city());
        addUpdate(parts, values, "state", update.stateProvince());
        addUpdate(parts, values, "postal_code", update.postalCode());
        addUpdate(parts, values, "country", update.country());
        addUpdate(parts, values, "title", update.title());
        addUpdate(parts, values, "ic_number", update.icNumber());
        if (parts.isEmpty()) {
            return;
        }
        values.add(guestId);
        jdbc.update("UPDATE guests SET " + String.join(", ", parts) + " WHERE id = ?",
                values.toArray());
    }

    /** {@code push_update}: {@code column = ?} only when the patch field is present. */
    private static void addUpdate(List<String> parts, List<Object> values,
            String column, String value) {
        if (value != null) {
            parts.add(column + " = ?");
            values.add(value);
        }
    }

    private void updateBookingPrecheckin(long bookingId, String marketCode,
            String specialRequests) {
        List<String> updates = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        if (marketCode != null) {
            updates.add("market_code = ?");
            values.add(marketCode);
        }
        if (specialRequests != null) {
            updates.add("special_requests = ?");
            values.add(specialRequests);
        }
        updates.add("pre_checkin_completed = true");
        updates.add("pre_checkin_completed_at = CURRENT_TIMESTAMP");
        values.add(bookingId);
        jdbc.update("UPDATE bookings SET " + String.join(", ", updates) + " WHERE id = ?",
                values.toArray());
    }

    private GuestPortalMembership findMembership(long guestId) {
        List<GuestPortalMembership> rows = jdbc.query("""
                SELECT m.member_number AS member_number, m.status AS status,
                       t.name AS tier_name, t.sort_order AS tier_level,
                       a.lifetime_points AS lifetime_points,
                       COALESCE((SELECT SUM(lt.available_delta) FROM loyalty_transactions lt
                                 WHERE lt.member_id = m.id), 0) AS points_balance
                FROM loyalty_members m
                JOIN loyalty_accounts a ON a.member_id = m.id
                JOIN loyalty_tiers t ON t.id = a.current_tier_id
                WHERE m.guest_id = ? LIMIT 1
                """, (rs, i) -> {
                    long balance = rs.getLong("points_balance");
                    if (balance > Integer.MAX_VALUE || balance < Integer.MIN_VALUE) {
                        throw ApiError.internal("Loyalty points balance is out of range.");
                    }
                    return new GuestPortalMembership(
                            rs.getString("member_number"), rs.getString("tier_name"),
                            rs.getInt("tier_level"), (int) balance,
                            rs.getInt("lifetime_points"), rs.getString("status"));
                }, guestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<GuestPortalPointsActivity> recentPointsActivity(long guestId) {
        return jdbc.query("""
                SELECT lt.created_at AS occurred_at, lt.transaction_type AS transaction_type,
                       lt.points_delta AS points, lt.balance_after AS balance_after,
                       lt.description AS reason, b.booking_number AS booking_number,
                       CASE WHEN lt.transaction_type = 'adjusted'
                            THEN COALESCE(NULLIF(TRIM(u.full_name), ''), u.username)
                            ELSE NULL END AS adjusted_by
                FROM loyalty_transactions lt
                JOIN loyalty_members m ON m.id = lt.member_id
                LEFT JOIN bookings b ON b.id = lt.booking_id
                LEFT JOIN users u ON u.id = lt.actor_user_id
                WHERE m.guest_id = ?
                ORDER BY lt.created_at DESC, lt.id DESC LIMIT 20
                """, (rs, i) -> new GuestPortalPointsActivity(
                rs.getObject("occurred_at"), rs.getString("transaction_type"),
                rs.getInt("points"), rs.getInt("balance_after"), rs.getString("reason"),
                rs.getString("booking_number"), rs.getString("adjusted_by")), guestId);
    }

    private List<GuestPortalTierBenefit> tierBenefits(long guestId) {
        return jdbc.query("""
                SELECT t.name AS tier_name
                FROM loyalty_members m
                JOIN loyalty_accounts a ON a.member_id = m.id
                JOIN loyalty_tiers t ON t.id = a.current_tier_id
                WHERE m.guest_id = ? LIMIT 1
                """, (rs, i) -> new GuestPortalTierBenefit(rs.getString("tier_name"),
                BigDecimal.ZERO), guestId);
    }

    private List<GuestPortalReward> rewards(int pointsBalance) {
        Set<String> seen = new HashSet<>();
        return jdbc.query("""
                SELECT id, name, description, category, points_cost
                FROM loyalty_rewards
                WHERE is_active = true
                  AND (valid_from IS NULL OR valid_from <= CURRENT_DATE)
                  AND (valid_to IS NULL OR valid_to >= CURRENT_DATE)
                ORDER BY points_cost ASC, id ASC
                """, (rs, i) -> {
                    int pointsRequired = rs.getInt("points_cost");
                    String key = rs.getString("name") + "|" + rs.getString("description")
                            + "|" + rs.getString("category") + "|" + pointsRequired;
                    if (!seen.add(key)) {
                        return null;
                    }
                    return new GuestPortalReward(rs.getLong("id"), rs.getString("name"),
                            rs.getString("description"), rs.getString("category"),
                            pointsRequired, pointsBalance >= pointsRequired);
                }).stream().filter(java.util.Objects::nonNull).toList();
    }

    private List<GuestPortalRoomTypeCredit> complimentaryCredits(long guestId) {
        return jdbc.query("""
                SELECT rt.id AS room_type_id, rt.code AS room_type_code,
                       rt.name AS room_type_name, gc.nights_available
                FROM guest_complimentary_credits gc
                JOIN room_types rt ON rt.id = gc.room_type_id
                WHERE gc.guest_id = ? AND gc.nights_available > 0
                ORDER BY rt.name ASC, rt.id ASC
                """, (rs, i) -> new GuestPortalRoomTypeCredit(
                rs.getLong("room_type_id"), rs.getString("room_type_code"),
                rs.getString("room_type_name"), rs.getInt("nights_available")), guestId);
    }

    // ------------------------------------------------------------------
    // Small mapping helpers
    // ------------------------------------------------------------------

    private record LocalDateRange(java.time.LocalDate checkIn, java.time.LocalDate checkOut) {
    }

    private static LocalDateRange bookingDates(Map<String, Object> booking) {
        return new LocalDateRange(toLocalDate(booking.get("check_in_date")),
                toLocalDate(booking.get("check_out_date")));
    }

    private static java.time.LocalDate toLocalDate(Object value) {
        if (value instanceof java.time.LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return java.time.LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
