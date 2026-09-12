package com.hotelapp.ekyc;

import com.hotelapp.consent.Consents;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.text.Sanitizer;
import com.hotelapp.ekyc.EkycModels.EkycStatusResponse;
import com.hotelapp.ekyc.EkycModels.EkycSubmissionRequest;
import com.hotelapp.portal.PortalModels.ConsentAcceptance;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Port of the guest-facing half of {@code modules/ekyc/service.rs}
 * ({@code get_ekyc_status}, {@code store_document_upload},
 * {@code submit_ekyc} for {@code SubmissionChannel::GuestPortal}) plus the
 * validation helpers they reach ({@code validate_existing_ekyc_path},
 * {@code sanitize_document_type}, {@code validate_image_bytes},
 * {@code build_ekyc_filename}, {@code validate_date_strings},
 * {@code validate_submission_field_lengths}, {@code status_response}).
 *
 * The portal channel never accepts inline base64 — only paths already written
 * by the upload route, each namespaced to the caller's {@code user_id}.
 */
@Service
public class EkycPortalService {

    /** Upstream EKYC_UPLOAD_DIR. */
    public static final String EKYC_UPLOAD_DIR = "private_uploads/ekyc";

    /** Upstream MAX_EKYC_IMAGE_BYTES. */
    public static final long MAX_EKYC_IMAGE_BYTES = 10L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final Consents consents;
    private final AuditWriter audit;
    private final ObjectMapper objectMapper;

    public EkycPortalService(JdbcTemplate jdbc, Consents consents,
            AuditWriter audit, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.consents = consents;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // Pure helpers (contract-tested)
    // ------------------------------------------------------------------

    /** {@code sanitize_document_type}: [A-Za-z0-9_-], capped at 40 chars. */
    public static String sanitizeDocumentType(String value) {
        StringBuilder out = new StringBuilder(40);
        for (char c : (value == null ? "" : value).toCharArray()) {
            if (out.length() >= 40) {
                break;
            }
            if (Character.isLetterOrDigit(c) && c < 128 || c == '_' || c == '-') {
                out.append(c);
            }
        }
        if (out.length() == 0) {
            throw ApiError.badRequest("Invalid document type");
        }
        return out.toString();
    }

    /** {@code image_extension}: magic-byte sniffing only. */
    public static String imageExtension(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "jpg";
        }
        byte[] pngMagic = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (bytes.length >= 8 && startsWith(bytes, pngMagic)) {
            return "png";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        return null;
    }

    /** {@code validate_image_bytes}: 1 byte..10MB and a recognised signature. */
    public static String validateImageBytes(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_EKYC_IMAGE_BYTES) {
            throw ApiError.badRequest("File size must be between 1 byte and 10MB");
        }
        String extension = imageExtension(bytes);
        if (extension == null) {
            throw ApiError.badRequest("Only JPEG, PNG, or WebP image files are allowed");
        }
        return extension;
    }

    /** {@code build_ekyc_filename}: {user_id}_{type}_{epoch}_{uuid}.{ext}. */
    public static String buildEkycFilename(long userId, String imageType, String extension) {
        return userId + "_" + sanitizeDocumentType(imageType) + "_"
                + Instant.now().getEpochSecond() + "_" + UUID.randomUUID() + "." + extension;
    }

    /**
     * {@code validate_existing_ekyc_path}: a portal submission may only name a
     * file this same user already stored under {@link #EKYC_UPLOAD_DIR}.
     */
    public static String validateExistingEkycPath(String path, long userId) {
        String prefix = EKYC_UPLOAD_DIR + "/";
        if (path == null || !path.startsWith(prefix)) {
            throw ApiError.badRequest("Invalid eKYC image reference");
        }
        String filename = path.substring(prefix.length());
        if (filename.contains("/") || filename.contains("\\")
                || !filename.startsWith(userId + "_")) {
            throw ApiError.badRequest("Invalid eKYC image reference");
        }
        if (!Files.exists(Path.of(EKYC_UPLOAD_DIR).resolve(filename))) {
            throw ApiError.badRequest("Referenced eKYC image does not exist");
        }
        return path;
    }

