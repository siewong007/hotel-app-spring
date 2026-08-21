package com.hotelapp.core.security;

import com.hotelapp.core.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public static final long ACCESS_TOKEN_TTL_MINUTES = 30;
    public static final int MIN_JWT_SECRET_LEN = 32;

    private final AppProperties properties;
    private final SecretKey key;

    public JwtService(AppProperties properties) {
        validateSecret(properties.getJwtSecret());
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public static void validateSecret(String secret) {
        if (secret == null || secret.length() < MIN_JWT_SECRET_LEN) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_JWT_SECRET_LEN + " characters");
        }
    }

    public String issueAccessToken(long userId, String username, List<String> roles, String sessionId) {
        Instant now = Instant.now();
        var claims = Jwts.claims()
                .subject(String.valueOf(userId))
                .add("username", username)
                .add("iat", now.getEpochSecond())
                .add("roles", roles == null ? List.of() : roles);
        if (sessionId != null) {
            claims.add("sid", sessionId);
        }
        if (!properties.isDesktopMode()) {
            claims.add("exp",
                    now.plus(ACCESS_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES).getEpochSecond());
        }
        return Jwts.builder()
                .claims(claims.build())
                .issuer(properties.getJwtIssuer())
                .audience().add(properties.getJwtAudience()).and()
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public AuthClaims parse(String token) {
        boolean desktop = properties.isDesktopMode();
        try {
            return toAuthClaims(parsePayload(token));
        } catch (ExpiredJwtException e) {
            if (desktop) {
                return toAuthClaims(e.getClaims());
            }
            throw new JwtValidationException("Token has expired", e);
        } catch (JwtValidationException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtValidationException("Invalid token", e);
        }
    }

    private Claims parsePayload(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getJwtIssuer())
                .requireAudience(properties.getJwtAudience())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!properties.isDesktopMode() && claims.getExpiration() == null) {
            throw new JwtValidationException("Missing exp claim");
        }
        return claims;
    }

    private AuthClaims toAuthClaims(Claims claims) {
        Long exp = claims.getExpiration() == null ? null : claims.getExpiration().getTime() / 1000;
        List<String> roles = new ArrayList<>();
        Object rawRoles = claims.get("roles");
        if (rawRoles instanceof List<?> list) {
            for (Object item : list) {
                roles.add(String.valueOf(item));
            }
        }
        String username =
                claims.get("username") == null ? null : String.valueOf(claims.get("username"));
        String sid = claims.get("sid") == null ? null : String.valueOf(claims.get("sid"));
        return new AuthClaims(
                claims.getSubject(),
                username,
                claims.getIssuer(),
                claims.getAudience() == null || claims.getAudience().isEmpty()
                        ? properties.getJwtAudience()
                        : String.valueOf(claims.getAudience().iterator().next()),
                exp,
                claims.getIssuedAt() == null ? 0 : claims.getIssuedAt().getTime() / 1000,
                List.copyOf(roles),
                sid);
    }
}
