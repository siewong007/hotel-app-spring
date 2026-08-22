package com.hotelapp.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.jwt-secret=unit-test-secret-key-that-is-long-enough-32ch",
                "SKIP_EMAIL_VERIFICATION=true"})
@Testcontainers
class AuthFlowIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    com.hotelapp.core.bootstrap.ReferenceDataSeeder seeder;

    static boolean seeded = false;

    @BeforeAll
    static void seed(@Autowired com.hotelapp.core.bootstrap.ReferenceDataSeeder seeder,
            @Autowired JdbcTemplate jdbc) {
        if (!seeded) {
            seeder.seed();
            jdbc.update("UPDATE users SET password_hash = ?, is_verified = true "
                    + "WHERE username = 'admin'",
                    new BCryptPasswordEncoder().encode("admin123"));
            seeded = true;
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> post(String uri, String body) {
        return rest.exchange(uri, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()),
                String.class);
    }

    private ResponseEntity<String> get(String uri, String bearer) {
        HttpHeaders headers = jsonHeaders();
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void wrongPasswordReturnsExactRemainingAttemptsBody() {
        ResponseEntity<String> response =
                post("/api/auth/login",
                        "{\"username\":\"admin\",\"password\":\"wrong-pass\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode body = read(response.getBody());
        assertThat(body.get("error").asText())
                .isEqualTo("Invalid credentials. 4 attempt(s) remaining before account lockout.");
    }

    @Test
    void loginRefreshLogoutLifecycle() throws Exception {
        ResponseEntity<String> login = post("/api/auth/login",
                "{\"username\":\"admin\",\"password\":\"admin123\"}");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode loginBody = read(login.getBody());
        String accessToken = loginBody.get("access_token").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(loginBody.get("user").get("username").asText()).isEqualTo("admin");
        assertThat(loginBody.get("roles").toString()).contains("admin");

        List<String> cookies = login.getHeaders().get("Set-Cookie");
        assertThat(cookies).isNotNull();
        assertThat(cookies.stream().anyMatch(c -> c.startsWith("refresh_token=")
                && c.contains("HttpOnly") && c.contains("/api/auth"))).isTrue();
        String refreshCookie = cookies.stream()
                .filter(c -> c.startsWith("refresh_token="))
                .findFirst().orElseThrow();

        HttpHeaders refreshHeaders = jsonHeaders();
        refreshHeaders.put(HttpHeaders.COOKIE, List.of(refreshCookie));
        ResponseEntity<String> refreshed = rest.exchange("/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(refreshHeaders), String.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotatedCookie = refreshed.getHeaders().get("Set-Cookie").stream()
                .filter(c -> c.startsWith("refresh_token=")).findFirst().orElseThrow();
        String oldCookieValue = extractValue(refreshCookie);
        String newCookieValue = extractValue(rotatedCookie);
        assertThat(newCookieValue).isNotEqualTo(oldCookieValue);

        ResponseEntity<String> stale = rest.exchange("/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(withCookie(jsonHeaders(), refreshCookie)), String.class);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> access = get("/api/auth/access", accessToken);
        assertThat(access.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode snapshot = read(access.getBody());
        assertThat(snapshot.get("roles").toString()).contains("admin");
        assertThat(snapshot.get("permissions").size()).isGreaterThan(10);

        rest.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(withCookie(jsonHeaders(), rotatedCookie)), String.class);
        ResponseEntity<String> afterLogout = get("/api/auth/access", accessToken);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode logoutError = read(afterLogout.getBody());
        assertThat(logoutError.get("error").asText()).isEqualTo("Session has been logged out.");
    }

    @Test
    void unknownUserGetsGenericInvalidCredentials() {
        ResponseEntity<String> response =
                post("/api/auth/login", "{\"username\":\"ghost\",\"password\":\"whatever1\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(read(response.getBody()).get("error").asText())
                .isEqualTo("Invalid credentials.");
    }

    @Test
    void missingCookieOnRefreshIs401() {
        ResponseEntity<String> response = post("/api/auth/refresh", "");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(read(response.getBody()).get("error").asText())
                .isEqualTo("Missing refresh token.");
    }

    private HttpHeaders withCookie(HttpHeaders base, String cookie) {
        base.put(HttpHeaders.COOKIE, List.of(cookie));
        return base;
    }

    private String extractValue(String setCookie) {
        return setCookie.split(";", 2)[0].substring("refresh_token=".length());
    }

    private JsonNode read(String body) {
        try {
            return new ObjectMapper().readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
