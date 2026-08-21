package com.hotelapp.core.security;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Port of core/rate_limiter.rs: sliding-window counters keyed by IP (or an
 * arbitrary key), with the same categories and limits.
 */
@Service
public class RateLimitService {

    public record Decision(boolean allowed, long retryAfterSecs) {
    }

    public record Config(long maxRequests, long windowSecs) {
    }

    private static class Entry {
        final Deque<Instant> timestamps = new ArrayDeque<>();
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public static final Config AUTH = new Config(5, 60);
    public static final Config REGISTER = new Config(10, 600);
    public static final Config SENSITIVE = new Config(10, 300);
    public static final Config WEBHOOK = new Config(60, 60);
    public static final Config GUEST_PORTAL_VERIFY = new Config(10, 300);
    public static final Config GUEST_PORTAL_TOKEN_IP = new Config(240, 900);
    public static final Config GUEST_PORTAL_BOOKING = new Config(5, 900);
    public static final Config GUEST_PORTAL_TOKEN = new Config(5, 900);
    public static final Config GUEST_PORTAL_TOKEN_PAYMENT = new Config(100, 600);
    public static final Config GUEST_PORTAL_PAYMENT = new Config(100, 600);
    public static final Config GUEST_PORTAL_TOKEN_READ = new Config(120, 900);
    public static final Config GUEST_PORTAL_SUPPORT_MUTATION = new Config(30, 900);
    public static final Config GUEST_PORTAL_SUPPORT_MUTATION_IP = new Config(120, 900);
    public static final Config GUEST_PORTAL_BOOKING_CREATE = new Config(10, 900);
    public static final Config GUEST_PORTAL_BOOKING_CREATE_IP = new Config(30, 900);
    public static final Config GUEST_PORTAL_EKYC = new Config(20, 900);
    public static final Config GUEST_PORTAL_EKYC_IP = new Config(60, 900);

    public Decision check(Config config, String key) {
        Instant now = Instant.now();
        Entry entry = entries.computeIfAbsent(categoryKey(config, key), k -> new Entry());
        synchronized (entry) {
            Instant cutoff = now.minusSeconds(config.windowSecs());
            while (!entry.timestamps.isEmpty() && !entry.timestamps.peekFirst().isAfter(cutoff)) {
                entry.timestamps.pollFirst();
            }
            if (entry.timestamps.size() < config.maxRequests()) {
                entry.timestamps.addLast(now);
                return new Decision(true, 0);
            }
            Instant oldest = entry.timestamps.peekFirst();
            long retryAfter = config.windowSecs()
                    - (now.getEpochSecond() - oldest.getEpochSecond());
            return new Decision(false, Math.max(retryAfter, 1));
        }
    }

    private String categoryKey(Config config, String key) {
        return System.identityHashCode(config) + ":" + key;
    }
}
