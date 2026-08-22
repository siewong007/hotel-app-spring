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
@Table(name = "payment_receipt_requests")
public class PaymentReceiptRequestsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "payment_id")
    private Long payment_id;

    @Column(name = "requested_by")
    private Long requested_by;

    @Column(name = "request_message", columnDefinition = "text")
    private String request_message;

    @Column(name = "requested_at")
    private java.time.OffsetDateTime requested_at;

    @Column(name = "uploaded_at")
    private java.time.OffsetDateTime uploaded_at;

    @Column(name = "receipt_path", columnDefinition = "text")
    private String receipt_path;

    @Column(name = "receipt_content_type", columnDefinition = "varchar(100)")
    private String receipt_content_type;

    public Long getPaymentId() { return payment_id; }
    public void setPaymentId(Long payment_id) { this.payment_id = payment_id; }

    public Long getRequestedBy() { return requested_by; }
    public void setRequestedBy(Long requested_by) { this.requested_by = requested_by; }

    public String getRequestMessage() { return request_message; }
    public void setRequestMessage(String request_message) { this.request_message = request_message; }

    public java.time.OffsetDateTime getRequestedAt() { return requested_at; }
    public void setRequestedAt(java.time.OffsetDateTime requested_at) { this.requested_at = requested_at; }

    public java.time.OffsetDateTime getUploadedAt() { return uploaded_at; }
    public void setUploadedAt(java.time.OffsetDateTime uploaded_at) { this.uploaded_at = uploaded_at; }

    public String getReceiptPath() { return receipt_path; }
    public void setReceiptPath(String receipt_path) { this.receipt_path = receipt_path; }

    public String getReceiptContentType() { return receipt_content_type; }
    public void setReceiptContentType(String receipt_content_type) { this.receipt_content_type = receipt_content_type; }
}
