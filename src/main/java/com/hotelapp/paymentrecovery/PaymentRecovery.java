package com.hotelapp.paymentrecovery;

import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.settings.HotelSettings;
import com.hotelapp.email.EmailLayout;
import com.hotelapp.paymentrecovery.PaymentRecoveryModels.Capability;
import com.hotelapp.paymentrecovery.PaymentRecoveryModels.PaymentRecoveryView;
import com.hotelapp.portal.PortalAuth;
import com.hotelapp.portal.PortalBookingOps;
import com.hotelapp.portal.PortalModels.PaymentActionResponse;
import com.hotelapp.portal.PortalModels.PaypalCreateOrderResponse;
import com.hotelapp.portal.PortalPayments;
import com.hotelapp.portal.PaypalClient;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Port of {@code services/payment_retry.rs} + {@code repositories/payment_retry.rs}:
 * minting and resolving emailed payment-retry capabilities.
 *
 * The capability authorises exactly one thing — replacing one rejected payment
 * on one booking — and is kept separate from the booking-access token so
 * recovering a payment never widens into pre-check-in or profile access.
 * A 256-bit random hex token goes in the mail; only its prefixed SHA-256 is
 * persisted, exactly like the booking-access scheme.
 */
@Component
public class PaymentRecovery {

    /** How long a freshly issued recovery link stays usable. */
    public static final long RETRY_CAPABILITY_TTL_MINUTES = 60;

    /** Raw token length: 32 random bytes rendered as hex. */
    private static final int CAPABILITY_TOKEN_HEX_LEN = 64;

    /** Payment states that mean the booking is already settled. */
    private static final List<String> SETTLED_PAYMENT_STATUSES =
            List.of("paid", "paid_rate", "refunded", "void");

    /** {@code BOOKING_STATUSES_AWAITING_PAYMENT} from services/payments.rs. */
    private static final List<String> RECOVERABLE_BOOKING_STATUSES =
            List.of("pending", "pending_payment");

    private static final String CAPABILITY_COLUMNS =
            "id, booking_id, payment_id, expires_at, consumed_at, replacement_payment_id";

    private final JdbcTemplate jdbc;
    private final PortalBookingOps bookings;
    private final PortalPayments payments;
    private final PaypalClient paypal;
    private final HotelSettings settings;
    private final AppProperties props;

