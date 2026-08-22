package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "email_deliveries",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"idempotency_key"})})
public class EmailDeliveriesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "campaign_id")
    private Long campaign_id;

    @Column(name = "kind", columnDefinition = "varchar(20)")
    private String kind;

    @Column(name = "guest_id")
    private Long guest_id;

    @Column(name = "topic", columnDefinition = "varchar(32)")
    private String topic;

    @Column(name = "recipient_email", columnDefinition = "varchar(255)")
    private String recipient_email;

    @Column(name = "subject", columnDefinition = "varchar(255)")
    private String subject;

    @Column(name = "body_html", columnDefinition = "text")
    private String body_html;

    @Column(name = "body_text", columnDefinition = "text")
    private String body_text;

    @Column(name = "voucher_id")
    private Long voucher_id;

    @Column(name = "status", columnDefinition = "varchar(16)")
    private String status;

    @Column(name = "attempts")
    private Integer attempts;

    @Column(name = "max_attempts")
    private Integer max_attempts;

    @Column(name = "next_attempt_at")
    private java.time.OffsetDateTime next_attempt_at;

    @Column(name = "lease_owner", columnDefinition = "varchar(64)")
    private String lease_owner;

    @Column(name = "lease_expires_at")
    private java.time.OffsetDateTime lease_expires_at;

    @Column(name = "provider_message_id", columnDefinition = "varchar(255)")
    private String provider_message_id;

    @Column(name = "idempotency_key", columnDefinition = "varchar(160)")
    private String idempotency_key;

    @Column(name = "last_error", columnDefinition = "text")
    private String last_error;

    @Column(name = "sent_at")
    private java.time.OffsetDateTime sent_at;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCampaignId() { return campaign_id; }
    public void setCampaignId(Long campaign_id) { this.campaign_id = campaign_id; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getRecipientEmail() { return recipient_email; }
    public void setRecipientEmail(String recipient_email) { this.recipient_email = recipient_email; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBodyHtml() { return body_html; }
    public void setBodyHtml(String body_html) { this.body_html = body_html; }

    public String getBodyText() { return body_text; }
    public void setBodyText(String body_text) { this.body_text = body_text; }

    public Long getVoucherId() { return voucher_id; }
    public void setVoucherId(Long voucher_id) { this.voucher_id = voucher_id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public Integer getMaxAttempts() { return max_attempts; }
    public void setMaxAttempts(Integer max_attempts) { this.max_attempts = max_attempts; }

    public java.time.OffsetDateTime getNextAttemptAt() { return next_attempt_at; }
    public void setNextAttemptAt(java.time.OffsetDateTime next_attempt_at) { this.next_attempt_at = next_attempt_at; }

    public String getLeaseOwner() { return lease_owner; }
    public void setLeaseOwner(String lease_owner) { this.lease_owner = lease_owner; }

    public java.time.OffsetDateTime getLeaseExpiresAt() { return lease_expires_at; }
    public void setLeaseExpiresAt(java.time.OffsetDateTime lease_expires_at) { this.lease_expires_at = lease_expires_at; }

    public String getProviderMessageId() { return provider_message_id; }
    public void setProviderMessageId(String provider_message_id) { this.provider_message_id = provider_message_id; }

    public String getIdempotencyKey() { return idempotency_key; }
    public void setIdempotencyKey(String idempotency_key) { this.idempotency_key = idempotency_key; }

    public String getLastError() { return last_error; }
    public void setLastError(String last_error) { this.last_error = last_error; }

    public java.time.OffsetDateTime getSentAt() { return sent_at; }
    public void setSentAt(java.time.OffsetDateTime sent_at) { this.sent_at = sent_at; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
