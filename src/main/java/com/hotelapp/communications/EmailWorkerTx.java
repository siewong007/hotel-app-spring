package com.hotelapp.communications;

import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction-scoped delivery-outcome writes for
 * {@code modules/communications/worker.rs}: each mark lands in one
 * transaction together with the campaign counters it moves, mirroring
 * upstream's per-delivery {@code pool.begin()}.
 */
@Component
public class EmailWorkerTx {

    private final JdbcTemplate jdbc;

    public EmailWorkerTx(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code mark_delivery_sent_tx} + {@code add_campaign_counts_tx}(+1 sent). */
    @Transactional
    public void markSent(long deliveryId, String providerMessageId, Long campaignId) {
        jdbc.update("""
                UPDATE email_deliveries SET
                    status = 'sent', sent_at = CURRENT_TIMESTAMP,
                    provider_message_id = ?, last_error = NULL,
                    lease_owner = NULL, lease_expires_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, providerMessageId, deliveryId);
        if (campaignId != null) {
            addCampaignCounts(campaignId, 1, 0);
        }
    }

    /**
     * {@code mark_delivery_failed_tx}: {@code retryAt != null} requeues for
     * retry; {@code null} marks terminally failed (and bumps failed_count).
     */
    @Transactional
    public void markFailed(long deliveryId, String error, OffsetDateTime retryAt,
            Long campaignId) {
        if (retryAt != null) {
            jdbc.update("""
                    UPDATE email_deliveries SET
                        status = 'queued', next_attempt_at = ?, last_error = ?,
                        lease_owner = NULL, lease_expires_at = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, retryAt, error, deliveryId);
        } else {
            jdbc.update("""
                    UPDATE email_deliveries SET
                        status = 'failed', last_error = ?,
                        lease_owner = NULL, lease_expires_at = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, error, deliveryId);
            if (campaignId != null) {
                addCampaignCounts(campaignId, 0, 1);
            }
        }
    }

    /**
     * {@code mark_delivery_skipped_tx}: terminal skip without sending —
     * 'suppressed' (consent/suppression recheck failed) or 'cancelled'
     * (campaign cancelled).
     */
    @Transactional
    public void markSkipped(long deliveryId, String status, String reason) {
        jdbc.update("""
                UPDATE email_deliveries SET
                    status = ?, last_error = ?,
                    lease_owner = NULL, lease_expires_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, status, reason, deliveryId);
    }

    private void addCampaignCounts(long campaignId, int sentDelta, int failedDelta) {
        jdbc.update("""
                UPDATE email_campaigns
                SET sent_count = sent_count + ?, failed_count = failed_count + ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, sentDelta, failedDelta, campaignId);
    }
}
