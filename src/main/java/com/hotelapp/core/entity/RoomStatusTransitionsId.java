package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class RoomStatusTransitionsId implements Serializable {

    private String from_status;
    private String to_status;

    public String getFromStatus() { return from_status; }
    public void setFromStatus(String from_status) { this.from_status = from_status; }

    public String getToStatus() { return to_status; }
    public void setToStatus(String to_status) { this.to_status = to_status; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomStatusTransitionsId)) return false;
        RoomStatusTransitionsId other = (RoomStatusTransitionsId) o;
        return Objects.equals(this.from_status, other.from_status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFromStatus(), getToStatus());
    }
}
