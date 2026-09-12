package com.hotelapp.communications;

import com.hotelapp.core.audit.AuditWriter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction-scoped writes for {@code modules/communications/scheduler.rs}:
 * one {@code pool.begin()} per queued delivery and one per issued birthday
 * voucher (voucher + audit + queued email atomically).
 */
@Component
public class CommunicationsSchedulerTx {

    /** Column values for one queued outbound delivery (upstream DeliveryValues). */
    public record DeliveryValues(
            Long campaignId,
            String kind,
            long guestId,
            String topic,
            String recipientEmail,
            String subject,
            String bodyHtml,
            String bodyText,
            Long voucherId,
            String idempotencyKey) {
    }

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public CommunicationsSchedulerTx(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /**
     * {@code insert_delivery_tx}: enqueue one delivery. Returns null when a
     * row with the same idempotency key already exists (duplicate suppressed).
     */
    @Transactional
    public Long insertDelivery(DeliveryValues values) {
        List<Long> ids = jdbc.query("""
                INSERT INTO email_deliveries
                    (campaign_id, kind, guest_id, topic, recipient_email, subject,
                     body_html, body_text, voucher_id, idempotency_key)
                VALUES (?, ?, ?, ?, LOWER(?), ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING id
                """, (rs, i) -> rs.getLong(1),
                values.campaignId(), values.kind(), values.guestId(), values.topic(),
                values.recipientEmail(), values.subject(), values.bodyHtml(),
                values.bodyText(), values.voucherId(), values.idempotencyKey());
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * The birthday issue bundle, one transaction per guest: voucher + audit +
     * queued email. Returns the voucher id, or null on a uniqueness conflict
     * (nothing written — the commit still lands an empty tx like upstream).
     */
    @Transactional
    public Long issueBirthdayVoucher(long promotionId, long guestId, String code,
            OffsetDateTime expiresAt, String sourceReference, DeliveryValues delivery) {
        List<Long> voucherIds = jdbc.query("""
                INSERT INTO vouchers
                    (promotion_id, guest_id, code, status, source, expires_at, source_reference)
                VALUES (?, ?, ?, 'available', 'admin_issue', ?, ?)
                ON CONFLICT DO NOTHING
                RETURNING id
                """, (rs, i) -> rs.getLong(1),
                promotionId, guestId, code, expiresAt, sourceReference);
        if (voucherIds.isEmpty()) {
            return null;
        }
        Long voucherId = voucherIds.get(0);
        audit.event(null, "voucher.birthday_issued", "voucher", voucherId,
                Map.of("guest_id", guestId,
                        "promotion_id", promotionId,
                        "source_reference", sourceReference));
        jdbc.update("""
                INSERT INTO email_deliveries
                    (campaign_id, kind, guest_id, topic, recipient_email, subject,
                     body_html, body_text, voucher_id, idempotency_key)
                VALUES (?, ?, ?, ?, LOWER(?), ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                delivery.campaignId(), delivery.kind(), delivery.guestId(), delivery.topic(),
                delivery.recipientEmail(), delivery.subject(), delivery.bodyHtml(),
                delivery.bodyText(), voucherId, delivery.idempotencyKey());
        return voucherId;
    }
}
