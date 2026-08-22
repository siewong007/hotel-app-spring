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
@Table(name = "voucher_redemptions")
public class VoucherRedemptionsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "voucher_id")
    private Long voucher_id;

    @Column(name = "promotion_id")
    private Long promotion_id;

    @Column(name = "booking_id")
    private Long booking_id;

    @Column(name = "guest_id")
    private Long guest_id;

    @Column(name = "status", columnDefinition = "varchar(16)")
    private String status;

    @Column(name = "gross_subtotal", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal gross_subtotal;

    @Column(name = "discount_type", columnDefinition = "varchar(24)")
    private String discount_type;

    @Column(name = "discount_value", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal discount_value;

    @Column(name = "discount_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal discount_amount;

    @Column(name = "net_total", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal net_total;

    @Column(name = "applied_by")
    private Long applied_by;

    @Column(name = "applied_at")
    private java.time.OffsetDateTime applied_at;

    @Column(name = "reversed_by")
    private Long reversed_by;

    @Column(name = "reversed_at")
    private java.time.OffsetDateTime reversed_at;

    @Column(name = "reversal_reason", columnDefinition = "text")
    private String reversal_reason;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVoucherId() { return voucher_id; }
    public void setVoucherId(Long voucher_id) { this.voucher_id = voucher_id; }

    public Long getPromotionId() { return promotion_id; }
    public void setPromotionId(Long promotion_id) { this.promotion_id = promotion_id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public java.math.BigDecimal getGrossSubtotal() { return gross_subtotal; }
    public void setGrossSubtotal(java.math.BigDecimal gross_subtotal) { this.gross_subtotal = gross_subtotal; }

    public String getDiscountType() { return discount_type; }
    public void setDiscountType(String discount_type) { this.discount_type = discount_type; }

    public java.math.BigDecimal getDiscountValue() { return discount_value; }
    public void setDiscountValue(java.math.BigDecimal discount_value) { this.discount_value = discount_value; }

    public java.math.BigDecimal getDiscountAmount() { return discount_amount; }
    public void setDiscountAmount(java.math.BigDecimal discount_amount) { this.discount_amount = discount_amount; }

    public java.math.BigDecimal getNetTotal() { return net_total; }
    public void setNetTotal(java.math.BigDecimal net_total) { this.net_total = net_total; }

    public Long getAppliedBy() { return applied_by; }
    public void setAppliedBy(Long applied_by) { this.applied_by = applied_by; }

    public java.time.OffsetDateTime getAppliedAt() { return applied_at; }
    public void setAppliedAt(java.time.OffsetDateTime applied_at) { this.applied_at = applied_at; }

    public Long getReversedBy() { return reversed_by; }
    public void setReversedBy(Long reversed_by) { this.reversed_by = reversed_by; }

    public java.time.OffsetDateTime getReversedAt() { return reversed_at; }
    public void setReversedAt(java.time.OffsetDateTime reversed_at) { this.reversed_at = reversed_at; }

    public String getReversalReason() { return reversal_reason; }
    public void setReversalReason(String reversal_reason) { this.reversal_reason = reversal_reason; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
