package com.hotelapp.communications;

import com.hotelapp.communications.CommsModels.EmailDelivery;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Durable email delivery worker, mirroring
 * {@code modules/communications/worker.rs}: polls the {@code email_deliveries}
 * outbox, claims due rows under a database lease, and sends them through the
 * configured transport. Crash recovery is structural — a row stuck in
 * 'sending' past its lease expiry is reclaimed by the next tick. Consent and
 * suppression are rechecked immediately before every send, so a late
 * unsubscribe still prevents delivery of queued mail.
 */
@Component
public class EmailDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryWorker.class);

    private static final int DEFAULT_BATCH = 10;
    private static final int MAX_BACKOFF_MINUTES = 60;

    private final CommunicationsRepo repo;
    private final EmailWorkerTx workerTx;
    private final SmtpTransport transport;
    private final String workerId;
    private final int batch;

    public EmailDeliveryWorker(CommunicationsRepo repo, EmailWorkerTx workerTx,
            SmtpTransport transport) {
        this.repo = repo;
        this.workerTx = workerTx;
        this.transport = transport;
        this.workerId = "worker-" + java.util.UUID.randomUUID();
        this.batch = envInt("EMAIL_WORKER_BATCH", DEFAULT_BATCH);
    }

    private static int envInt(String key, int fallback) {
        try {
            String value = System.getenv(key);
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** {@code backoff_minutes}: 2^attempts minutes, capped. */
    static long backoffMinutes(int attempts) {
        int clamped = Math.max(1, Math.min(30, attempts));
        return Math.min(1L << clamped, MAX_BACKOFF_MINUTES);
    }

    /**
     * Polls every {@code EMAIL_WORKER_INTERVAL_SECS} (default 15s). Inert when
     * SMTP is not configured — upstream never spawns the loop then; queued
     * rows simply wait until a configured process starts.
     */
    @Scheduled(fixedDelayString = "${EMAIL_WORKER_INTERVAL_SECS:15}000")
    public void tick() {
        if (!transport.isConfigured()) {
            return;
        }
        try {
            List<EmailDelivery> claimed = repo.claimDueDeliveries(workerId, batch);
            if (claimed.isEmpty()) {
                return;
            }
            List<Long> campaignsTouched = new ArrayList<>();
            for (EmailDelivery delivery : claimed) {
                if (delivery.campaignId() != null && !campaignsTouched.contains(delivery.campaignId())) {
                    campaignsTouched.add(delivery.campaignId());
                }
                try {
                    processDelivery(delivery);
                } catch (Exception e) {
                    // Persisting the outcome failed; the lease will expire and
                    // the row will be reclaimed. Log without recipient details.
                    log.warn("Delivery {} outcome persistence failed: {}",
                            delivery.id(), e.getMessage());
                }
            }
            for (long campaignId : campaignsTouched) {
                if (repo.completeCampaignIfDone(campaignId)) {
                    log.info("Campaign {} completed", campaignId);
                }
            }
        } catch (Exception e) {
            log.warn("Email delivery worker tick failed: {}", e.getMessage());
        }
    }

    private void processDelivery(EmailDelivery delivery) {
        // Campaign cancelled after enqueue → drop the remaining sends.
        if (delivery.campaignId() != null) {
            var campaign = repo.getCampaign(delivery.campaignId());
            if (campaign.isPresent() && "cancelled".equals(campaign.get().status())) {
                workerTx.markSkipped(delivery.id(), "cancelled", "campaign cancelled");
                return;
            }
        }

        // Last-moment consent + suppression recheck. Transactional kinds are
        // part of the service: they need only an active guest (hard
        // suppressions below still apply), while marketing kinds additionally
        // require a live per-topic subscription.
        boolean suppressed = repo.isEmailSuppressed(delivery.recipientEmail());
        boolean transactional = !CommsValidation.requiresTopicSubscription(delivery.kind());
        boolean deliverable = transactional
                ? repo.isGuestActive(delivery.guestId())
                : repo.isGuestDeliverable(delivery.guestId(), delivery.topic());
        if (suppressed || !deliverable) {
            String reason = suppressed ? "recipient suppressed"
                    : transactional ? "guest inactive"
                    : "subscription revoked or guest inactive";
            workerTx.markSkipped(delivery.id(), "suppressed", reason);
            return;
        }

        String error = null;
        String providerMessageId = null;
        try {
            providerMessageId = transport.send(new SmtpTransport.OutgoingEmail(
                    delivery.recipientEmail(), delivery.subject(),
                    delivery.bodyHtml(), delivery.bodyText()));
        } catch (Exception e) {
            error = e.getMessage() == null ? e.toString() : e.getMessage();
        }

        if (error == null) {
            workerTx.markSent(delivery.id(), providerMessageId, delivery.campaignId());
            return;
        }
        String truncated = error.codePointCount(0, error.length()) > 500
                ? error.substring(0, error.offsetByCodePoints(0, 500)) : error;
        if (delivery.attempts() >= delivery.maxAttempts()) {
            workerTx.markFailed(delivery.id(), truncated, null, delivery.campaignId());
            log.warn("Delivery {} permanently failed after {} attempts",
                    delivery.id(), delivery.attempts());
        } else {
            OffsetDateTime retryAt = OffsetDateTime.now()
                    .plusMinutes(backoffMinutes(delivery.attempts()));
            workerTx.markFailed(delivery.id(), truncated, retryAt, null);
        }
    }
}