    /** {@code validate_date_strings}: expiry must be strictly after today. */
    public static LocalDate[] validateDateStrings(String dateOfBirth, String idExpiryDate,
            String idIssueDate, LocalDate today) {
        LocalDate dob = parseDate(dateOfBirth, "Invalid date of birth. Use YYYY-MM-DD");
        LocalDate expiry = parseDate(idExpiryDate, "Invalid ID expiry date. Use YYYY-MM-DD");
        LocalDate issue = idIssueDate == null ? null
                : parseDate(idIssueDate, "Invalid ID issue date. Use YYYY-MM-DD");
        if (!expiry.isAfter(today)) {
            throw ApiError.badRequest("ID expiry date must be in the future");
        }
        return new LocalDate[] {dob, expiry, issue};
    }

    /** {@code validate_submission_field_lengths} — caps mirror the column widths. */
    public static void validateSubmissionFieldLengths(EkycSubmissionRequest req) {
        String[][] checks = {
                {"full_name", req.fullName(), "255"},
                {"id_type", req.idType(), "80"},
                {"id_number", req.idNumber(), "255"},
                {"nationality", req.nationality(), "100"},
                {"phone", req.phone(), "50"},
                {"email", req.email(), "255"},
                {"id_issuing_country", req.idIssuingCountry(), "100"},
                {"current_address", req.currentAddress(), "2000"},
        };
        for (String[] check : checks) {
            String value = check[1];
            if (value == null) {
                continue;
            }
            long max = Long.parseLong(check[2]);
            if (value.codePointCount(0, value.length()) > max) {
                throw ApiError.badRequest(check[0] + " must be " + max + " characters or fewer");
            }
        }
        if (Sanitizer.sanitizeGuestName(req.fullName() == null ? "" : req.fullName()).isEmpty()) {
            throw ApiError.badRequest("full_name is required");
        }
        if (req.idNumber() == null || req.idNumber().trim().isEmpty()) {
            throw ApiError.badRequest("id_number is required");
        }
        String phone = req.phone();
        if (phone != null && !phone.trim().isEmpty()
                && Sanitizer.sanitizePhone(phone).isEmpty()) {
            throw ApiError.badRequest("phone must contain at least one digit");
        }
    }

