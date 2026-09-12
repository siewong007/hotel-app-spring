package com.hotelapp.consent;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.portal.PortalModels.ConsentAcceptance;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Port of {@code modules/consent}: the document vocabulary, the
 * required-consent checks, and the consent_records writer shared by
 * registration, booking, payment, eKYC and the guest portal.
 */
@Component
public class Consents {

    /** mirrors ConsentDocument::as_str / current_version. */
    public enum Document {
        TERMS_OF_SERVICE("terms_of_service", "Booking Terms and Conditions"),
        PRIVACY_NOTICE("privacy_notice", "Privacy Notice"),
        PAYMENT_TERMS("payment_terms", "Payment Terms"),
        EKYC_BIOMETRIC("ekyc_biometric", "identity verification consent");

        private final String wire;
        private final String humanName;

        Document(String wire, String humanName) {
            this.wire = wire;
            this.humanName = humanName;
        }

        public String wire() {
            return wire;
        }

        public String humanName() {
            return humanName;
        }

        /** The version the server currently publishes (CONSENT_DOCUMENT_VERSIONS). */
        public String currentVersion() {
            return "2026-09-09";
        }

        public static Document fromWire(String value) {
            for (Document doc : values()) {
                if (doc.wire.equals(value)) {
                    return doc;
                }
            }
            return null;
        }
    }

    /** mirrors ConsentSource::as_str. */
    public enum Source {
        REGISTRATION("registration"),
        ONLINE_BOOKING("online_booking"),
        PAYMENT("payment"),
        EKYC("ekyc"),
        GUEST_PORTAL("guest_portal"),
        FRONT_DESK("front_desk");

        private final String wire;

        Source(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }
    }

    /** mirrors ConsentSubjectType::as_str. */
    public enum SubjectType {
        USER("user"),
        GUEST("guest"),
        ANONYMOUS("anonymous");

        private final String wire;

        SubjectType(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }
    }

    /** Who a batch of consent belongs to — resolved server-side. */
    public record Subject(
            SubjectType subjectType, Long userId, Long guestId, Long bookingId) {

        public static Subject user(long userId) {
            return new Subject(SubjectType.USER, userId, null, null);
        }

        public static Subject guest(long guestId) {
            return new Subject(SubjectType.GUEST, null, guestId, null);
        }

        public static Subject anonymousBooking(long guestId, long bookingId) {
            return new Subject(SubjectType.ANONYMOUS, null, guestId, bookingId);
        }

        public Subject withUser(long userId) {
            return new Subject(subjectType, userId, guestId, bookingId);
        }

        public Subject withGuest(long guestId) {
            return new Subject(subjectType, userId, guestId, bookingId);
        }

        public Subject withBooking(long bookingId) {
            return new Subject(subjectType, userId, guestId, bookingId);
        }
    }

    /** Request context captured alongside a consent decision. */
    public record Context(String ipAddress, String userAgent) {
    }

    public static final List<Document> REGISTRATION_REQUIRED =
            List.of(Document.TERMS_OF_SERVICE, Document.PRIVACY_NOTICE);
    public static final List<Document> BOOKING_REQUIRED =
            List.of(Document.TERMS_OF_SERVICE, Document.PRIVACY_NOTICE);
    public static final List<Document> EKYC_REQUIRED = List.of(Document.EKYC_BIOMETRIC);

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public Consents(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /**
     * {@code validate_locales}: reject a locale the schema will not store before
     * it reaches the database as an opaque constraint violation.
     */
    public static void validateLocales(List<ConsentAcceptance> submitted) {
        for (ConsentAcceptance entry : submitted == null ? List.<ConsentAcceptance>of() : submitted) {
            if (!"en".equals(entry.locale()) && !"ms".equals(entry.locale())) {
                throw ApiError.badRequest("Consent locale must be 'en' or 'ms'");
            }
        }
    }

    /**
     * {@code require_consents}: every required document must appear, be granted,
     * and pin the version the server currently publishes.
     */
    public static void requireConsents(List<ConsentAcceptance> submitted, List<Document> required) {
        List<ConsentAcceptance> safe = submitted == null ? List.of() : submitted;
        for (Document document : required) {
            ConsentAcceptance entry = safe.stream()
                    .filter(candidate -> document.wire().equals(candidate.document()))
                    .findFirst()
                    .orElseThrow(() -> ApiError.badRequest(
                            "Consent to the " + document.humanName()
                                    + " is required before this request can be accepted"));
            if (!entry.granted()) {
                throw ApiError.badRequest(
                        "Consent to the " + document.humanName()
                                + " is required before this request can be accepted");
            }
            if (!document.currentVersion().equals(entry.version())) {
                throw ApiError.badRequest(
                        "The " + document.humanName()
                                + " has been updated. Please review the current version and accept it again");
            }
        }
    }

    /**
     * {@code preferred_locale}: the language the guest read the notices in —
     * stored on guests.language_preference.
     */
    public static String preferredLocale(List<ConsentAcceptance> submitted) {
        if (submitted == null) {
            return "en";
        }
        return submitted.stream()
                .map(ConsentAcceptance::locale)
                .filter(locale -> "en".equals(locale) || "ms".equals(locale))
                .findFirst()
                .orElse("en");
    }

    /**
     * {@code record} / {@code record_tx}: persist every acceptance (grants AND
     * refusals) and note the batch in the audit trail. Joins the ambient JDBC
     * transaction when the caller is inside one.
     */
    public void record(Subject subject, List<ConsentAcceptance> acceptances,
            Source source, Context context) {
        if (acceptances == null || acceptances.isEmpty()) {
            return;
        }
        for (ConsentAcceptance acceptance : acceptances) {
            Document document = Document.fromWire(acceptance.document());
            if (document == null) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO consent_records
                        (subject_type, user_id, guest_id, booking_id, document_type,
                         document_version, locale, granted, source, ip_address, user_agent)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS inet), ?)
                    """,
                    subject.subjectType().wire(), subject.userId(), subject.guestId(),
                    subject.bookingId(), document.wire(), acceptance.version(),
                    acceptance.locale() == null ? "en" : acceptance.locale(),
                    acceptance.granted(), source.wire(), context.ipAddress(),
                    context.userAgent());
        }
        List<String> granted = acceptances.stream()
                .filter(ConsentAcceptance::granted).map(ConsentAcceptance::document).toList();
        List<String> refused = acceptances.stream()
                .filter(a -> !a.granted()).map(ConsentAcceptance::document).toList();
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("source", source.wire());
        details.put("subject_type", subject.subjectType().wire());
        details.put("booking_id", subject.bookingId());
        details.put("granted", granted);
        details.put("refused", refused);
        Long resourceId = subject.bookingId() != null ? subject.bookingId()
                : subject.guestId() != null ? subject.guestId() : subject.userId();
        audit.event(subject.userId(), "consent.recorded", "consent", resourceId,
                details, context.ipAddress(), context.userAgent());
    }
}
