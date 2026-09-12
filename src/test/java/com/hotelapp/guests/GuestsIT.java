package com.hotelapp.guests;

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
class GuestsIT {

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

    record Resp(int status, String body) {
    }

    private Resp post(String path, String jsonBody, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), response.body());
    }

    private Resp patch(String path, String jsonBody, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), response.body());
    }

    private Resp delete(String path, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .DELETE();
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), response.body());
    }

    private Resp get(String path, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path)).GET();
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), response.body());
    }

    private static String cachedAdminToken;

    private String adminToken() throws Exception {
        if (cachedAdminToken == null) {
            Resp login = post("/api/auth/login",
                    "{\"username\":\"admin\",\"password\":\"admin123\"}", null);
            assertThat(login.status()).isEqualTo(200);
            cachedAdminToken = field(login.body(), "access_token");
        }
        return cachedAdminToken;
    }

    private static String field(String json, String name) {
        try {
            tools.jackson.databind.JsonNode node =
                    new tools.jackson.databind.ObjectMapper().readTree(json);
            return node.get(name).isNull() ? null : node.get(name).asString();
        } catch (Exception e) {
            return null;
        }
    }

    private static tools.jackson.databind.JsonNode json(String body) {
        return new tools.jackson.databind.ObjectMapper().readTree(body);
    }

    @Test
    void createComputesNickNameAndRejectsDuplicate() throws Exception {
        String token = adminToken();
        long suffix = System.nanoTime() % 100000;
        String first = "Itta" + suffix;
        String last = "Karna" + suffix;

        Resp created = post("/api/guests",
                "{\"first_name\":\"" + first + "\",\"last_name\":\"" + last
                        + "\",\"email\":\"GUEST@EXAMPLE.COM\",\"phone\":\"+60 12-345\"}",
                token);
        assertThat(created.status()).isEqualTo(200);
        tools.jackson.databind.JsonNode guest = json(created.body());
        assertThat(guest.get("nick_name").asString()).isEqualTo(first + " " + last);
        assertThat(guest.get("first_name").asString()).isEqualTo(first);
        assertThat(guest.get("email").asString()).isEqualTo("guest@example.com");
        assertThat(guest.get("phone").asString()).isEqualTo("+6012345");
        assertThat(guest.has("full_name")).isFalse();
        assertThat(guest.get("guest_type").asString()).isEqualTo("non_member");
        assertThat(guest.get("tourism_type").asString()).isEqualTo("local");

        String nick = guest.get("nick_name").asString();
        String stored = jdbc.queryForObject(
                "SELECT nick_name FROM guests WHERE id = ?", String.class,
                guest.get("id").asLong());
        assertThat(stored).isEqualTo(nick);

        Resp duplicate = post("/api/guests",
                "{\"first_name\":\"" + first + "\",\"last_name\":\"" + last + "\"}", token);
        assertThat(duplicate.status()).isEqualTo(400);
        assertThat(field(duplicate.body(), "error"))
                .isEqualTo("A guest with the name '" + nick + "' already exists (Guest ID #"
                        + guest.get("id").asLong()
                        + "). Please select the existing guest instead of creating a new one.");

        Resp missingFirst = post("/api/guests", "{\"last_name\":\"Nobody\"}", token);
        assertThat(missingFirst.status()).isEqualTo(400);
        assertThat(field(missingFirst.body(), "error"))
                .isEqualTo("First name cannot be empty.");

        Resp badEmail = post("/api/guests",
                "{\"first_name\":\"Mail\",\"last_name\":\"Check\",\"email\":\"not-an-email\"}",
                token);
        assertThat(badEmail.status()).isEqualTo(400);
        assertThat(field(badEmail.body(), "error")).isEqualTo("Invalid email format.");
    }

    @Test
    void listSearchesFiltersAndPaginates() throws Exception {
        String token = adminToken();
        long suffix = System.nanoTime() % 100000;
        String first = "Seek" + suffix;
        post("/api/guests",
                "{\"first_name\":\"" + first + "\",\"last_name\":\"Target\"}", token);
        post("/api/guests",
                "{\"first_name\":\"" + first + "\",\"last_name\":\"Other\","
                        + "\"tourism_type\":\"foreign\"}", token);

        Resp all = get("/api/guests", token);
        assertThat(all.status()).isEqualTo(200);
        tools.jackson.databind.JsonNode page = json(all.body());
        assertThat(page.get("page").asLong()).isEqualTo(1);
        assertThat(page.get("page_size").asLong()).isEqualTo(100);
        assertThat(page.get("total").asLong()).isGreaterThanOrEqualTo(2);
        tools.jackson.databind.JsonNode firstRow = page.get("data").get(0);
        assertThat(firstRow.has("nick_name")).isTrue();
        assertThat(firstRow.has("full_name")).isFalse();

        Resp searched = get("/api/guests?search=" + first + "+Target", token);
        assertThat(searched.status()).isEqualTo(200);
        tools.jackson.databind.JsonNode hits = json(searched.body());
        assertThat(hits.get("total").asLong()).isEqualTo(1);
        tools.jackson.databind.JsonNode hit = hits.get("data").get(0);
        assertThat(hit.get("nick_name").asString()).isEqualTo(first + " Target");
        assertThat(hit.has("bookings_count")).isTrue();
        assertThat(hit.has("ekyc_summary")).isTrue();
        assertThat(hit.get("ekyc_summary").get("status").asString())
                .isEqualTo("not_submitted");

        Resp foreign = get("/api/guests?tourism_type=foreign&search=" + first, token);
        assertThat(json(foreign.body()).get("total").asLong()).isEqualTo(1);
        assertThat(json(foreign.body()).get("data").get(0)
                .get("tourism_type").asString()).isEqualTo("foreign");

        // A created guest always carries a tourism_type ('local' by default);
        // the missing_tourism filter only matches rows whose column is NULL.
        jdbc.update("UPDATE guests SET tourism_type = NULL WHERE nick_name = ?",
                first + " Target");
        Resp missingTourism = get("/api/guests?missing_tourism=true&search=" + first, token);
        assertThat(json(missingTourism.body()).get("total").asLong()).isEqualTo(1);
        assertThat(json(missingTourism.body()).get("data").get(0)
                .get("nick_name").asString()).isEqualTo(first + " Target");
    }

    @Test
    void updateMergesFieldsAndRejectsNameConflict() throws Exception {
        String token = adminToken();
        long suffix = System.nanoTime() % 100000;
        post("/api/guests",
                "{\"first_name\":\"One" + suffix + "\",\"last_name\":\"Alpha\"}", token);
        Resp second = post("/api/guests",
                "{\"first_name\":\"Two" + suffix + "\",\"last_name\":\"Beta\"}", token);
        long secondId = json(second.body()).get("id").asLong();

        Resp updated = patch("/api/guests/" + secondId,
                "{\"email\":\"two@example.com\",\"title\":\"Dr\"}", token);
        assertThat(updated.status()).isEqualTo(200);
        tools.jackson.databind.JsonNode guest = json(updated.body());
        assertThat(guest.get("email").asString()).isEqualTo("two@example.com");
        assertThat(guest.get("title").asString()).isEqualTo("Dr");
        assertThat(guest.get("nick_name").asString()).isEqualTo("Two" + suffix + " Beta");
        assertThat(guest.has("ekyc_summary")).isTrue();

        Resp renamed = patch("/api/guests/" + secondId, "{\"last_name\":\"Gamma\"}", token);
        assertThat(renamed.status()).isEqualTo(200);
        assertThat(json(renamed.body()).get("nick_name").asString())
                .isEqualTo("Two" + suffix + " Gamma");

        Resp conflict = patch("/api/guests/" + secondId,
                "{\"first_name\":\"One" + suffix + "\",\"last_name\":\"Alpha\"}", token);
        assertThat(conflict.status()).isEqualTo(400);
        assertThat(field(conflict.body(), "error")).contains("Guest names must be unique");
    }

    @Test
    void deleteHardDeletesAndBlocksCheckedIn() throws Exception {
        String token = adminToken();
        Resp created = post("/api/guests",
                "{\"first_name\":\"Gone\",\"last_name\":\"Soon\"}", token);
        long id = json(created.body()).get("id").asLong();

        jdbc.update("""
                INSERT INTO bookings (guest_id, room_id, check_in_date, check_out_date,
                    status, created_by)
                VALUES (?, (SELECT id FROM rooms LIMIT 1), CURRENT_DATE,
                    CURRENT_DATE + 1, 'checked_in', 1000)
                """, id);
        Resp blocked = delete("/api/guests/" + id, token);
        assertThat(blocked.status()).isEqualTo(400);
        assertThat(field(blocked.body(), "error")).contains("currently checked in");

        jdbc.update("UPDATE bookings SET status = 'checked_out' WHERE guest_id = ?", id);
        Resp deleted = delete("/api/guests/" + id, token);
        assertThat(deleted.status()).isEqualTo(200);
        assertThat(json(deleted.body()).get("success").asBoolean()).isTrue();
        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM guests WHERE id = ?", Integer.class, id);
        assertThat(remaining).isEqualTo(0);
    }

    @Test
    void profileReturnsGuest360Shape() throws Exception {
        String token = adminToken();
        Resp created = post("/api/guests",
                "{\"first_name\":\"Prof\",\"last_name\":\"Ile\"}", token);
        long id = json(created.body()).get("id").asLong();

        Resp profile = get("/api/guests/" + id + "/profile", token);
        assertThat(profile.status()).isEqualTo(200);
        tools.jackson.databind.JsonNode body = json(profile.body());
        assertThat(body.get("guest").get("nick_name").asString()).isEqualTo("Prof Ile");
        assertThat(body.has("summary")).isTrue();
        assertThat(body.get("summary").has("completed_stays")).isTrue();
        assertThat(body.get("summary").has("outstanding_balance")).isTrue();
        assertThat(body.get("ekyc_summary").get("status").asString())
                .isEqualTo("not_submitted");
        assertThat(body.get("reservations").isArray()).isTrue();
        assertThat(body.get("duplicate_candidates").isArray()).isTrue();
    }

    @Test
    void linkMyGuestsAndUnlink() throws Exception {
        String token = adminToken();
        Resp created = post("/api/guests",
                "{\"first_name\":\"Link\",\"last_name\":\"Me\"}", token);
        long guestId = json(created.body()).get("id").asLong();

        Resp linked = post("/api/guests/link",
                "{\"guest_id\":" + guestId + ",\"relationship_type\":\"family\"}", token);
        assertThat(linked.status()).isEqualTo(200);
        assertThat(json(linked.body()).get("guest_id").asLong()).isEqualTo(guestId);

        Resp mine = get("/api/guests/my-guests", token);
        assertThat(mine.status()).isEqualTo(200);
        tools.jackson.databind.JsonNode rows = json(mine.body());
        assertThat(rows.isArray()).isTrue();
        boolean found = false;
        for (tools.jackson.databind.JsonNode row : rows) {
            if (row.get("id").asLong() == guestId) {
                found = true;
                assertThat(row.get("nick_name").asString()).isEqualTo("Link Me");
                assertThat(row.has("ekyc_summary")).isTrue();
            }
        }
        assertThat(found).as("linked guest appears in my-guests").isTrue();

        Resp unlinked = delete("/api/guests/unlink/" + guestId, token);
        assertThat(unlinked.status()).isEqualTo(200);
        Resp missing = delete("/api/guests/unlink/" + guestId, token);
        assertThat(missing.status()).isEqualTo(404);
        assertThat(field(missing.body(), "error")).isEqualTo("Guest link not found.");
    }

    @Test
    void listIsEmptyForUsersWithoutGuestPermission() throws Exception {
        String token = adminToken();
        String username = "noguest" + (System.nanoTime() % 100000);
        Resp registered = post("/api/auth/register",
                "{\"username\":\"" + username + "\",\"email\":\"" + username
                        + "@example.com\",\"password\":\"Tr7!mXq9zL2\","
                        + "\"first_name\":\"No\",\"last_name\":\"Guest\","
                        + "\"phone\":\"+60123456789\","
                        + "\"consents\":["
                        + "{\"document\":\"terms_of_service\",\"version\":\"2026-09-09\",\"granted\":true,\"locale\":\"en\"},"
                        + "{\"document\":\"privacy_notice\",\"version\":\"2026-09-09\",\"granted\":true,\"locale\":\"en\"}]}",
                token);
        assertThat(registered.status()).isEqualTo(200);
        jdbc.update("UPDATE users SET is_verified = true WHERE username = ?", username);
        Resp login = post("/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"Tr7!mXq9zL2\"}", null);
        assertThat(login.status()).isEqualTo(200);
        String userToken = field(login.body(), "access_token");

        Resp list = get("/api/guests", userToken);
        assertThat(list.status()).isEqualTo(200);
        tools.jackson.databind.JsonNode page = json(list.body());
        assertThat(page.get("data").size()).isEqualTo(0);
        assertThat(page.get("total").asLong()).isEqualTo(0);
    }
}
