package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "payment_retry_capabilities",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"token_hash"})})
public class PaymentRetryCapabilitiesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "booking_id", columnDefinition = "bigint")
    private Long booking_id;

    @Column(name = "payment_id", columnDefinition = "bigint")
    private Long payment_id;

    @Column(name = "token_hash", columnDefinition = "varchar(80)")
    private String token_hash;

    @Column(name = "expires_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime expires_at;

    @Column(name = "consumed_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime consumed_at;

    @Column(name = "replacement_payment_id", columnDefinition = "bigint")
    private Long replacement_payment_id;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public Long getPaymentId() { return payment_id; }
    public void setPaymentId(Long payment_id) { this.payment_id = payment_id; }

    public String getTokenHash() { return token_hash; }
    public void setTokenHash(String token_hash) { this.token_hash = token_hash; }

    public java.time.OffsetDateTime getExpiresAt() { return expires_at; }
    public void setExpiresAt(java.time.OffsetDateTime expires_at) { this.expires_at = expires_at; }

    public java.time.OffsetDateTime getConsumedAt() { return consumed_at; }
    public void setConsumedAt(java.time.OffsetDateTime consumed_at) { this.consumed_at = consumed_at; }

    public Long getReplacementPaymentId() { return replacement_payment_id; }
    public void setReplacementPaymentId(Long replacement_payment_id) { this.replacement_payment_id = replacement_payment_id; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
