package com.hotelapp.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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

    @LocalServerPort
    int port;

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

    private final HttpClient http = HttpClient.newHttpClient();

    record Resp(int status, String body, List<String> setCookies) {
        String cookieValue(String name) {
            return setCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst()
                    .orElse(null);
        }

        String rawCookie(String name) {
            return setCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst()
                    .orElse(null);
        }
    }

    private Resp post(String path, String jsonBody, String cookieHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
        if (cookieHeader != null) {
            builder.header("Cookie", cookieHeader);
        }
        HttpResponse<String> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), response.body(),
                response.headers().allValues("set-cookie"));
    }

    private Resp get(String path, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path)).GET();
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), response.body(), List.of());
    }

    @Test
    void wrongPasswordReturnsExactRemainingAttemptsBody() throws Exception {
        Resp response = post("/api/auth/login",
                "{\"username\":\"admin\",\"password\":\"wrong-pass\"}", null);
        assertThat(response.status()).isEqualTo(401);
        assertThat(field(response.body(), "error"))
                .isEqualTo("Invalid credentials. 4 attempt(s) remaining before account lockout.");
    }

    @Test
    void loginRefreshLogoutLifecycle() throws Exception {
        Resp login = post("/api/auth/login",
                "{\"username\":\"admin\",\"password\":\"admin123\"}", null);
        assertThat(login.status()).isEqualTo(200);
        String accessToken = field(login.body(), "access_token");
        assertThat(accessToken).isNotBlank();
        assertThat(login.body()).contains("\"username\":\"admin\"");
        assertThat(login.body()).contains("admin");

        String refreshCookie = login.cookieValue("refresh_token");
        assertThat(refreshCookie).as("Set-Cookie refresh_token").isNotNull();

        String cookieHeader = "refresh_token=" + extractValue(refreshCookie);
        Resp refreshed = post("/api/auth/refresh", "", cookieHeader);
        assertThat(refreshed.status()).isEqualTo(200);

        String rotatedCookie = refreshed.cookieValue("refresh_token");
        assertThat(extractValue(rotatedCookie)).isNotEqualTo(extractValue(refreshCookie));

        Resp stale = post("/api/auth/refresh", "", refreshCookie);
        assertThat(stale.status()).isEqualTo(401);

        Resp access = get("/api/auth/access", accessToken);
        assertThat(access.status()).isEqualTo(200);
        assertThat(access.body()).contains("admin");

        post("/api/auth/logout", "",
                "refresh_token=" + extractValue(rotatedCookie));
        Resp afterLogout = get("/api/auth/access", accessToken);
        assertThat(afterLogout.status()).isEqualTo(401);
        assertThat(field(afterLogout.body(), "error")).isEqualTo("Session has been logged out.");
    }

    @Test
    void unknownUserGetsGenericInvalidCredentials() throws Exception {
        Resp response = post("/api/auth/login", "{\"username\":\"ghost\",\"password\":\"x\"}",
                null);
        assertThat(response.status()).isEqualTo(401);
        assertThat(field(response.body(), "error")).isEqualTo("Invalid credentials.");
    }

    @Test
    void missingCookieOnRefreshIs401() throws Exception {
        Resp response = post("/api/auth/refresh", "", null);
        assertThat(response.status()).isEqualTo(401);
        assertThat(field(response.body(), "error")).isEqualTo("Missing refresh token.");
    }

    private static String extractValue(String setCookie) {
        return setCookie.split(";", 2)[0].substring("refresh_token=".length());
    }

    private static String field(String json, String name) {
        tools.jackson.databind.JsonNode node =
                new tools.jackson.databind.ObjectMapper().readTree(json);
        return node.get(name).asString();
    }
}
