package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "booking_services")
public class BookingServicesEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private java.util.UUID id;

    @Column(name = "booking_id", columnDefinition = "bigint")
    private Long booking_id;

    @Column(name = "service_id", columnDefinition = "bigint")
    private Long service_id;

    @Column(name = "quantity", columnDefinition = "integer")
    private Integer quantity;

    @Column(name = "unit_price", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal unit_price;

    @Column(name = "total_price", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal total_price;

    @Column(name = "service_date", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime service_date;

    @Column(name = "status", columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "delivered_by", columnDefinition = "bigint")
    private Long delivered_by;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long created_by;

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public Long getServiceId() { return service_id; }
    public void setServiceId(Long service_id) { this.service_id = service_id; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public java.math.BigDecimal getUnitPrice() { return unit_price; }
    public void setUnitPrice(java.math.BigDecimal unit_price) { this.unit_price = unit_price; }

    public java.math.BigDecimal getTotalPrice() { return total_price; }
    public void setTotalPrice(java.math.BigDecimal total_price) { this.total_price = total_price; }

    public java.time.OffsetDateTime getServiceDate() { return service_date; }
    public void setServiceDate(java.time.OffsetDateTime service_date) { this.service_date = service_date; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getDeliveredBy() { return delivered_by; }
    public void setDeliveredBy(Long delivered_by) { this.delivered_by = delivered_by; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }
}
