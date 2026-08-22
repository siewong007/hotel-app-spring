package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "promotion_room_types")
@IdClass(PromotionRoomTypesId.class)
public class PromotionRoomTypesEntity {

    @Id
    @Column(name = "promotion_id")
    private Long promotion_id;

    @Id
    @Column(name = "room_type_id")
    private Long room_type_id;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    public Long getPromotionId() { return promotion_id; }
    public void setPromotionId(Long promotion_id) { this.promotion_id = promotion_id; }

    public Long getRoomTypeId() { return room_type_id; }
    public void setRoomTypeId(Long room_type_id) { this.room_type_id = room_type_id; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
