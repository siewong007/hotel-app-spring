package com.hotelapp.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Port of {@code services::payment_receipt_scheduler} — reject overdue
 * bank-transfer receipts and unstarted PayPal attempts every 60 seconds.
 */
@Component
public class PaymentsSweep {

    private static final Logger log = LoggerFactory.getLogger(PaymentsSweep.class);

    private final StaffPayments payments;

    public PaymentsSweep(StaffPayments payments) {
        this.payments = payments;
    }

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        try {
            int rejected = payments.rejectExpiredReceiptRequests();
            if (rejected > 0) {
                log.info("Automatically rejected {} payment claim(s) with overdue "
                        + "receipt requests", rejected);
            }
            int expiredPaypal = payments.rejectExpiredPaypalAttempts();
            if (expiredPaypal > 0) {
                log.info("Automatically released {} stale PayPal payment attempt(s)",
                        expiredPaypal);
            }
        } catch (Exception e) {
            log.error("payment receipt sweep failed: {}", e.getMessage());
        }
    }
}
