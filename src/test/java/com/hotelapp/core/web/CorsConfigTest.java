package com.hotelapp.core.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotelapp.core.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigTest {

    @Test
    void wildcardModeUsesOriginPatternsWithoutCredentials() {
        AppProperties properties = new AppProperties();
        properties.setAllowedOriginsRaw(" * ");

        CorsConfigurationSource source = new CorsConfig(properties).corsConfigurationSource();

        CorsConfiguration config = ((UrlBasedCorsConfigurationSource) source)
                .getCorsConfiguration(new MockHttpServletRequest());
        assertThat(config.getAllowedOriginPatterns()).containsExactly("*");
        assertThat(config.getAllowedOrigins()).isNull();
        assertThat(config.getAllowedHeaders()).containsExactly("*");
        assertThat(config.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(config.getAllowCredentials()).isFalse();
    }

    @Test
    void listModeSplitsCommaSeparatedOriginsWithCredentials() {
        AppProperties properties = new AppProperties();
        properties.setAllowedOriginsRaw("https://app.example.com, https://admin.example.com");

        CorsConfigurationSource source = new CorsConfig(properties).corsConfigurationSource();

        CorsConfiguration config = ((UrlBasedCorsConfigurationSource) source)
                .getCorsConfiguration(new MockHttpServletRequest());
        assertThat(config.getAllowedOriginPatterns())
                .containsExactly("https://app.example.com", "https://admin.example.com");
        assertThat(config.getAllowedHeaders())
                .containsExactly("Authorization", "Content-Type", "Accept");
        assertThat(config.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(config.getAllowCredentials()).isTrue();
    }
}
