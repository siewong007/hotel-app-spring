package com.hotelapp.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hotelapp.core.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-32ch";

    private AppProperties props(boolean desktop) {
        AppProperties p = new AppProperties();
        p.setJwtSecret(SECRET);
        p.setDesktopMode(desktop);
        return p;
    }

    @Test
    void roundTripsEveryClaim() {
        JwtService service = new JwtService(props(false));
        long before = Instant.now().getEpochSecond();
        String token = service.issueAccessToken(42L, "alice", List.of("admin", "staff"), "sess-1");
        AuthClaims claims = service.parse(token);
        assertEquals("42", claims.sub());
        assertEquals("alice", claims.username());
        assertEquals("hotel-app-be", claims.iss());
        assertEquals("hotel-web", claims.aud());
        assertEquals(List.of("admin", "staff"), claims.roles());
        assertEquals("sess-1", claims.sid());
        assertNotNull(claims.exp());
        assertTrue(claims.iat() >= before);
        assertThat(claims.exp()).isBetween(before + 1800, before + 1801);
    }

    @Test
    void omitsExpInDesktopModeAndIgnoresExpiry() throws Exception {
        AppProperties desktop = props(true);
        JwtService service = new JwtService(desktop);
        String token = service.issueAccessToken(7L, "bob", List.of(), null);
        assertFalse(rawPayload(token).contains("exp"));
        AuthClaims parsed = service.parse(token);
        assertNull(parsed.exp());
        assertNull(parsed.sid());

        String expired = Jwts.builder()
                .subject("7")
                .issuer("hotel-app-be")
                .audience().add("hotel-web").and()
                .claim("username", "bob")
                .claim("roles", List.of())
                .claim("iat", Instant.now().getEpochSecond() - 3600)
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
        assertEquals("7", service.parse(expired).sub());
    }

    @Test
    void rejectsExpiredTokenOutsideDesktopMode() {
        String expired = Jwts.builder()
                .subject("1")
                .issuer("hotel-app-be")
                .audience().add("hotel-web").and()
                .claim("iat", Instant.now().getEpochSecond() - 3600)
                .expiration(new Date(System.currentTimeMillis() - 2000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
        assertThrows(JwtValidationException.class, () -> new JwtService(props(false)).parse(expired));
    }

    @Test
    void requiresExpClaimOutsideDesktopMode() {
        String noExp = Jwts.builder()
                .subject("1")
                .issuer("hotel-app-be")
                .audience().add("hotel-web").and()
                .claim("iat", Instant.now().getEpochSecond())
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
        assertThrows(JwtValidationException.class,
                () -> new JwtService(props(false)).parse(noExp));
    }

    @Test
    void rejectsWrongSignatureGarbageAndWrongIssuer() {
        JwtService service = new JwtService(props(false));
        assertThrows(JwtValidationException.class, () -> service.parse("not-a-jwt"));
        String otherKey = Jwts.builder()
                .subject("1").issuer("hotel-app-be").audience().add("hotel-web").and()
                .claim("exp", Instant.now().getEpochSecond() + 600)
                .signWith(Keys.hmacShaKeyFor("another-secret-key-that-is-long-enough!!".getBytes(
                        StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        assertThrows(JwtValidationException.class, () -> service.parse(otherKey));
        String wrongIssuer = Jwts.builder()
                .subject("1").issuer("someone-else").audience().add("hotel-web").and()
                .claim("exp", Instant.now().getEpochSecond() + 600)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
        assertThrows(JwtValidationException.class, () -> service.parse(wrongIssuer));
    }

    @Test
    void rejectsShortSecretAtStartup() {
        AppProperties shortProps = new AppProperties();
        shortProps.setJwtSecret("too-short");
        assertThrows(IllegalStateException.class, () -> new JwtService(shortProps));
    }

    @Test
    void rejectsTokensMissingRequiredClaims() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        JwtService service = new JwtService(props(false));
        long now = Instant.now().getEpochSecond();
        String noUsername = Jwts.builder().subject("1").issuer("hotel-app-be")
                .audience().add("hotel-web").and()
                .claim("iat", now).claim("roles", List.of())
                .claim("exp", now + 600)
                .signWith(key, Jwts.SIG.HS256).compact();
        String noRoles = Jwts.builder().subject("1").issuer("hotel-app-be")
                .audience().add("hotel-web").and()
                .claim("username", "a").claim("iat", now).claim("exp", now + 600)
                .signWith(key, Jwts.SIG.HS256).compact();
        String noIat = Jwts.builder().subject("1").issuer("hotel-app-be")
                .audience().add("hotel-web").and()
                .claim("username", "a").claim("roles", List.of()).claim("exp", now + 600)
                .signWith(key, Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> service.parse(noUsername))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> service.parse(noRoles))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> service.parse(noIat))
                .isInstanceOf(JwtValidationException.class);
    }

    private String rawPayload(String token) {
        String[] parts = token.split("\\.");
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }
}
