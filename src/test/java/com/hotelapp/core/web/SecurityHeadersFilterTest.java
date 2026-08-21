package com.hotelapp.core.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SecurityHeadersFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SecurityHeadersFilterTest())
                .addFilters(new SecurityHeadersFilter())
                .build();
    }

    @Test
    void setsAllSecurityHeadersOnAnyResponse() throws Exception {
        mockMvc.perform(get("/anything"))
                .andExpect(status().isOk())
                .andExpect(header()
                        .string("Strict-Transport-Security", "max-age=31536000; includeSubDomains"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-XSS-Protection", "1; mode=block"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; script-src 'self'; style-src 'self'; "
                                + "img-src 'self' data: https:; font-src 'self' data:; "
                                + "connect-src 'self'; frame-ancestors 'none';"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    void doesNotOverwriteExistingHeader() throws Exception {
        mockMvc.perform(get("/existing-frame-options"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @RequestMapping("/**")
    String any() {
        return "ok";
    }

    @RequestMapping("/existing-frame-options")
    String existing(HttpServletResponse response) {
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        return "ok";
    }
}