    public PaymentRecovery(JdbcTemplate jdbc, PortalBookingOps bookings,
            PortalPayments payments, PaypalClient paypal, HotelSettings settings,
            AppProperties props) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.payments = payments;
        this.paypal = paypal;
        this.settings = settings;
        this.props = props;
    }

    // ------------------------------------------------------------------
    // Token handling (services/payment_retry.rs)
    // ------------------------------------------------------------------

    /** Value stored in {@code payment_retry_capabilities.token_hash} — {@code sha256:<hex>}. */
    static String persistCapabilityToken(String token) {
        return PortalAuth.persistBookingAccessToken(token);
    }

    /** Reject anything that cannot be a freshly minted token before it reaches the database. */
    static boolean isWellFormedCapabilityToken(String presented) {
        if (presented == null || presented.length() != CAPABILITY_TOKEN_HEX_LEN) {
            return false;
        }
        for (int i = 0; i < presented.length(); i++) {
            char c = presented.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code capability_expiry_at}: the hold deadline caps the TTL whenever it
     * lands first — a lapsed hold means a released room, so the link must not
     * outlive it. A deadline already in the past yields an already-expired
     * capability ("do not send a recovery link at all").
     */
    static OffsetDateTime capabilityExpiryAt(OffsetDateTime issuedAt,
            OffsetDateTime holdDeadline) {
        OffsetDateTime ttlExpiry = issuedAt.plusMinutes(RETRY_CAPABILITY_TTL_MINUTES);
        if (holdDeadline != null && holdDeadline.isBefore(ttlExpiry)) {
            return holdDeadline;
        }
        return ttlExpiry;
    }

    /** {@code is_recoverable} — whether a booking in this state may get a link. */
    static boolean isRecoverable(String bookingStatus, String paymentStatus) {
        return RECOVERABLE_BOOKING_STATUSES.contains(bookingStatus)
                && !SETTLED_PAYMENT_STATUSES.contains(paymentStatus);
    }

    /** The single message every unusable link gets — deliberately unspecific. */
    static ApiError unavailable() {
        return ApiError.notFound("This payment link is no longer available. "
                + "Please contact the hotel to complete your booking.");
    }

    // ------------------------------------------------------------------
    // Repository (repositories/payment_retry.rs)
    // ------------------------------------------------------------------

    private Capability mapCapability(Map<String, Object> row) {
        return new Capability(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("booking_id")).longValue(),
                (Number) row.get("payment_id") == null ? null
                        : ((Number) row.get("payment_id")).longValue(),
                (OffsetDateTime) row.get("expires_at"),
                (OffsetDateTime) row.get("consumed_at"),
                row.get("replacement_payment_id") == null ? null
                        : ((Number) row.get("replacement_payment_id")).longValue());
    }

    /** {@code find_by_token_hash} — lookup deliberately does not mutate. */
    Capability findByTokenHash(String tokenHash) {
        List<Capability> rows = jdbc.query(
                "SELECT " + CAPABILITY_COLUMNS
                        + " FROM payment_retry_capabilities WHERE token_hash = ?",
                (rs, i) -> new Capability(
                        rs.getLong("id"),
                        rs.getLong("booking_id"),
                        rs.getObject("payment_id") == null ? null : rs.getLong("payment_id"),
                        rs.getObject("expires_at", OffsetDateTime.class),
                        rs.getObject("consumed_at", OffsetDateTime.class),
                        rs.getObject("replacement_payment_id") == null ? null
                                : rs.getLong("replacement_payment_id")),
                tokenHash);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code expire} — retire a capability by expiring it in place. */
    void expire(long capabilityId) {
        jdbc.update("""
                UPDATE payment_retry_capabilities SET expires_at = CURRENT_TIMESTAMP
                WHERE id = ? AND consumed_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                """, capabilityId);
    }

    /** {@code find_live_for_booking} — outstanding capabilities, newest first. */
    List<Capability> findLiveForBooking(long bookingId) {
        return jdbc.query(
                "SELECT " + CAPABILITY_COLUMNS + " FROM payment_retry_capabilities "
                        + "WHERE booking_id = ? AND consumed_at IS NULL "
                        + "AND expires_at > CURRENT_TIMESTAMP ORDER BY id DESC",
                (rs, i) -> new Capability(
                        rs.getLong("id"),
                        rs.getLong("booking_id"),
                        rs.getObject("payment_id") == null ? null : rs.getLong("payment_id"),
                        rs.getObject("expires_at", OffsetDateTime.class),
                        rs.getObject("consumed_at", OffsetDateTime.class),
                        rs.getObject("replacement_payment_id") == null ? null
                                : rs.getLong("replacement_payment_id")),
                bookingId);
    }

    // ------------------------------------------------------------------
    // Service (services/payment_retry.rs)
    // ------------------------------------------------------------------

    /**
     * {@code resolve_capability} — every failure mode (malformed, unknown,
     * expired) returns the same generic error so the endpoint cannot probe
     * which tokens ever existed. A consumed row is returned, not rejected: a
     * duplicate submission must resolve to the payment it already produced.
     */
    Capability resolveCapability(String presented) {
        if (!isWellFormedCapabilityToken(presented)) {
            throw unavailable();
        }
        Capability capability = findByTokenHash(persistCapabilityToken(presented));
        if (capability == null) {
            throw unavailable();
        }
        if (!capability.isConsumed()
                && !capability.isSpendableAt(OffsetDateTime.now())) {
            throw unavailable();
        }
        return capability;
    }

    /** {@code issue_capability} — mint + persist; returns the raw token for the link. */
    String issueCapability(long bookingId, Long paymentId, OffsetDateTime holdDeadline) {
        OffsetDateTime issuedAt = OffsetDateTime.now();
        OffsetDateTime expiresAt = capabilityExpiryAt(issuedAt, holdDeadline);
        if (!expiresAt.isAfter(issuedAt)) {
            throw ApiError.badRequest("The booking hold has already lapsed, "
                    + "so no recovery link can be issued.");
        }
        String token = PortalAuth.generateSessionToken();
        jdbc.update("""
                INSERT INTO payment_retry_capabilities
                    (booking_id, payment_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, bookingId, paymentId, persistCapabilityToken(token), expiresAt);
        return token;
    }

    /** When this booking's unpaid hold lapses, if auto-release is switched on. */
    OffsetDateTime holdDeadlineFor(OffsetDateTime createdAt) {
        int hours = settings.getPositiveInt("unpaid_hold_release_hours", 0);
        if (hours <= 0) {
            return null;
        }
        return createdAt.plusHours(hours);
    }

    /**
     * {@code issue_recovery_link} — issue (or replace) a link for a booking
     * whose payment was rejected. Returns null — "send the mail, but no
     * self-service repayment" — whenever the booking is settled or past the
     * point where an emailed payment makes sense.
     */
    public Map<String, Object> issueRecoveryLink(long bookingId, long paymentId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status, payment_status, created_at FROM bookings WHERE id = ?",
                bookingId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> context = rows.get(0);
        String paymentStatus = context.get("payment_status") == null
                ? "unpaid" : String.valueOf(context.get("payment_status"));
        if (!isRecoverable(String.valueOf(context.get("status")), paymentStatus)) {
            return null;
        }

        // The raw token is unrecoverable by design, so a live capability can be
        // detected but not re-linked. Retire it and mint a fresh one, keeping
        // exactly one live capability per booking.
        List<Capability> live = findLiveForBooking(bookingId);
        if (!live.isEmpty()) {
            expire(live.get(0).id());
        }

        OffsetDateTime holdDeadline = holdDeadlineFor(
                (OffsetDateTime) context.get("created_at"));
        String token = issueCapability(bookingId, paymentId, holdDeadline);
        Capability minted = findByTokenHash(persistCapabilityToken(token));
        return Map.of(
                "url", EmailLayout.absoluteUrl(props, "/booking/recover-payment/" + token),
                "expires_at", minted == null ? holdDeadline : minted.expiresAt());
    }

    /**
     * {@code describe_recovery} — read-only on purpose: consuming on view would
     * let a mail scanner spend the guest's one attempt before they saw the page.
     */
    public PaymentRecoveryView describeRecovery(String presented) {
        Capability capability = resolveCapability(presented);
        Map<String, Object> booking = fetchBookingOrUnavailable(capability.bookingId());

        // The booking may have moved on since the mail was sent — paid at the
        // desk, cancelled, released. The link is not an entitlement to pay.
        if (!capability.isConsumed() && !isRecoverable(
                String.valueOf(booking.get("status")),
                booking.get("payment_status") == null ? "unpaid"
                        : String.valueOf(booking.get("payment_status")))) {
            throw unavailable();
        }

        // Ask the payment itself whether it still wants evidence.
        boolean receiptUploadable = false;
        if (capability.replacementPaymentId() != null) {
            Map<String, Object> payment =
                    payments.getPaymentForReview(capability.replacementPaymentId());
            receiptUploadable = payment != null
                    && "bank_transfer".equals(String.valueOf(payment.get("payment_method")))
                    && "pending".equals(String.valueOf(payment.get("status")));
        }

        // Only advertise what the deployment can actually take.
        List<String> methods = new ArrayList<>(List.of("bank_transfer"));
        if (paypal.isEnabled()) {
            methods.add("paypal");
        }

        Object total = booking.get("total_amount");
        return new PaymentRecoveryView(
                String.valueOf(booking.get("booking_number")),
                total instanceof BigDecimal dec ? dec.toPlainString() : String.valueOf(total),
                booking.get("currency") == null ? "MYR"
                        : String.valueOf(booking.get("currency")),
                capability.expiresAt(),
                methods,
                props.paypalPublicClientId(),
                capability.replacementPaymentId(),
                receiptUploadable,
                capability.isConsumed());
    }

    /**
     * {@code recover_with_bank_transfer} — a second submission does not create
     * a second payment: a spent capability resolves to the payment it already
     * produced. The authoritative consume happens inside the claim transaction.
     */
    public PaymentActionResponse recoverWithBankTransfer(String presented) {
        Capability capability = resolveCapability(presented);
        if (capability.replacementPaymentId() != null) {
            return new PaymentActionResponse(
                    capability.replacementPaymentId(), "pending", "pending_confirmation");
        }
        Map<String, Object> booking = fetchBookingOrUnavailable(capability.bookingId());
        return payments.createBankTransferClaim(booking, capability.id());
    }

    /**
     * {@code recover_with_paypal} — a duplicate submission resolves to the
     * order it already produced so the guest finishes the order they have.
     */
    public PaypalCreateOrderResponse recoverWithPaypal(String presented) {
        Capability capability = resolveCapability(presented);
        if (capability.replacementPaymentId() != null) {
            String orderId = payments.findGatewayOrderId(capability.replacementPaymentId());
            if (orderId == null) {
                throw ApiError.conflict(
                        "A payment is already in progress for this booking.");
            }
            return new PaypalCreateOrderResponse(orderId, capability.replacementPaymentId());
        }
        Map<String, Object> booking = fetchBookingOrUnavailable(capability.bookingId());
        return payments.createPaypalOrder(booking, capability.id());
    }

    /**
     * {@code capture_recovered_paypal} — deliberately works on a *spent*
     * capability: creating the order consumes the link, but the guest still
     * has to approve it in PayPal's window and come back.
     */
    public PaymentActionResponse captureRecoveredPaypal(String presented,
            String orderId, long paymentId) {
        Capability capability = resolveCapability(presented);
        if (capability.replacementPaymentId() == null
                || capability.replacementPaymentId() != paymentId) {
            throw ApiError.forbidden(
                    "This payment link does not authorise that payment.");
        }
        Map<String, Object> booking = fetchBookingOrUnavailable(capability.bookingId());
        return payments.capturePaypalPayment(booking, orderId, paymentId);
    }

    /**
     * {@code upload_recovered_receipt} — scoped to the payment the capability
     * produced; the size/type/state checks are {@code save_payment_receipt}'s.
     */
    public void uploadRecoveredReceipt(String presented, long paymentId, byte[] bytes) {
        Capability capability = resolveCapability(presented);
        if (capability.replacementPaymentId() == null
                || capability.replacementPaymentId() != paymentId) {
            throw ApiError.forbidden(
                    "This payment link does not authorise that payment.");
        }
        payments.savePaymentReceipt(paymentId, bytes);
    }

    private Map<String, Object> fetchBookingOrUnavailable(long bookingId) {
        try {
            return bookings.fetchBooking(bookingId);
        } catch (ApiError e) {
            throw unavailable();
        }
    }
}
