package com.hotelapp.auth;

import com.hotelapp.auth.dto.AuthDtos.PasskeyInfo;
import com.hotelapp.auth.dto.AuthDtos.PasskeyLoginFinish;
import com.hotelapp.auth.dto.AuthDtos.PasskeyLoginStart;
import com.hotelapp.auth.dto.AuthDtos.PasskeyRegistrationFinish;
import com.hotelapp.auth.dto.AuthDtos.PasskeyRegistrationStart;
import com.hotelapp.auth.dto.AuthDtos.PasskeyUpdateInput;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.settings.HotelSettings;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Port of {@code services/passkey.rs}: WebAuthn registration and assertion
 * verification — client-data JSON checks, RP-ID hash, presence/verification
 * flags, ES256 signatures, signature counters, and one-time challenges.
 *
 * <p>Passkeys satisfy 2FA on their own, so registration is a step-up operation
 * (see {@link AuthService#ensureStepUp}) and assertion verification shares the
 * session-minting path with password and Google sign-in.
 */
@Component
public class PasskeyService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_PASSKEYS_PER_USER = 10;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final HotelSettings settings;
    private final AuthService authService;
    private final AuditWriter audit;
    private final AuthAudit authAudit;

    public PasskeyService(JdbcTemplate jdbc, ObjectMapper objectMapper,
            AppProperties properties, HotelSettings settings, AuthService authService,
            AuditWriter audit, AuthAudit authAudit) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.settings = settings;
        this.authService = authService;
        this.audit = audit;
        this.authAudit = authAudit;
    }

    // ---- profile management -------------------------------------------------

    /** {@code list_passkeys}: credential ids base64url-encoded for clients. */
    public List<PasskeyInfo> listPasskeys(long userId) {
        return jdbc.query("""
                SELECT id, credential_id, device_name, created_at, last_used_at
                FROM passkeys WHERE user_id = ? ORDER BY created_at DESC
                """, (rs, n) -> new PasskeyInfo(
                rs.getString("id"),
                base64urlEncode(rs.getBytes("credential_id")),
                rs.getString("device_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_used_at") == null
                        ? null : rs.getTimestamp("last_used_at").toInstant()),
                userId);
    }

    public void deletePasskey(long userId, String passkeyId) {
        int removed = jdbc.update("DELETE FROM passkeys WHERE id = ?::uuid AND user_id = ?",
                passkeyId, userId);
        if (removed == 0) {
            throw ApiError.notFound("Passkey not found");
        }
    }

    public void updatePasskey(long userId, String passkeyId, PasskeyUpdateInput input) {
        int updated = jdbc.update(
                "UPDATE passkeys SET device_name = ? WHERE id = ?::uuid AND user_id = ?",
                input.deviceName(), passkeyId, userId);
        if (updated == 0) {
            throw ApiError.notFound("Passkey not found");
        }
    }

    // ---- registration --------------------------------------------------------

    /**
     * {@code register_start}: same-user guard, step-up re-authentication, cap
     * check, challenge mint, then the creation options the client feeds to
     * {@code navigator.credentials.create}.
     */
    public Map<String, Object> registerStart(long authenticatedUserId,
            PasskeyRegistrationStart req) {
        Map<String, Object> user = findActiveUserByIdAndUsername(authenticatedUserId,
                req.username());
        if (user == null) {
            throw ApiError.forbidden("Cannot register a passkey for another user");
        }
        long userId = ((Number) user.get("id")).longValue();

        // A passkey satisfies 2FA on its own, so minting one must cost more
        // than an already-open session.
        authService.ensureStepUp(userId, req.password(), req.totpCode());

        long count = passkeyCount(userId);
        if (count >= MAX_PASSKEYS_PER_USER) {
            throw ApiError.badRequest("Maximum of 10 passkeys allowed per user");
        }

        byte[] challengeBytes = new byte[32];
        RANDOM.nextBytes(challengeBytes);
        String challengeB64 = Base64.getEncoder().encodeToString(challengeBytes);

        jdbc.update("""
                INSERT INTO passkey_challenges (user_id, challenge, challenge_type, expires_at)
                VALUES (?, ?, 'registration', ?)
                """, userId, challengeBytes, Timestamp.from(Instant.now().plusSeconds(300)));

        String rpName = settings.getHotelDisplayName("passkey_relying_party_name");
        String rpId = rpId();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("challenge", challengeB64);
        body.put("rp", Map.of("name", rpName, "id", rpId));
        body.put("user", Map.of(
                "id", Base64.getEncoder().encodeToString(
                        String.valueOf(userId).getBytes(StandardCharsets.UTF_8)),
                "name", user.get("username"),
                "displayName", user.get("full_name") != null
                        ? user.get("full_name") : user.get("username")));
        return body;
    }

    /**
     * {@code register_finish}: verify the challenge exists, then the attestation
     * — clientDataJSON type/challenge/origin, authData RP hash + flags, COSE
     * ES256 public key — and store the credential.
     */
    public void registerFinish(long authenticatedUserId, PasskeyRegistrationFinish req) {
        Map<String, Object> user = findActiveUserByIdAndUsername(authenticatedUserId,
                req.username());
        if (user == null) {
            throw ApiError.forbidden("Cannot register a passkey for another user");
        }
        long userId = ((Number) user.get("id")).longValue();

        byte[] expectedChallenge = decodeStandardB64(req.challenge(), "challenge");
        if (!challengeExists(userId, expectedChallenge, "registration")) {
            throw ApiError.unauthorized("Invalid or expired challenge");
        }

        JsonNode credential;
        try {
            credential = objectMapper.readTree(req.credential());
        } catch (Exception e) {
            throw ApiError.badRequest("Invalid credential format");
        }
        String credentialIdStr = credential.path("id").asText(null);
        if (credentialIdStr == null) {
            throw ApiError.badRequest("Missing credential ID");
        }
        byte[] credentialIdFromJson;
        try {
            credentialIdFromJson = decodeBase64url(credentialIdStr);
        } catch (IllegalArgumentException e) {
            throw ApiError.badRequest("Invalid credential ID format: " + e.getMessage());
        }

        JsonNode response = credential.get("response");
        if (response == null) {
            throw ApiError.badRequest("Missing credential response");
        }
        byte[] clientDataJson = jsonByteArray(response.get("clientDataJSON"), "clientDataJSON");
        byte[] attestationObject =
                jsonByteArray(response.get("attestationObject"), "attestationObject");

        String rpId = rpId();
        verifyClientData(clientDataJson, "webauthn.create", expectedChallenge, rpId);
        byte[] authData = extractAuthDataFromAttestation(attestationObject);
        CredentialParts parsed = parseAttestedCredential(authData, rpId);
        if (!MessageDigest.isEqual(parsed.credentialId(), credentialIdFromJson)) {
            throw ApiError.badRequest("Credential ID does not match attestation data");
        }

        String deviceName = req.deviceName() != null ? req.deviceName()
                : "Passkey " + java.time.LocalDate.now().toString();
        jdbc.update("""
                INSERT INTO passkeys (user_id, credential_id, public_key, counter, device_name)
                VALUES (?, ?, ?, ?, ?)
                """, userId, parsed.credentialId(), parsed.publicKey(), parsed.counter(),
                deviceName);
        markChallengeUsed(userId, expectedChallenge);

        audit.event(userId, "passkey_registered", "user", userId,
                Map.of("device_name", deviceName), null, null);
    }

    // ---- login ---------------------------------------------------------------

    /** {@code login_start}: challenge + allowCredentials for the username. */
    public Map<String, Object> loginStart(PasskeyLoginStart req) {
        Map<String, Object> user = findActiveUserByUsername(req.username());
        if (user == null) {
            throw ApiError.notFound("User not found");
        }
        long userId = ((Number) user.get("id")).longValue();

        List<Map<String, Object>> passkeys = activePasskeys(userId);
        if (passkeys.isEmpty()) {
            throw ApiError.notFound("No passkeys found for this user");
        }

        byte[] challengeBytes = new byte[32];
        RANDOM.nextBytes(challengeBytes);
        String challengeB64 = Base64.getEncoder().encodeToString(challengeBytes);
        jdbc.update("""
                INSERT INTO passkey_challenges (user_id, challenge, challenge_type, expires_at)
                VALUES (?, ?, 'authentication', ?)
                """, userId, challengeBytes, Timestamp.from(Instant.now().plusSeconds(300)));

        List<Map<String, Object>> allowCredentials = new ArrayList<>();
        for (Map<String, Object> passkey : passkeys) {
            allowCredentials.add(Map.of(
                    "id", base64urlEncode((byte[]) passkey.get("credential_id")),
                    "type", "public-key"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("challenge", challengeB64);
        body.put("allowCredentials", allowCredentials);
        return body;
    }

    /**
     * {@code login_finish}: assertion verification, sign-counter advance, then
     * the shared session-minting path used by password and Google sign-in.
     */
    public AuthService.LoginResult loginFinish(PasskeyLoginFinish req, String ip,
            String userAgent, String clientTimezone) {
        Map<String, Object> user = findActiveUserByUsername(req.username());
        if (user == null) {
            throw ApiError.notFound("User not found");
        }
        long userId = ((Number) user.get("id")).longValue();

        // Account lockout applies to every login door, not just the password one.
        authService.ensureNotLocked(userId, req.username(), ip, userAgent);

        byte[] expectedChallenge = decodeStandardB64(req.challenge(), "challenge");
        if (!challengeExists(userId, expectedChallenge, "authentication")) {
            authAudit.loginFailure(req.username(), "Invalid or expired passkey challenge",
                    ip, userAgent);
            throw ApiError.unauthorized("Invalid or expired challenge");
        }

        byte[] credentialIdBytes;
        try {
            credentialIdBytes = decodeBase64url(req.credentialId());
        } catch (IllegalArgumentException e) {
            throw ApiError.badRequest("Invalid credential ID format: " + e.getMessage());
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, user_id, credential_id, public_key, counter, device_name,
                       created_at, last_used_at
                FROM passkeys
                WHERE user_id = ? AND credential_id = ? AND is_active = true
                """, userId, credentialIdBytes);
        if (rows.isEmpty()) {
            authAudit.loginFailure(req.username(), "Unknown passkey credential", ip, userAgent);
            throw ApiError.unauthorized("Invalid passkey");
        }
        Map<String, Object> passkey = rows.get(0);

        byte[] clientDataJson = decodeStandardB64(req.clientDataJson(), "clientDataJSON");
        byte[] authenticatorData = decodeStandardB64(req.authenticatorData(), "authenticatorData");
        byte[] signatureBytes = decodeStandardB64(req.signature(), "signature");
        String rpId = rpId();
        byte[] clientDataHash = verifyClientData(clientDataJson, "webauthn.get",
                expectedChallenge, rpId);
        long counter = validateAuthDataPrefix(authenticatorData, rpId, false);

        long storedCounter = passkey.get("counter") == null
                ? 0 : ((Number) passkey.get("counter")).longValue();
        if (storedCounter > 0 && counter <= storedCounter) {
            throw ApiError.unauthorized("Passkey sign counter did not advance");
        }

        byte[] signedData = new byte[authenticatorData.length + clientDataHash.length];
        System.arraycopy(authenticatorData, 0, signedData, 0, authenticatorData.length);
        System.arraycopy(clientDataHash, 0, signedData, authenticatorData.length,
                clientDataHash.length);
        verifyEs256Signature((byte[]) passkey.get("public_key"), signedData, signatureBytes);

        jdbc.update(
                "UPDATE passkeys SET last_used_at = CURRENT_TIMESTAMP, counter = ? WHERE id = ?::uuid",
                counter, passkey.get("id"));
        markChallengeUsed(userId, expectedChallenge);

        authAudit.loginSuccess(userId, "passkey", ip, userAgent);

        return authService.issueAuthenticatedResponse(user, ip, userAgent, clientTimezone);
    }

    // ---- repository helpers ----------------------------------------------------

    public long passkeyCount(long userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM passkeys WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    private Map<String, Object> findActiveUserByIdAndUsername(long userId, String username) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, username, email, full_name, phone, is_active, is_verified, user_type,
                       two_factor_enabled, two_factor_secret, two_factor_recovery_codes,
                       created_at, updated_at
                FROM users
                WHERE id = ? AND username = ? AND is_active = true AND deleted_at IS NULL
                """, userId, username);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findActiveUserByUsername(String username) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, username, email, full_name, phone, is_active, is_verified, user_type,
                       two_factor_enabled, two_factor_secret, two_factor_recovery_codes,
                       created_at, updated_at
                FROM users
                WHERE username = ? AND is_active = true AND deleted_at IS NULL
                """, username);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean challengeExists(long userId, byte[] challenge, String type) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM passkey_challenges
                              WHERE user_id = ? AND challenge = ? AND challenge_type = ?
                                AND expires_at > CURRENT_TIMESTAMP AND used_at IS NULL)
                """, Boolean.class, userId, challenge, type));
    }

    private void markChallengeUsed(long userId, byte[] challenge) {
        jdbc.update("""
                UPDATE passkey_challenges SET used_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND challenge = ?
                """, userId, challenge);
    }

    private List<Map<String, Object>> activePasskeys(long userId) {
        return jdbc.queryForList("""
                SELECT id, user_id, credential_id, public_key, counter, device_name,
                       created_at, last_used_at
                FROM passkeys WHERE user_id = ? AND is_active = true
                """, userId);
    }

    /** {@code revoke_all_for_user}: password resets deactivate every passkey. */
    public int revokeAllForUser(long userId) {
        return jdbc.update(
                "UPDATE passkeys SET is_active = false WHERE user_id = ? AND is_active = true",
                userId);
    }

    private String rpId() {
        String configured = properties.getPasskeyRpId();
        return configured == null || configured.isBlank() ? "localhost" : configured;
    }

    // ---- WebAuthn decoding + verification -------------------------------------

    static byte[] decodeBase64url(String input) {
        String standard = input.replace('-', '+').replace('_', '/');
        int pad = (4 - standard.length() % 4) % 4;
        return Base64.getDecoder().decode(standard + "=".repeat(pad));
    }

    private static byte[] decodeStandardB64(String input, String label) {
        try {
            return Base64.getDecoder().decode(input);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiError.badRequest("Invalid " + label + " encoding");
        }
    }

    static String base64urlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] jsonByteArray(JsonNode value, String label) {
        if (value == null || !value.isArray()) {
            throw ApiError.badRequest("Missing " + label);
        }
        byte[] out = new byte[value.size()];
        for (int i = 0; i < value.size(); i++) {
            JsonNode element = value.get(i);
            if (!element.isIntegralNumber()) {
                throw ApiError.badRequest("Invalid " + label);
            }
            int b = element.intValue();
            if (b < 0 || b > 255) {
                throw ApiError.badRequest("Invalid " + label);
            }
            out[i] = (byte) b;
        }
        return out;
    }

    private static String originHost(String origin) {
        int schemeEnd = origin.indexOf("://");
        if (schemeEnd < 0) {
            return null;
        }
        String rest = origin.substring(schemeEnd + 3);
        int slash = rest.indexOf('/');
        String authority = slash >= 0 ? rest.substring(0, slash) : rest;
        int at = authority.lastIndexOf('@');
        String hostPort = at >= 0 ? authority.substring(at + 1) : authority;
        int colon = hostPort.indexOf(':');
        String host = colon >= 0 ? hostPort.substring(0, colon) : hostPort;
        return host.isEmpty() ? null : host;
    }

    static boolean originAllowed(String origin, String rpId) {
        String host = originHost(origin);
        if (host == null) {
            return false;
        }
        return host.equals(rpId)
                || host.endsWith("." + rpId)
                || ("localhost".equals(rpId) && (host.equals("127.0.0.1") || host.equals("::1")));
    }

    /**
     * {@code verify_client_data}: parse the JSON, check type + challenge +
     * origin + crossOrigin, and return the SHA-256 of the raw bytes (the
     * signature input's second half).
     */
    private byte[] verifyClientData(byte[] clientData, String expectedType,
            byte[] expectedChallenge, String rpId) {
        JsonNode value;
        try {
            value = objectMapper.readTree(clientData);
        } catch (Exception e) {
            throw ApiError.badRequest("Invalid WebAuthn client data");
        }
        String actualType = value.path("type").asText(null);
        if (actualType == null) {
            throw ApiError.badRequest("Missing WebAuthn client data type");
        }
        if (!expectedType.equals(actualType)) {
            throw ApiError.unauthorized("Invalid WebAuthn client data type");
        }

        String challenge = value.path("challenge").asText(null);
        if (challenge == null) {
            throw ApiError.badRequest("Missing WebAuthn challenge");
        }
        byte[] challengeBytes;
        try {
            challengeBytes = decodeBase64url(challenge);
        } catch (IllegalArgumentException e) {
            throw ApiError.badRequest("Invalid WebAuthn challenge");
        }
        if (!MessageDigest.isEqual(challengeBytes, expectedChallenge)) {
            throw ApiError.unauthorized("WebAuthn challenge mismatch");
        }

        String origin = value.path("origin").asText(null);
        if (origin == null) {
            throw ApiError.badRequest("Missing WebAuthn origin");
        }
        if (!originAllowed(origin, rpId)) {
            throw ApiError.unauthorized("WebAuthn origin is not allowed");
        }

        if (value.path("crossOrigin").asBoolean(false)) {
            throw ApiError.unauthorized("Cross-origin WebAuthn assertions are not allowed");
        }

        try {
            return MessageDigest.getInstance("SHA-256").digest(clientData);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record CredentialParts(byte[] credentialId, byte[] publicKey, long counter) {
    }

    /** Minimal definite-length CBOR reader for the attestation envelope. */
    private static final class CborReader {
        private final byte[] data;
        private int pos;

        CborReader(byte[] data) {
            this.data = data;
        }

        int readU8() {
            if (pos >= data.length) {
                throw ApiError.badRequest("Truncated CBOR data");
            }
            return data[pos++] & 0xFF;
        }

        byte[] readExact(int len) {
            if (len < 0 || pos + len > data.length) {
                throw ApiError.badRequest(len < 0 ? "Invalid CBOR length" : "Truncated CBOR data");
            }
            byte[] out = new byte[len];
            System.arraycopy(data, pos, out, 0, len);
            pos += len;
            return out;
        }

        long[] readTypeLen() {
            int first = readU8();
            int major = first >> 5;
            int additional = first & 0x1F;
            long len;
            if (additional <= 23) {
                len = additional;
            } else if (additional == 24) {
                len = readU8();
            } else if (additional == 25) {
                byte[] b = readExact(2);
                len = ((b[0] & 0xFFL) << 8) | (b[1] & 0xFFL);
            } else if (additional == 26) {
                byte[] b = readExact(4);
                len = ((b[0] & 0xFFL) << 24) | ((b[1] & 0xFFL) << 16)
                        | ((b[2] & 0xFFL) << 8) | (b[3] & 0xFFL);
            } else if (additional == 27) {
                byte[] b = readExact(8);
                len = 0;
                for (byte x : b) {
                    len = (len << 8) | (x & 0xFFL);
                }
            } else {
                throw ApiError.badRequest("Indefinite CBOR is not supported");
            }
            return new long[] {major, len};
        }

        int readMapLen() {
            long[] tl = readTypeLen();
            if (tl[0] != 5) {
                throw ApiError.badRequest("Expected CBOR map");
            }
            if (tl[1] > Integer.MAX_VALUE) {
                throw ApiError.badRequest("CBOR map too large");
            }
            return (int) tl[1];
        }

        String readText() {
            long[] tl = readTypeLen();
            if (tl[0] != 3) {
                throw ApiError.badRequest("Expected CBOR text");
            }
            if (tl[1] > Integer.MAX_VALUE) {
                throw ApiError.badRequest("CBOR text too large");
            }
            try {
                return new String(readExact((int) tl[1]), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw ApiError.badRequest("Invalid CBOR text");
            }
        }

        byte[] readBytes() {
            long[] tl = readTypeLen();
            if (tl[0] != 2) {
                throw ApiError.badRequest("Expected CBOR bytes");
            }
            if (tl[1] > Integer.MAX_VALUE) {
                throw ApiError.badRequest("CBOR bytes too large");
            }
            return readExact((int) tl[1]);
        }

        long readInt() {
            long[] tl = readTypeLen();
            if (tl[0] == 0) {
                return tl[1];
            }
            if (tl[0] == 1) {
                return -1 - tl[1];
            }
            throw ApiError.badRequest("Expected CBOR integer");
        }

        void skip() {
            long[] tl = readTypeLen();
            int major = (int) tl[0];
            long len = tl[1];
            if (major == 0 || major == 1 || major == 7) {
                return;
            }
            if (major == 2 || major == 3) {
                if (len > Integer.MAX_VALUE) {
                    throw ApiError.badRequest("CBOR value too large");
                }
                readExact((int) len);
                return;
            }
            if (major == 4) {
                for (long i = 0; i < len; i++) {
                    skip();
                }
                return;
            }
            if (major == 5) {
                for (long i = 0; i < len; i++) {
                    skip();
                    skip();
                }
                return;
            }
            if (major == 6) {
                skip();
                return;
            }
            throw ApiError.badRequest("Unsupported CBOR value");
        }
    }

    /** {@code extract_auth_data_from_attestation}: the `authData` map entry. */
    private static byte[] extractAuthDataFromAttestation(byte[] attestationObject) {
        CborReader reader = new CborReader(attestationObject);
        int entries = reader.readMapLen();
        byte[] authData = null;
        for (int i = 0; i < entries; i++) {
            String key = reader.readText();
            if ("authData".equals(key)) {
                authData = reader.readBytes();
            } else {
                reader.skip();
            }
        }
        if (authData == null) {
            throw ApiError.badRequest("Missing WebAuthn authData");
        }
        return authData;
    }

    /**
     * {@code parse_cose_es256_public_key}: COSE key map → uncompressed-point
     * bytes {@code 0x04 || X || Y}, requiring kty=EC2, alg=ES256, crv=P-256.
     */
    private static byte[] parseCoseEs256PublicKey(byte[] coseKey) {
        CborReader reader = new CborReader(coseKey);
        int entries = reader.readMapLen();
        Long kty = null;
        Long alg = null;
        Long crv = null;
        byte[] x = null;
        byte[] y = null;
        for (int i = 0; i < entries; i++) {
            long key = reader.readInt();
            if (key == 1) {
                kty = reader.readInt();
            } else if (key == 3) {
                alg = reader.readInt();
            } else if (key == -1) {
                crv = reader.readInt();
            } else if (key == -2) {
                x = reader.readBytes();
            } else if (key == -3) {
                y = reader.readBytes();
            } else {
                reader.skip();
            }
        }
        if (kty == null || kty != 2 || alg == null || alg != -7 || crv == null || crv != 1) {
            throw ApiError.badRequest("Only ES256 passkeys are supported");
        }
        if (x == null || y == null) {
            throw ApiError.badRequest(x == null
                    ? "Missing passkey public key x" : "Missing passkey public key y");
        }
        if (x.length != 32 || y.length != 32) {
            throw ApiError.badRequest("Invalid passkey public key length");
        }
        byte[] publicKey = new byte[65];
        publicKey[0] = 0x04;
        System.arraycopy(x, 0, publicKey, 1, 32);
        System.arraycopy(y, 0, publicKey, 33, 32);
        return publicKey;
    }

    /**
     * {@code validate_auth_data_prefix}: RP-ID hash, UP+UV flags, optional
     * attested-credential flag; returns the 4-byte signature counter.
     */
    private static long validateAuthDataPrefix(byte[] authData, String rpId,
            boolean requireAttested) {
        if (authData.length < 37) {
            throw ApiError.badRequest("Invalid WebAuthn authenticator data");
        }
        byte[] expectedRpHash;
        try {
            expectedRpHash = MessageDigest.getInstance("SHA-256")
                    .digest(rpId.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        for (int i = 0; i < 32; i++) {
            if (authData[i] != expectedRpHash[i]) {
                throw ApiError.unauthorized("WebAuthn RP ID hash mismatch");
            }
        }
        int flags = authData[32] & 0xFF;
        if ((flags & 0x01) == 0 || (flags & 0x04) == 0) {
            throw ApiError.unauthorized(
                    "Passkey user presence and verification are required");
        }
        if (requireAttested && (flags & 0x40) == 0) {
            throw ApiError.badRequest("Missing passkey attested credential data");
        }
        return ((authData[33] & 0xFFL) << 24) | ((authData[34] & 0xFFL) << 16)
                | ((authData[35] & 0xFFL) << 8) | (authData[36] & 0xFFL);
    }

    /**
     * {@code parse_attested_credential}: flags/counter, then AAGUID (16B),
     * credential-id length + id, then the COSE key.
     */
    private static CredentialParts parseAttestedCredential(byte[] authData, String rpId) {
        long counter = validateAuthDataPrefix(authData, rpId, true);
        int pos = 37 + 16;
        if (authData.length < pos + 2) {
            throw ApiError.badRequest("Invalid passkey credential data");
        }
        int credentialLen = ((authData[pos] & 0xFF) << 8) | (authData[pos + 1] & 0xFF);
        pos += 2;
        int credentialEnd = pos + credentialLen;
        if (credentialLen == 0 || authData.length <= credentialEnd) {
            throw ApiError.badRequest("Invalid passkey credential data");
        }
        byte[] credentialId = new byte[credentialLen];
        System.arraycopy(authData, pos, credentialId, 0, credentialLen);
        byte[] publicKey = parseCoseEs256PublicKey(
                java.util.Arrays.copyOfRange(authData, credentialEnd, authData.length));
        return new CredentialParts(credentialId, publicKey, counter);
    }

    /** ES256 verify: stored key is `0x04||X||Y`; signature is ASN.1 DER. */
    private static void verifyEs256Signature(byte[] publicKeyBytes, byte[] data,
            byte[] signature) {
        try {
            byte[] x = new byte[32];
            byte[] y = new byte[32];
            System.arraycopy(publicKeyBytes, 1, x, 0, 32);
            System.arraycopy(publicKeyBytes, 33, y, 0, 32);
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);
            ECPublicKey key = (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(
                            new ECPoint(new BigInteger(1, x), new BigInteger(1, y)), ecSpec));
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(data);
            if (!verifier.verify(signature)) {
                throw ApiError.unauthorized("Invalid passkey signature");
            }
        } catch (ApiError e) {
            throw e;
        } catch (Exception e) {
            throw ApiError.unauthorized("Invalid passkey signature");
        }
    }
}
