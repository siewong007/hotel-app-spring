package com.hotelapp.core.security;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Port of core/rate_limiter.rs: sliding-window counters keyed by IP (or an
 * arbitrary key), with the same categories and limits, plus the 5-minute
 * stale-bucket cleanup task.
 */
@Service
public class RateLimitService {

    public record Decision(boolean allowed, long retryAfterSecs) {
    }

    public enum Category {
        AUTH(5, 60),
        REGISTER(10, 600),
        SENSITIVE(10, 300),
        WEBHOOK(60, 60),
        GUEST_PORTAL_VERIFY(10, 300),
        GUEST_PORTAL_TOKEN_IP(240, 900),
        GUEST_PORTAL_BOOKING(5, 900),
        GUEST_PORTAL_TOKEN(5, 900),
        GUEST_PORTAL_TOKEN_PAYMENT(100, 600),
        GUEST_PORTAL_PAYMENT(100, 600),
        GUEST_PORTAL_TOKEN_READ(120, 900),
        GUEST_PORTAL_SUPPORT_MUTATION(30, 900),
        GUEST_PORTAL_SUPPORT_MUTATION_IP(120, 900),
        GUEST_PORTAL_BOOKING_CREATE(10, 900),
        GUEST_PORTAL_BOOKING_CREATE_IP(30, 900),
        GUEST_PORTAL_EKYC(20, 900),
        GUEST_PORTAL_EKYC_IP(60, 900),
        PUBLIC_BOOKING_READ_IP(120, 900),
        PUBLIC_BOOKING_CREATE_IP(10, 900);

        final long maxRequests;
        final long windowSecs;

        Category(long maxRequests, long windowSecs) {
            this.maxRequests = maxRequests;
            this.windowSecs = windowSecs;
        }
    }

    private static class Entry {
        final Deque<Instant> timestamps = new ArrayDeque<>();
    }

    private final Map<Category, Map<String, Entry>> buckets = new ConcurrentHashMap<>();

    public Decision check(Category category, String key) {
        Instant now = Instant.now();
        Map<String, Entry> categoryBuckets =
                buckets.computeIfAbsent(category, c -> new ConcurrentHashMap<>());
        Entry entry = categoryBuckets.computeIfAbsent(key, k -> new Entry());
        synchronized (entry) {
            Instant cutoff = now.minusSeconds(category.windowSecs);
            while (!entry.timestamps.isEmpty() && !entry.timestamps.peekFirst().isAfter(cutoff)) {
                entry.timestamps.pollFirst();
            }
            if (entry.timestamps.size() < category.maxRequests) {
                entry.timestamps.addLast(now);
                return new Decision(true, 0);
            }
            Instant oldest = entry.timestamps.peekFirst();
            long retryAfter = category.windowSecs - (now.getEpochSecond() - oldest.getEpochSecond());
            return new Decision(false, Math.max(retryAfter, 1));
        }
    }

    /**
     * Port of the Rust cleanup task spawned in RateLimiter::new: every 5
     * minutes drop timestamps older than the window and remove empty buckets.
     */
    @Scheduled(fixedDelay = 300_000)
    public void cleanupStaleEntries() {
        Instant now = Instant.now();
        for (Map.Entry<Category, Map<String, Entry>> byCategory : buckets.entrySet()) {
            long windowSecs = byCategory.getKey().windowSecs;
            byCategory.getValue().values().removeIf(entry -> {
                synchronized (entry) {
                    entry.timestamps.removeIf(t ->
                            t.plusSeconds(windowSecs).isBefore(now));
                    return entry.timestamps.isEmpty();
                }
            });
        }
    }
}
