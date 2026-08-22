package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "online_inventory_allocations")
@IdClass(OnlineInventoryAllocationsId.class)
public class OnlineInventoryAllocationsEntity {

    @Id
    @Column(name = "room_type_id")
    private Long room_type_id;

    @Id
    @Column(name = "stay_date")
    private java.time.LocalDate stay_date;

    @Column(name = "walk_in_reserved_rooms")
    private Integer walk_in_reserved_rooms;

    @Column(name = "online_booking_enabled")
    private Boolean online_booking_enabled;

    @Column(name = "custom_price", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal custom_price;

    @Column(name = "updated_by")
    private Long updated_by;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getRoomTypeId() { return room_type_id; }
    public void setRoomTypeId(Long room_type_id) { this.room_type_id = room_type_id; }

    public java.time.LocalDate getStayDate() { return stay_date; }
    public void setStayDate(java.time.LocalDate stay_date) { this.stay_date = stay_date; }

    public Integer getWalkInReservedRooms() { return walk_in_reserved_rooms; }
    public void setWalkInReservedRooms(Integer walk_in_reserved_rooms) { this.walk_in_reserved_rooms = walk_in_reserved_rooms; }

    public Boolean isOnlineBookingEnabled() { return online_booking_enabled; }
    public void setOnlineBookingEnabled(Boolean online_booking_enabled) { this.online_booking_enabled = online_booking_enabled; }

    public java.math.BigDecimal getCustomPrice() { return custom_price; }
    public void setCustomPrice(java.math.BigDecimal custom_price) { this.custom_price = custom_price; }

    public Long getUpdatedBy() { return updated_by; }
    public void setUpdatedBy(Long updated_by) { this.updated_by = updated_by; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
