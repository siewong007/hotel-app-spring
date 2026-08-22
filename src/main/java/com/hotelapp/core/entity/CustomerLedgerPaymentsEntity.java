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
@Table(name = "customer_ledger_payments")
public class CustomerLedgerPaymentsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "ledger_id", columnDefinition = "bigint")
    private Long ledger_id;

    @Column(name = "payment_amount", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal payment_amount;

    @Column(name = "payment_method", columnDefinition = "varchar(50)")
    private String payment_method;

    @Column(name = "payment_reference", columnDefinition = "varchar(255)")
    private String payment_reference;

    @Column(name = "payment_date", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime payment_date;

    @Column(name = "receipt_number", columnDefinition = "varchar(100)")
    private String receipt_number;

    @Column(name = "receipt_file_url", columnDefinition = "varchar(500)")
    private String receipt_file_url;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "processed_by", columnDefinition = "bigint")
    private Long processed_by;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "idempotency_key", columnDefinition = "varchar(160)")
    private String idempotency_key;

    @Column(name = "idempotency_fingerprint", columnDefinition = "varchar(64)")
    private String idempotency_fingerprint;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getLedgerId() { return ledger_id; }
    public void setLedgerId(Long ledger_id) { this.ledger_id = ledger_id; }

    public java.math.BigDecimal getPaymentAmount() { return payment_amount; }
    public void setPaymentAmount(java.math.BigDecimal payment_amount) { this.payment_amount = payment_amount; }

    public String getPaymentMethod() { return payment_method; }
    public void setPaymentMethod(String payment_method) { this.payment_method = payment_method; }

    public String getPaymentReference() { return payment_reference; }
    public void setPaymentReference(String payment_reference) { this.payment_reference = payment_reference; }

    public java.time.OffsetDateTime getPaymentDate() { return payment_date; }
    public void setPaymentDate(java.time.OffsetDateTime payment_date) { this.payment_date = payment_date; }

    public String getReceiptNumber() { return receipt_number; }
    public void setReceiptNumber(String receipt_number) { this.receipt_number = receipt_number; }

    public String getReceiptFileUrl() { return receipt_file_url; }
    public void setReceiptFileUrl(String receipt_file_url) { this.receipt_file_url = receipt_file_url; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getProcessedBy() { return processed_by; }
    public void setProcessedBy(Long processed_by) { this.processed_by = processed_by; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public String getIdempotencyKey() { return idempotency_key; }
    public void setIdempotencyKey(String idempotency_key) { this.idempotency_key = idempotency_key; }

    public String getIdempotencyFingerprint() { return idempotency_fingerprint; }
    public void setIdempotencyFingerprint(String idempotency_fingerprint) { this.idempotency_fingerprint = idempotency_fingerprint; }
}
