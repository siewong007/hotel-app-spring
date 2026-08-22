package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class PromotionRoomTypesId implements Serializable {

    private Long promotion_id;
    private Long room_type_id;

    public Long getPromotionId() { return promotion_id; }
    public void setPromotionId(Long promotion_id) { this.promotion_id = promotion_id; }

    public Long getRoomTypeId() { return room_type_id; }
    public void setRoomTypeId(Long room_type_id) { this.room_type_id = room_type_id; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PromotionRoomTypesId)) return false;
        PromotionRoomTypesId other = (PromotionRoomTypesId) o;
        return Objects.equals(this.promotion_id, other.promotion_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPromotionId(), getRoomTypeId());
    }
}
