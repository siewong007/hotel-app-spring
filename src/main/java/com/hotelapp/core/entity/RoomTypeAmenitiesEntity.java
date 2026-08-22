package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "room_type_amenities")
@IdClass(RoomTypeAmenitiesId.class)
public class RoomTypeAmenitiesEntity {

    @Id
    @Column(name = "room_type_id", columnDefinition = "bigint")
    private Long room_type_id;

    @Id
    @Column(name = "amenity_id", columnDefinition = "bigint")
    private Long amenity_id;

    @Column(name = "is_complimentary", columnDefinition = "boolean DEFAULT true")
    private Boolean is_complimentary;

    public Long getRoomTypeId() { return room_type_id; }
    public void setRoomTypeId(Long room_type_id) { this.room_type_id = room_type_id; }

    public Long getAmenityId() { return amenity_id; }
    public void setAmenityId(Long amenity_id) { this.amenity_id = amenity_id; }

    public Boolean isIsComplimentary() { return is_complimentary; }
    public void setIsComplimentary(Boolean is_complimentary) { this.is_complimentary = is_complimentary; }
}
