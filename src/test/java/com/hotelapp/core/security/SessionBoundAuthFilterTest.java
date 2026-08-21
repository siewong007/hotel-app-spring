package com.hotelapp.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelapp.core.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionBoundAuthFilterTest {

    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-32ch";

    private JwtService jwtService;
    private SessionBoundAuthFilter filter;
    private boolean sessionActive = true;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setJwtSecret(SECRET);
        jwtService = new JwtService(props);
        filter = new SessionBoundAuthFilter(jwtService,
                (userId, sessionId) -> sessionActive);
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(String uri, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void passesThroughWithoutAuthorizationHeader() throws Exception {
        MockHttpServletResponse response = run("/api/bookings", null);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void authenticatesValidTokenWithActiveSession() throws Exception {
        String token = jwtService.issueAccessToken(9L, "alice", List.of("admin"), "sess-abc");
        MockHttpServletResponse response = run("/api/bookings", "Bearer " + token);
        assertThat(response.getStatus()).isEqualTo(200);
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        assertThat(((AuthenticatedUser) authentication.getPrincipal()).userId()).isEqualTo(9L);
    }

    @Test
    void rejectsInactiveSessionWithExactBody() throws Exception {
        sessionActive = false;
        String token = jwtService.issueAccessToken(9L, "alice", List.of(), "sess-abc");
        MockHttpServletResponse response = run("/api/bookings", "Bearer " + token);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("Session has been logged out.");
    }

    @Test
    void rejectsTokenWithoutSid() throws Exception {
        String token = jwtService.issueAccessToken(9L, "alice", List.of(), null);
        MockHttpServletResponse response = run("/api/bookings", "Bearer " + token);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("error").asText())
                .isEqualTo("Session-bound authentication is required.");
    }

    @Test
    void rejectsGarbageTokenWithPolishedMessage() throws Exception {
        MockHttpServletResponse response = run("/api/bookings", "Bearer garbage");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("Invalid or expired token.");
    }

    @Test
    void guestPortalPathsFallThroughOnBadToken() throws Exception {
        MockHttpServletResponse response =
                run("/api/guest-portal/me/summary", "Bearer garbage");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
