package com.hotelapp.gaps;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * {@code PendingPaymentsQuery::limit_offset} — page >= 1, per_page 1..=100
 * (default 20).
 */
class PendingPaginationContractTest {

    @Test
    void defaultsArePageOneTwentyPerPage() {
        assertArrayEquals(new long[] {20, 0},
                AccountGapsController.limitOffset(null, null));
    }

    @Test
    void pageAndPerPageProduceLimitAndOffset() {
        assertArrayEquals(new long[] {50, 100},
                AccountGapsController.limitOffset(3L, 50L));
        assertArrayEquals(new long[] {20, 40},
                AccountGapsController.limitOffset(3L, null));
    }

    @Test
    void clampsLikeUpstream() {
        assertArrayEquals(new long[] {20, 0},
                AccountGapsController.limitOffset(0L, null));
        assertArrayEquals(new long[] {20, 0},
                AccountGapsController.limitOffset(-5L, null));
        assertArrayEquals(new long[] {1, 0},
                AccountGapsController.limitOffset(null, 0L));
        assertArrayEquals(new long[] {100, 900},
                AccountGapsController.limitOffset(10L, 500L));
    }
}
