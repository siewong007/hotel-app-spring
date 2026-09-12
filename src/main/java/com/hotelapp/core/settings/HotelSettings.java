package com.hotelapp.core.settings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Port of {@code core/settings_cache.rs::get_string}: read a
 * {@code system_settings} value, falling back when the row is missing or
 * blank. (Upstream adds a short-lived in-memory cache; the read semantics are
 * identical without it.)
 */
@Component
public class HotelSettings {

    private final JdbcTemplate jdbc;

    public HotelSettings(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String getString(String key, String fallback) {
        try {
            String value = jdbc.queryForObject(
                    "SELECT value FROM system_settings WHERE key = ?", String.class, key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        } catch (Exception ignored) {
            // Missing row / unreadable value falls back exactly like upstream.
        }
        return fallback;
    }

    /** {@code get_decimal}: parsed value or fallback when missing/unparseable. */
    public java.math.BigDecimal getDecimal(String key, java.math.BigDecimal fallback) {
        try {
            String value = jdbc.queryForObject(
                    "SELECT value FROM system_settings WHERE key = ?", String.class, key);
            if (value != null) {
                return new java.math.BigDecimal(value.trim());
            }
        } catch (Exception ignored) {
            // Missing row / unreadable value falls back exactly like upstream.
        }
        return fallback;
    }

    /** {@code get_positive_decimal}: the stored value only when positive. */
    public java.math.BigDecimal getPositiveDecimal(String key, java.math.BigDecimal fallback) {
        java.math.BigDecimal value = getDecimal(key, fallback);
        return value.signum() > 0 ? value : fallback;
    }
}
