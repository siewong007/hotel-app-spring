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
@Table(name = "room_rates",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"rate_plan_id", "room_type_id", "effective_from"})})
public class RoomRatesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "rate_plan_id", columnDefinition = "bigint")
    private Long rate_plan_id;

    @Column(name = "room_type_id", columnDefinition = "bigint")
    private Long room_type_id;

    @Column(name = "price", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal price;

    @Column(name = "effective_from", columnDefinition = "date")
    private java.time.LocalDate effective_from;

    @Column(name = "effective_to", columnDefinition = "date")
    private java.time.LocalDate effective_to;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRatePlanId() { return rate_plan_id; }
    public void setRatePlanId(Long rate_plan_id) { this.rate_plan_id = rate_plan_id; }

    public Long getRoomTypeId() { return room_type_id; }
    public void setRoomTypeId(Long room_type_id) { this.room_type_id = room_type_id; }

    public java.math.BigDecimal getPrice() { return price; }
    public void setPrice(java.math.BigDecimal price) { this.price = price; }

    public java.time.LocalDate getEffectiveFrom() { return effective_from; }
    public void setEffectiveFrom(java.time.LocalDate effective_from) { this.effective_from = effective_from; }

    public java.time.LocalDate getEffectiveTo() { return effective_to; }
    public void setEffectiveTo(java.time.LocalDate effective_to) { this.effective_to = effective_to; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
