package com.hotelapp.core.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** core/rate_limiter.rs — sliding-window counter contract. */
class RateLimitServiceTest {

    @Test
    void zeroLimitDeniesWithoutThrowingOnFirstRequest() {
        // Degenerate configuration guard (upstream aff1237b): maxRequests = 0
        // used to reach peekFirst().getEpochSecond() on an empty deque and NPE
        // the request task instead of denying it.
        RateLimitService limiter = new RateLimitService();
        RateLimitService.Decision decision = limiter.check(
                RateLimitService.Category.AUTH, 0, 60, "ip-1");
        assertFalse(decision.allowed());
        assertTrue(decision.retryAfterSecs() >= 1);
    }

    @Test
    void standardCategoryStillAllowsThenDeniesAtLimit() {
        RateLimitService limiter = new RateLimitService();
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.check(RateLimitService.Category.AUTH, "ip-2")
                    .allowed());
        }
        RateLimitService.Decision denied =
                limiter.check(RateLimitService.Category.AUTH, "ip-2");
        assertFalse(denied.allowed());
        assertTrue(denied.retryAfterSecs() >= 1
                && denied.retryAfterSecs() <= 60);
    }
}
