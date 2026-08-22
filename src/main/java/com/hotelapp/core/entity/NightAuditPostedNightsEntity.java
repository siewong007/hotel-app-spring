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
@Table(name = "night_audit_posted_nights",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"booking_id", "audit_date"})})
public class NightAuditPostedNightsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "booking_id", columnDefinition = "bigint")
    private Long booking_id;

    @Column(name = "audit_date", columnDefinition = "date")
    private java.time.LocalDate audit_date;

    @Column(name = "room_rate", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal room_rate;

    @Column(name = "room_charge", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal room_charge;

    @Column(name = "service_tax", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal service_tax;

    @Column(name = "tourism_tax", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal tourism_tax;

    @Column(name = "extra_bed_charge", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal extra_bed_charge;

    @Column(name = "extra_bed_tax", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal extra_bed_tax;

    @Column(name = "total_posted", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal total_posted;

    @Column(name = "audit_run_id", columnDefinition = "bigint")
    private Long audit_run_id;

    @Column(name = "posted_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime posted_at;

    @Column(name = "posted_by", columnDefinition = "bigint")
    private Long posted_by;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public java.time.LocalDate getAuditDate() { return audit_date; }
    public void setAuditDate(java.time.LocalDate audit_date) { this.audit_date = audit_date; }

    public java.math.BigDecimal getRoomRate() { return room_rate; }
    public void setRoomRate(java.math.BigDecimal room_rate) { this.room_rate = room_rate; }

    public java.math.BigDecimal getRoomCharge() { return room_charge; }
    public void setRoomCharge(java.math.BigDecimal room_charge) { this.room_charge = room_charge; }

    public java.math.BigDecimal getServiceTax() { return service_tax; }
    public void setServiceTax(java.math.BigDecimal service_tax) { this.service_tax = service_tax; }

    public java.math.BigDecimal getTourismTax() { return tourism_tax; }
    public void setTourismTax(java.math.BigDecimal tourism_tax) { this.tourism_tax = tourism_tax; }

    public java.math.BigDecimal getExtraBedCharge() { return extra_bed_charge; }
    public void setExtraBedCharge(java.math.BigDecimal extra_bed_charge) { this.extra_bed_charge = extra_bed_charge; }

    public java.math.BigDecimal getExtraBedTax() { return extra_bed_tax; }
    public void setExtraBedTax(java.math.BigDecimal extra_bed_tax) { this.extra_bed_tax = extra_bed_tax; }

    public java.math.BigDecimal getTotalPosted() { return total_posted; }
    public void setTotalPosted(java.math.BigDecimal total_posted) { this.total_posted = total_posted; }

    public Long getAuditRunId() { return audit_run_id; }
    public void setAuditRunId(Long audit_run_id) { this.audit_run_id = audit_run_id; }

    public java.time.OffsetDateTime getPostedAt() { return posted_at; }
    public void setPostedAt(java.time.OffsetDateTime posted_at) { this.posted_at = posted_at; }

    public Long getPostedBy() { return posted_by; }
    public void setPostedBy(Long posted_by) { this.posted_by = posted_by; }
}
