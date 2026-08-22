package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class OnlineInventoryAllocationsId implements Serializable {

    private Long room_type_id;
    private java.time.LocalDate stay_date;

    public Long getRoomTypeId() { return room_type_id; }
    public void setRoomTypeId(Long room_type_id) { this.room_type_id = room_type_id; }

    public java.time.LocalDate getStayDate() { return stay_date; }
    public void setStayDate(java.time.LocalDate stay_date) { this.stay_date = stay_date; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OnlineInventoryAllocationsId)) return false;
        OnlineInventoryAllocationsId other = (OnlineInventoryAllocationsId) o;
        return Objects.equals(this.room_type_id, other.room_type_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRoomTypeId(), getStayDate());
    }
}
