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

    /** {@code get_i32}: parsed integer or fallback when missing/unparseable. */
    public int getInt(String key, int fallback) {
        try {
            String value = jdbc.queryForObject(
                    "SELECT value FROM system_settings WHERE key = ?", String.class, key);
            if (value != null) {
                return Integer.parseInt(value.trim());
            }
        } catch (Exception ignored) {
            // Missing row / non-numeric value falls back exactly like upstream.
        }
        return fallback;
    }

    /** {@code get_positive_i32}: the stored value only when a positive integer. */
    public int getPositiveInt(String key, int fallback) {
        try {
            String value = jdbc.queryForObject(
                    "SELECT value FROM system_settings WHERE key = ?", String.class, key);
            if (value != null) {
                int parsed = Integer.parseInt(value.trim());
                if (parsed > 0) {
                    return parsed;
                }
            }
        } catch (Exception ignored) {
            // Missing row / non-numeric value falls back exactly like upstream.
        }
        return fallback;
    }

    /**
     * {@code get_hotel_display_name}: the branded name for authenticator
     * prompts — the configured key when set, else {@code hotel_name}, else
     * {@code "Hotel"}.
     */
    public String getHotelDisplayName(String key) {
        return getString(key, getString("hotel_name", "Hotel"));
    }

    /**
     * {@code SettingsRepository::updated_at}: when the setting last changed —
     * the policy-effective-from stamp for {@code require_two_factor_roles}.
     */
    public java.time.Instant updatedAt(String key) {
        try {
            Object value = jdbc.queryForObject(
                    "SELECT updated_at FROM system_settings WHERE key = ?",
                    Object.class, key);
            if (value instanceof java.sql.Timestamp ts) {
                return ts.toInstant();
            }
            if (value instanceof java.time.OffsetDateTime odt) {
                return odt.toInstant();
            }
            return null;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }
}
