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
@Table(name = "night_audit_runs",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"audit_date"})})
public class NightAuditRunsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "audit_date", columnDefinition = "date")
    private java.time.LocalDate audit_date;

    @Column(name = "run_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime run_at;

    @Column(name = "run_by", columnDefinition = "bigint")
    private Long run_by;

    @Column(name = "status", columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "total_bookings_posted", columnDefinition = "integer DEFAULT 0")
    private Integer total_bookings_posted;

    @Column(name = "total_checkins", columnDefinition = "integer DEFAULT 0")
    private Integer total_checkins;

    @Column(name = "total_checkouts", columnDefinition = "integer DEFAULT 0")
    private Integer total_checkouts;

    @Column(name = "total_revenue", columnDefinition = "numeric(12,2) DEFAULT 0")
    private java.math.BigDecimal total_revenue;

    @Column(name = "total_rooms_occupied", columnDefinition = "integer DEFAULT 0")
    private Integer total_rooms_occupied;

    @Column(name = "total_rooms_available", columnDefinition = "integer DEFAULT 0")
    private Integer total_rooms_available;

    @Column(name = "occupancy_rate", columnDefinition = "numeric(5,2) DEFAULT 0")
    private java.math.BigDecimal occupancy_rate;

    @Column(name = "rooms_available", columnDefinition = "integer DEFAULT 0")
    private Integer rooms_available;

    @Column(name = "rooms_occupied", columnDefinition = "integer DEFAULT 0")
    private Integer rooms_occupied;

    @Column(name = "rooms_reserved", columnDefinition = "integer DEFAULT 0")
    private Integer rooms_reserved;

    @Column(name = "rooms_maintenance", columnDefinition = "integer DEFAULT 0")
    private Integer rooms_maintenance;

    @Column(name = "rooms_dirty", columnDefinition = "integer DEFAULT 0")
    private Integer rooms_dirty;

    @Column(name = "payment_method_breakdown", columnDefinition = "jsonb")
    private String payment_method_breakdown;

    @Column(name = "booking_channel_breakdown", columnDefinition = "jsonb")
    private String booking_channel_breakdown;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "error_message", columnDefinition = "text")
    private String error_message;

    @Column(name = "created_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.time.LocalDate getAuditDate() { return audit_date; }
    public void setAuditDate(java.time.LocalDate audit_date) { this.audit_date = audit_date; }

    public java.time.OffsetDateTime getRunAt() { return run_at; }
    public void setRunAt(java.time.OffsetDateTime run_at) { this.run_at = run_at; }

    public Long getRunBy() { return run_by; }
    public void setRunBy(Long run_by) { this.run_by = run_by; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTotalBookingsPosted() { return total_bookings_posted; }
    public void setTotalBookingsPosted(Integer total_bookings_posted) { this.total_bookings_posted = total_bookings_posted; }

    public Integer getTotalCheckins() { return total_checkins; }
    public void setTotalCheckins(Integer total_checkins) { this.total_checkins = total_checkins; }

    public Integer getTotalCheckouts() { return total_checkouts; }
    public void setTotalCheckouts(Integer total_checkouts) { this.total_checkouts = total_checkouts; }

    public java.math.BigDecimal getTotalRevenue() { return total_revenue; }
    public void setTotalRevenue(java.math.BigDecimal total_revenue) { this.total_revenue = total_revenue; }

    public Integer getTotalRoomsOccupied() { return total_rooms_occupied; }
    public void setTotalRoomsOccupied(Integer total_rooms_occupied) { this.total_rooms_occupied = total_rooms_occupied; }

    public Integer getTotalRoomsAvailable() { return total_rooms_available; }
    public void setTotalRoomsAvailable(Integer total_rooms_available) { this.total_rooms_available = total_rooms_available; }

    public java.math.BigDecimal getOccupancyRate() { return occupancy_rate; }
    public void setOccupancyRate(java.math.BigDecimal occupancy_rate) { this.occupancy_rate = occupancy_rate; }

    public Integer getRoomsAvailable() { return rooms_available; }
    public void setRoomsAvailable(Integer rooms_available) { this.rooms_available = rooms_available; }

    public Integer getRoomsOccupied() { return rooms_occupied; }
    public void setRoomsOccupied(Integer rooms_occupied) { this.rooms_occupied = rooms_occupied; }

    public Integer getRoomsReserved() { return rooms_reserved; }
    public void setRoomsReserved(Integer rooms_reserved) { this.rooms_reserved = rooms_reserved; }

    public Integer getRoomsMaintenance() { return rooms_maintenance; }
    public void setRoomsMaintenance(Integer rooms_maintenance) { this.rooms_maintenance = rooms_maintenance; }

    public Integer getRoomsDirty() { return rooms_dirty; }
    public void setRoomsDirty(Integer rooms_dirty) { this.rooms_dirty = rooms_dirty; }

    public String getPaymentMethodBreakdown() { return payment_method_breakdown; }
    public void setPaymentMethodBreakdown(String payment_method_breakdown) { this.payment_method_breakdown = payment_method_breakdown; }

    public String getBookingChannelBreakdown() { return booking_channel_breakdown; }
    public void setBookingChannelBreakdown(String booking_channel_breakdown) { this.booking_channel_breakdown = booking_channel_breakdown; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getErrorMessage() { return error_message; }
    public void setErrorMessage(String error_message) { this.error_message = error_message; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
