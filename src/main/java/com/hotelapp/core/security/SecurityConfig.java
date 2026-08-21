package com.hotelapp.core.security;

import com.hotelapp.core.error.ApiErrorResponses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Authorization happens per-handler (PermissionGate), mirroring the Rust
 * router where each route calls require_permission_helper. The chain's job is
 * CORS + session-bound JWT authentication + envelope-shaped rejections.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SessionBoundAuthFilter sessionBoundAuthFilter;

    public SecurityConfig(SessionBoundAuthFilter sessionBoundAuthFilter) {
        this.sessionBoundAuthFilter = sessionBoundAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                ApiErrorResponses.write(
                                        com.hotelapp.core.error.ApiError.unauthorized(
                                                "Missing authorization header"), response)))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(sessionBoundAuthFilter,
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
