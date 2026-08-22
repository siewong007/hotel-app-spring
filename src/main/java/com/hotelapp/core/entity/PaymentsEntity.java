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
@Table(name = "payments",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"uuid"})})
public class PaymentsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid")
    private java.util.UUID uuid;

    @Column(name = "booking_id")
    private Long booking_id;

    @Column(name = "amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal amount;

    @Column(name = "currency", columnDefinition = "varchar(3)")
    private String currency;

    @Column(name = "payment_method", columnDefinition = "varchar(50)")
    private String payment_method;

    @Column(name = "payment_type", columnDefinition = "varchar(20)")
    private String payment_type;

    @Column(name = "transaction_id", columnDefinition = "varchar(255)")
    private String transaction_id;

    @Column(name = "card_last_four", columnDefinition = "varchar(4)")
    private String card_last_four;

    @Column(name = "card_brand", columnDefinition = "varchar(20)")
    private String card_brand;

    @Column(name = "payment_gateway", columnDefinition = "varchar(50)")
    private String payment_gateway;

    @Column(name = "gateway_customer_id", columnDefinition = "varchar(255)")
    private String gateway_customer_id;

    @Column(name = "gateway_payment_intent_id", columnDefinition = "varchar(255)")
    private String gateway_payment_intent_id;

    @Column(name = "gateway_charge_id", columnDefinition = "varchar(255)")
    private String gateway_charge_id;

    @Column(name = "status", columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failure_reason;

    @Column(name = "refund_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal refund_amount;

    @Column(name = "refunded_at")
    private java.time.OffsetDateTime refunded_at;

    @Column(name = "refund_reason", columnDefinition = "text")
    private String refund_reason;

    @Column(name = "gateway_refund_id", columnDefinition = "varchar(255)")
    private String gateway_refund_id;

    @Column(name = "metadata")
    private String metadata;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "receipt_url", columnDefinition = "text")
    private String receipt_url;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "created_by")
    private Long created_by;

    @Column(name = "processed_at")
    private java.time.OffsetDateTime processed_at;

    @Column(name = "processed_by")
    private Long processed_by;

    @Column(name = "idempotency_key", columnDefinition = "varchar(160)")
    private String idempotency_key;

    @Column(name = "idempotency_fingerprint", columnDefinition = "varchar(64)")
    private String idempotency_fingerprint;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.util.UUID getUuid() { return uuid; }
    public void setUuid(java.util.UUID uuid) { this.uuid = uuid; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public java.math.BigDecimal getAmount() { return amount; }
    public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentMethod() { return payment_method; }
    public void setPaymentMethod(String payment_method) { this.payment_method = payment_method; }

    public String getPaymentType() { return payment_type; }
    public void setPaymentType(String payment_type) { this.payment_type = payment_type; }

    public String getTransactionId() { return transaction_id; }
    public void setTransactionId(String transaction_id) { this.transaction_id = transaction_id; }

    public String getCardLastFour() { return card_last_four; }
    public void setCardLastFour(String card_last_four) { this.card_last_four = card_last_four; }

    public String getCardBrand() { return card_brand; }
    public void setCardBrand(String card_brand) { this.card_brand = card_brand; }

    public String getPaymentGateway() { return payment_gateway; }
    public void setPaymentGateway(String payment_gateway) { this.payment_gateway = payment_gateway; }

    public String getGatewayCustomerId() { return gateway_customer_id; }
    public void setGatewayCustomerId(String gateway_customer_id) { this.gateway_customer_id = gateway_customer_id; }

    public String getGatewayPaymentIntentId() { return gateway_payment_intent_id; }
    public void setGatewayPaymentIntentId(String gateway_payment_intent_id) { this.gateway_payment_intent_id = gateway_payment_intent_id; }

    public String getGatewayChargeId() { return gateway_charge_id; }
    public void setGatewayChargeId(String gateway_charge_id) { this.gateway_charge_id = gateway_charge_id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFailureReason() { return failure_reason; }
    public void setFailureReason(String failure_reason) { this.failure_reason = failure_reason; }

    public java.math.BigDecimal getRefundAmount() { return refund_amount; }
    public void setRefundAmount(java.math.BigDecimal refund_amount) { this.refund_amount = refund_amount; }

    public java.time.OffsetDateTime getRefundedAt() { return refunded_at; }
    public void setRefundedAt(java.time.OffsetDateTime refunded_at) { this.refunded_at = refunded_at; }

    public String getRefundReason() { return refund_reason; }
    public void setRefundReason(String refund_reason) { this.refund_reason = refund_reason; }

    public String getGatewayRefundId() { return gateway_refund_id; }
    public void setGatewayRefundId(String gateway_refund_id) { this.gateway_refund_id = gateway_refund_id; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getReceiptUrl() { return receipt_url; }
    public void setReceiptUrl(String receipt_url) { this.receipt_url = receipt_url; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public java.time.OffsetDateTime getProcessedAt() { return processed_at; }
    public void setProcessedAt(java.time.OffsetDateTime processed_at) { this.processed_at = processed_at; }

    public Long getProcessedBy() { return processed_by; }
    public void setProcessedBy(Long processed_by) { this.processed_by = processed_by; }

    public String getIdempotencyKey() { return idempotency_key; }
    public void setIdempotencyKey(String idempotency_key) { this.idempotency_key = idempotency_key; }

    public String getIdempotencyFingerprint() { return idempotency_fingerprint; }
    public void setIdempotencyFingerprint(String idempotency_fingerprint) { this.idempotency_fingerprint = idempotency_fingerprint; }
}
