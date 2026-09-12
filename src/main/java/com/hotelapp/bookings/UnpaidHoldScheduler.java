package com.hotelapp.bookings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code services/unpaid_hold_scheduler.rs} — release the rooms held by
 * bookings that were never paid for. Off unless
 * {@code unpaid_hold_release_hours} is a positive number of hours; this class
 * only decides how often to ask, {@link BookingRelease} owns the policy.
 *
 * The sweep is bounded by the configured hold window, measured in hours, so
 * polling more often than 15 minutes buys nothing (upstream POLL_INTERVAL).
 */
@Component
public class UnpaidHoldScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnpaidHoldScheduler.class);

    private final BookingRelease release;

    public UnpaidHoldScheduler(BookingRelease release) {
        this.release = release;
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    public void tick() {
        try {
            long released = release.releaseStaleUnpaidHolds();
            if (released > 0) {
                log.info("Automatically released {} stale unpaid booking hold(s)", released);
            }
        } catch (Exception error) {
            log.warn("Unpaid hold scheduler tick failed: {}", error.getMessage());
        }
    }
}
