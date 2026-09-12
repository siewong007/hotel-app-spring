package com.hotelapp.auth;

import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.text.Sanitizer;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Port of {@code services/google_identity.rs}: verifies a Google ID token
 * against Google's rotating RSA JWKS before its claims reach account
 * resolution.
 *
 * <p>The JWKS is cached for one hour; an unknown {@code kid} triggers a single
 * bounded forced refresh (60s cooldown, one refresh per cached generation) so
 * junk credentials cannot amplify into outbound requests.
 */
@Component
public class GoogleIdentityService {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdentityService.class);

    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final long JWKS_CACHE_TTL_SECS = 3_600;
    private static final long UNKNOWN_KID_COOLDOWN_SECS = 60;
    private static final List<String> GOOGLE_ISSUERS =
            List.of("accounts.google.com", "https://accounts.google.com");

    /** A verified Google identity claim suitable for guest-account resolution. */
    public record Identity(
            String subject, String email, String givenName, String familyName) {
    }

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile CachedJwks cached;
    private volatile Instant unknownKidCooldownUntil = Instant.EPOCH;
    private volatile long refreshInFlightGeneration = -1;
    private volatile long generation = 0;

    private record CachedJwks(JsonNode jwks, Instant expiresAt) {
    }

    public GoogleIdentityService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private static ApiError invalid() {
        return ApiError.unauthorized("Invalid Google credential.");
    }

    private static ApiError unavailable() {
        return ApiError.serviceUnavailable("Google sign-in is temporarily unavailable.");
    }

    /**
     * {@code verify_id_token}: decode header → pick JWKS (with one bounded
     * unknown-kid refresh) → RS256 signature → claim validation.
     */
    public Identity verifyIdToken(String credential) {
        String clientId = properties.getGoogleClientId();
        if (clientId == null || clientId.trim().isEmpty()) {
            throw ApiError.serviceUnavailable("Google sign-in is not configured.");
        }
        clientId = clientId.trim();

        String[] segments = credential == null ? new String[0] : credential.split("\\.");
        if (segments.length != 3) {
            throw invalid();
        }
        JsonNode header;
        try {
            header = objectMapper.readTree(
                    new String(Base64.getUrlDecoder().decode(segments[0]), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw invalid();
        }
        if (!"RS256".equals(header.path("alg").asText(null))) {
            throw invalid();
        }
        String kid = header.path("kid").asText(null);
        if (kid == null || kid.isEmpty()) {
            throw invalid();
        }

        JsonNode jwks = googleJwks();
        if (findKey(jwks, kid) == null) {
            jwks = refreshForUnknownKid(kid, jwks);
        }
        verifyWithJwks(credential, kid, jwks);

        JsonNode claims;
        try {
            claims = objectMapper.readTree(
                    new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw invalid();
        }
        return validateClaims(claims, clientId);
    }

    // ---- JWKS cache + bounded unknown-kid refresh --------------------------

    private JsonNode googleJwks() {
        CachedJwks current = cached;
        if (current != null && current.expiresAt().isAfter(Instant.now())) {
            return current.jwks();
        }
        JsonNode fresh = fetchJwks();
        lock.lock();
        try {
            generation++;
            refreshInFlightGeneration = -1;
            cached = new CachedJwks(fresh, Instant.now().plusSeconds(JWKS_CACHE_TTL_SECS));
        } finally {
            lock.unlock();
        }
        return fresh;
    }

    private JsonNode refreshForUnknownKid(String kid, JsonNode stale) {
        Instant now = Instant.now();
        long myGeneration;
        lock.lock();
        try {
            CachedJwks current = cached;
            if (current == null || current.expiresAt().isBefore(now)) {
                return googleJwks();
            }
            if (refreshInFlightGeneration == generation
                    || now.isBefore(unknownKidCooldownUntil)) {
                return stale;
            }
            refreshInFlightGeneration = generation;
            myGeneration = generation;
            unknownKidCooldownUntil = now.plusSeconds(UNKNOWN_KID_COOLDOWN_SECS);
        } finally {
            lock.unlock();
        }

        JsonNode fresh;
        try {
            fresh = fetchJwks();
        } catch (ApiError e) {
            lock.lock();
            try {
                if (generation == myGeneration) {
                    refreshInFlightGeneration = -1;
                }
            } finally {
                lock.unlock();
            }
            throw e;
        }
        lock.lock();
        try {
            if (generation == myGeneration) {
                refreshInFlightGeneration = -1;
                cached = new CachedJwks(fresh, Instant.now().plusSeconds(JWKS_CACHE_TTL_SECS));
            }
        } finally {
            lock.unlock();
        }
        return fresh;
    }

    private JsonNode fetchJwks() {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(GOOGLE_JWKS_URL))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable();
            }
            return objectMapper.readTree(response.body());
        } catch (ApiError e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google JWKS fetch failed: {}", e.getMessage());
            throw unavailable();
        }
    }

    // ---- signature + claim validation ----------------------------------------

    private JsonNode findKey(JsonNode jwks, String kid) {
        for (JsonNode key : jwks.path("keys")) {
            if (kid.equals(key.path("kid").asText(null))) {
                return key;
            }
        }
        return null;
    }

    private void verifyWithJwks(String credential, String kid, JsonNode jwks) {
        JsonNode key = findKey(jwks, kid);
        if (key == null
                || !"RSA".equals(key.path("kty").asText(null))
                || !"RS256".equals(key.path("alg").asText(null))
                || !"sig".equals(key.path("use").asText(null))) {
            throw invalid();
        }
        try {
            BigInteger modulus = new BigInteger(1,
                    Base64.getUrlDecoder().decode(key.path("n").asText()));
            BigInteger exponent = new BigInteger(1,
                    Base64.getUrlDecoder().decode(key.path("e").asText()));
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, exponent));
            String[] segments = credential.split("\\.");
            byte[] signed = (segments[0] + "." + segments[1]).getBytes(StandardCharsets.UTF_8);
            byte[] signature = Base64.getUrlDecoder().decode(segments[2]);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signed);
            if (!verifier.verify(signature)) {
                throw invalid();
            }
        } catch (ApiError e) {
            throw e;
        } catch (Exception e) {
            throw invalid();
        }
    }

    /** {@code validate_claims}: issuer, audience, expiry, verified email, subject. */
    private Identity validateClaims(JsonNode claims, String clientId) {
        String issuer = claims.path("iss").asText(null);
        boolean audienceOk = claims.path("aud").isTextual()
                ? clientId.equals(claims.path("aud").asText())
                : claims.path("aud").isArray()
                        && containsText(claims.path("aud"), clientId);
        long expiry = claims.path("exp").asLong(0);
        String email = Sanitizer.sanitizeEmail(claims.path("email").asText(""));
        boolean emailVerified = claims.path("email_verified").asBoolean(false);
        String subject = claims.path("sub").asText(null);

        if (!GOOGLE_ISSUERS.contains(issuer)
                || !audienceOk
                || expiry <= Instant.now().getEpochSecond()
                || !emailVerified
                || email.isEmpty()
                || !email.contains("@")
                || subject == null
                || subject.trim().isEmpty()
                || !subject.equals(subject.trim())
                || subject.length() > 255) {
            throw invalid();
        }

        return new Identity(subject, email,
                cleanName(claims.path("given_name").asText(null)),
                cleanName(claims.path("family_name").asText(null)));
    }

    private static boolean containsText(JsonNode array, String wanted) {
        for (JsonNode item : array) {
            if (wanted.equals(item.asText(null))) {
                return true;
            }
        }
        return false;
    }

    private static String cleanName(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = Sanitizer.sanitizeGuestName(value);
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }

    // ---- naming helpers ----------------------------------------------------

    /** {@code google_identity_fingerprint}: 16-hex deterministic per identity. */
    public static String fingerprint(String email, String subject) {
        // Upstream hashes (email, subject) with SipHash-1-3. The value never
        // leaves the server, so the port uses SHA-256's first 16 hex chars —
        // deterministic and collision-tight without a hand-rolled SipHash.
        return AuthService.sha256Hex(email + "\u0000" + subject).substring(0, 16);
    }

    /**
     * {@code google_username}: lowercase, database-safe, deterministic per
     * identity — `{local-part}_{fingerprint}`, ≤100 chars.
     */
    public static String usernameFor(String email, String subject) {
        String localPart = email.contains("@")
                ? email.substring(0, email.indexOf('@')) : email;
        StringBuilder normalized = new StringBuilder(localPart.length());
        for (char c : localPart.toCharArray()) {
            normalized.append(Character.isLetterOrDigit(c)
                    && c < 128 ? Character.toLowerCase(c) : '_');
        }
        String base = normalized.toString().replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "guest";
        }
        String suffix = fingerprint(email, subject);
        int baseLimit = 100 - suffix.length() - 1;
        if (base.length() > baseLimit) {
            base = base.substring(0, baseLimit);
        }
        return base + "_" + suffix;
    }

    /**
     * {@code google_display_name}: "Given Family", or "Google guest" when the
     * token carried no usable names. ≤255 chars.
     */
    public static String displayName(Identity identity) {
        String joined = java.util.stream.Stream.of(identity.givenName(), identity.familyName())
                .filter(java.util.Objects::nonNull)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        String fullName = joined.isEmpty() ? "Google guest" : joined;
        return fullName.length() > 255 ? fullName.substring(0, 255) : fullName;
    }

    /**
     * {@code profile_completion}: the contact fields a guest still owes —
     * first_name, last_name, phone.
     */
    public record ProfileCompletion(boolean complete, List<String> missingFields) {
    }

    public static ProfileCompletion profileCompletion(
            String firstName, String lastName, String phone) {
        List<String> missing = new java.util.ArrayList<>();
        if (firstName == null || firstName.trim().isEmpty()) {
            missing.add("first_name");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            missing.add("last_name");
        }
        if (phone == null || phone.trim().isEmpty()) {
            missing.add("phone");
        }
        return new ProfileCompletion(missing.isEmpty(), missing);
    }
}
