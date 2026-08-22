package com.hotelapp.core.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt-secret=unit-test-secret-key-that-is-long-enough-32ch")
@Testcontainers
class HealthIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @org.springframework.test.context.DynamicPropertySource
    static void datasourceProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    int port;

    @Test
    void healthReturnsOkWhenDatabaseReachable() throws Exception {
        HttpResponse<String> response = get("/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"ok\"");
    }

    @Test
    void wsStatusIsPubliclyReachable() throws Exception {
        HttpResponse<String> response = get("/ws/status");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"connected\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
