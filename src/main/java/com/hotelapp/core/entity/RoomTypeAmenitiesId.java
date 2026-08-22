package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class RoomTypeAmenitiesId implements Serializable {

    private Long room_type_id;
    private Long amenity_id;

    public Long getRoomTypeId() { return room_type_id; }
    public void setRoomTypeId(Long room_type_id) { this.room_type_id = room_type_id; }

    public Long getAmenityId() { return amenity_id; }
    public void setAmenityId(Long amenity_id) { this.amenity_id = amenity_id; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomTypeAmenitiesId)) return false;
        RoomTypeAmenitiesId other = (RoomTypeAmenitiesId) o;
        return Objects.equals(this.room_type_id, other.room_type_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRoomTypeId(), getAmenityId());
    }
}
