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
@Table(name = "voucher_redemption_allocations",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"redemption_id", "stay_date"})})
public class VoucherRedemptionAllocationsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "redemption_id", columnDefinition = "bigint")
    private Long redemption_id;

    @Column(name = "booking_id", columnDefinition = "bigint")
    private Long booking_id;

    @Column(name = "stay_date", columnDefinition = "date")
    private java.time.LocalDate stay_date;

    @Column(name = "gross_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal gross_amount;

    @Column(name = "discount_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal discount_amount;

    @Column(name = "net_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal net_amount;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRedemptionId() { return redemption_id; }
    public void setRedemptionId(Long redemption_id) { this.redemption_id = redemption_id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public java.time.LocalDate getStayDate() { return stay_date; }
    public void setStayDate(java.time.LocalDate stay_date) { this.stay_date = stay_date; }

    public java.math.BigDecimal getGrossAmount() { return gross_amount; }
    public void setGrossAmount(java.math.BigDecimal gross_amount) { this.gross_amount = gross_amount; }

    public java.math.BigDecimal getDiscountAmount() { return discount_amount; }
    public void setDiscountAmount(java.math.BigDecimal discount_amount) { this.discount_amount = discount_amount; }

    public java.math.BigDecimal getNetAmount() { return net_amount; }
    public void setNetAmount(java.math.BigDecimal net_amount) { this.net_amount = net_amount; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