    private static LocalDate parseDate(String value, String message) {
        try {
            return LocalDate.parse(value == null ? "" : value.trim());
        } catch (Exception e) {
            throw ApiError.badRequest(message);
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    private LocalDate hotelToday() {
        LocalDate today = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
        if (today == null) {
            throw ApiError.internal("Database clock unavailable");
        }
        return today;
    }

    /** {@code EkycRepository::user_type_and_guest_id}. */
    public Map<String, Object> userTypeAndGuestId(long userId) {
        return jdbc.queryForMap(
                "SELECT user_type::text AS user_type, guest_id FROM users WHERE id = ?", userId);
    }

    /** {@code EkycRepository::guest_id_for_user}. */
    public Long guestIdForUser(long userId) {
        List<Long> rows = jdbc.query("SELECT guest_id FROM users WHERE id = ?",
                (rs, i) -> rs.getLong(1), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code EkycRepository::exists_open_for_guest}. */
    public boolean existsOpenForGuest(long guestId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM ekyc_verifications
                    WHERE guest_id = ? AND status NOT IN
                        ('rejected', 'expired', 'void', 'additional_information_required'))
                """, Boolean.class, guestId));
    }

    /** {@code EkycRepository::supersede_information_requests}. */
    public void supersedeInformationRequests(long guestId) {
        jdbc.update("""
                UPDATE ekyc_verifications SET status = 'void', updated_at = CURRENT_TIMESTAMP
                WHERE guest_id = ? AND status = 'additional_information_required'
                """, guestId);
    }

    /** {@code find_by_guest} → {@code status_response} — latest row only. */
    public EkycStatusResponse statusForGuest(long guestId) {
        List<EkycStatusResponse> rows = jdbc.query("""
                SELECT id, status, self_checkin_enabled, submitted_at, verified_at,
                       full_name, id_type, id_expiry_date, customer_message
                FROM ekyc_verifications
                WHERE guest_id = ?
                ORDER BY submitted_at DESC, id DESC LIMIT 1
                """, (rs, i) -> new EkycStatusResponse(
                rs.getLong("id"), rs.getString("status"),
                rs.getObject("self_checkin_enabled") == null ? null
                        : rs.getBoolean("self_checkin_enabled"),
                rs.getTimestamp("submitted_at"), rs.getTimestamp("verified_at"),
                rs.getString("full_name"), rs.getString("id_type"),
                rs.getObject("id_expiry_date") == null ? null
                        : ((java.sql.Date) rs.getObject("id_expiry_date")).toLocalDate(),
                rs.getString("customer_message"), null), guestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * {@code get_ekyc_status} — null when the guest has never submitted. The
     * guest's account must resolve to a guest profile.
     */
    public EkycStatusResponse getStatus(long userId) {
        Long guestId = guestIdForUser(userId);
        if (guestId == null) {
            throw ApiError.badRequest("Your account is not linked to a guest profile");
        }
        return statusForGuest(guestId);
    }

    // ------------------------------------------------------------------
    // Upload
    // ------------------------------------------------------------------

    /**
     * {@code store_document_upload} — one image under EKYC_UPLOAD_DIR, filename
     * namespaced to the caller's user id. Returns the upstream JSON shape.
     */
    public Map<String, Object> storeDocumentUpload(long userId, String documentTypeRaw,
            String contentType, byte[] data) {
        String documentType = "document";
        if (documentTypeRaw != null) {
            documentType = sanitizeDocumentType(documentTypeRaw);
        }
        if (!List.of("image/jpeg", "image/jpg", "image/png", "image/webp")
                .contains(contentType == null ? "" : contentType)) {
            throw ApiError.badRequest("Only JPEG, PNG, or WebP image files are allowed");
        }
        String extension = validateImageBytes(data);
        String filename = buildEkycFilename(userId, documentType, extension);
        try {
            Files.createDirectories(Path.of(EKYC_UPLOAD_DIR));
            Files.write(Path.of(EKYC_UPLOAD_DIR).resolve(filename), data);
        } catch (IOException e) {
            throw ApiError.internal("Failed to write file: " + e.getMessage());
        }
        String filePath = EKYC_UPLOAD_DIR + "/" + filename;
        return Map.of("success", true, "file_path", filePath,
                "filename", filePath, "document_type", documentType);
    }

    // ------------------------------------------------------------------
    // Submit
    // ------------------------------------------------------------------

    /**
     * {@code submit_ekyc} for {@code SubmissionChannel::GuestPortal}: consent
     * first, one open verification per guest, image references must already be
     * on disk under this user's prefix.
     */
    public EkycStatusResponse submit(long userId, EkycSubmissionRequest req,
            String ipAddress, String userAgent) {
        Map<String, Object> row = userTypeAndGuestId(userId);
        String userType = (String) row.get("user_type");
        if (!"guest".equals(userType)) {
            throw ApiError.badRequest("Only guest users can submit eKYC verification");
        }
        Object guestIdRaw = row.get("guest_id");
        if (guestIdRaw == null) {
            throw ApiError.badRequest("Your account is not linked to a guest profile");
        }
        long guestId = ((Number) guestIdRaw).longValue();

        List<ConsentAcceptance> submittedConsents =
                req.consents() == null ? List.of() : req.consents();
        Consents.validateLocales(submittedConsents);
        Consents.requireConsents(submittedConsents, Consents.EKYC_REQUIRED);
        consents.record(Consents.Subject.guest(guestId).withUser(userId),
                submittedConsents, Consents.Source.EKYC,
                new Consents.Context(ipAddress, userAgent));

        if (existsOpenForGuest(guestId)) {
            throw ApiError.badRequest(
                    "You already have an active eKYC verification. Please check your status.");
        }
        supersedeInformationRequests(guestId);

        LocalDate[] dates = validateDateStrings(req.dateOfBirth(), req.idExpiryDate(),
                req.idIssueDate(), hotelToday());
        validateSubmissionFieldLengths(req);

        String fullName = Sanitizer.sanitizeGuestName(req.fullName());
        String nationality = req.nationality() == null ? null
                : Sanitizer.sanitizeText(req.nationality());
        String phone = req.phone() == null ? null : Sanitizer.sanitizePhone(req.phone());
        String email = req.email() == null ? null : Sanitizer.sanitizeEmail(req.email());
        String currentAddress = req.currentAddress() == null ? null
                : Sanitizer.sanitizeText(req.currentAddress());
        String idIssuingCountry = req.idIssuingCountry() == null ? null
                : Sanitizer.sanitizeText(req.idIssuingCountry());

        String idFrontPath = validateExistingEkycPath(req.idFrontImage(), userId);
        String idBackPath = req.idBackImage() == null ? null
                : validateExistingEkycPath(req.idBackImage(), userId);
        String selfiePath = validateExistingEkycPath(req.selfieImage(), userId);
        String proofPath = req.proofOfAddress() == null ? null
                : validateExistingEkycPath(req.proofOfAddress(), userId);

        Map<String, Object> userEntered = new java.util.LinkedHashMap<>();
        userEntered.put("full_name", fullName);
        userEntered.put("date_of_birth", dates[0]);
        userEntered.put("nationality", nationality);
        userEntered.put("phone", phone);
        userEntered.put("email", email);
        userEntered.put("current_address", currentAddress);
        userEntered.put("id_type", req.idType());
        userEntered.put("id_issuing_country", idIssuingCountry);
        userEntered.put("id_issue_date", dates[2]);
        userEntered.put("id_expiry_date", dates[1]);
        String userEnteredData = objectMapper.writeValueAsString(userEntered);
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("user_agent", userAgent);
        String metadata = objectMapper.writeValueAsString(meta);

        long verificationId = jdbc.queryForObject("""
                INSERT INTO ekyc_verifications (
                    user_id, guest_id, full_name, date_of_birth, nationality, phone, email,
                    current_address, id_type, id_number, id_issuing_country, id_issue_date,
                    id_expiry_date, id_front_image_path, id_back_image_path, selfie_image_path,
                    proof_of_address_path, status, provider_verification_result,
                    user_entered_data, ip_address, submission_metadata, face_match_passed,
                    liveness_passed, auto_verified, manual_review_required, risk_level,
                    risk_score, risk_flags, recommended_action, self_checkin_enabled,
                    submitted_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    'submitted', 'pending', CAST(? AS jsonb), ?, CAST(? AS jsonb),
                    false, false, false, true, 'medium', 35,
                    CAST('["manual_review_required"]' AS jsonb), 'manual_review', false,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class,
                userId, guestId, fullName, dates[0], nationality, phone, email,
                currentAddress, req.idType(), req.idNumber(), idIssuingCountry,
                dates[2], dates[1], idFrontPath, idBackPath, selfiePath, proofPath,
                userEnteredData, ipAddress, metadata);

        EkycStatusResponse response = statusForGuest(guestId);
        audit.event(userId, "ekyc_submitted", "ekyc_verification", verificationId,
                Map.of("status", "submitted", "channel", "guest_portal"),
                ipAddress, userAgent);
        return response;
    }
}
