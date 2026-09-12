package com.hotelapp.communications;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Mirrors the pure-function tests in upstream {@code communications/worker.rs}
 * and {@code communications/scheduler.rs}: retry backoff, the Feb-29 birthday
 * policy, reminder-window clamping and voucher code shape.
 */
class CommunicationsWorkerContractTest {

    // ------------------------------------------------------------------
    // worker.rs
    // ------------------------------------------------------------------

    @Test
    void backoffIsExponentialAndCapped() {
        assertEquals(2, EmailDeliveryWorker.backoffMinutes(1));
        assertEquals(4, EmailDeliveryWorker.backoffMinutes(2));
        assertEquals(16, EmailDeliveryWorker.backoffMinutes(4));
        assertEquals(60, EmailDeliveryWorker.backoffMinutes(10));
        assertEquals(60, EmailDeliveryWorker.backoffMinutes(30));
    }

    // ------------------------------------------------------------------
    // scheduler.rs
    // ------------------------------------------------------------------

    @Test
    void feb29PolicyMatchesFeb28InNonLeapYears() {
        int[][] nonLeap =
                CommunicationsScheduler.birthdayMatchPairs(LocalDate.of(2026, 2, 28));
        assertArrayEquals(new int[] {2, 28}, nonLeap[0]);
        assertArrayEquals(new int[] {2, 29}, nonLeap[1]);

        int[][] leapFeb28 =
                CommunicationsScheduler.birthdayMatchPairs(LocalDate.of(2028, 2, 28));
        assertArrayEquals(new int[] {2, 28}, leapFeb28[0]);
        assertArrayEquals(new int[] {2, 28}, leapFeb28[1]);

        int[][] leapFeb29 =
                CommunicationsScheduler.birthdayMatchPairs(LocalDate.of(2028, 2, 29));
        assertArrayEquals(new int[] {2, 29}, leapFeb29[0]);
        assertArrayEquals(new int[] {2, 29}, leapFeb29[1]);

        int[][] ordinary =
                CommunicationsScheduler.birthdayMatchPairs(LocalDate.of(2026, 7, 15));
        assertArrayEquals(new int[] {7, 15}, ordinary[0]);
        assertArrayEquals(new int[] {7, 15}, ordinary[1]);
    }

    @Test
    void centuryLeapRules() {
        assertTrue(CommunicationsScheduler.isLeapYear(2000));
        assertFalse(CommunicationsScheduler.isLeapYear(1900));
        assertTrue(CommunicationsScheduler.isLeapYear(2024));
        assertFalse(CommunicationsScheduler.isLeapYear(2026));
    }

    @Test
    void birthdayCodesHaveDistinctPrefix() {
        String code = CommunicationsScheduler.generateBirthdayVoucherCode();
        assertTrue(code.startsWith("BDY"));
        assertEquals(23, code.length());
    }

    @Test
    void reminderWindowSpansWholeDaysFromClampedHours() {
        // Default 48h -> 2 days; a week cap; tiny values still cover today.
        assertEquals(2, CommunicationsScheduler.reminderWindowDays(48));
        assertEquals(1, CommunicationsScheduler.reminderWindowDays(24));
        assertEquals(7, CommunicationsScheduler.reminderWindowDays(168));
        assertEquals(1, CommunicationsScheduler.reminderWindowDays(2));
        // Clamped: below the floor and above the ceiling.
        assertEquals(1, CommunicationsScheduler.reminderWindowDays(0));
        assertEquals(7, CommunicationsScheduler.reminderWindowDays(1_000));
    }
}
