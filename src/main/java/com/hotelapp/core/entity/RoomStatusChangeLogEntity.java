package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "room_status_change_log")
public class RoomStatusChangeLogEntity {

    @Id
    @Column(name = "id")
    private java.util.UUID id;

    @Column(name = "room_id")
    private Long room_id;

    @Column(name = "from_status", columnDefinition = "varchar(20)")
    private String from_status;

    @Column(name = "to_status", columnDefinition = "varchar(20)")
    private String to_status;

    @Column(name = "trigger_source", columnDefinition = "varchar(100)")
    private String trigger_source;

    @Column(name = "booking_id")
    private Long booking_id;

    @Column(name = "was_blocked")
    private Boolean was_blocked;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }

    public Long getRoomId() { return room_id; }
    public void setRoomId(Long room_id) { this.room_id = room_id; }

    public String getFromStatus() { return from_status; }
    public void setFromStatus(String from_status) { this.from_status = from_status; }

    public String getToStatus() { return to_status; }
    public void setToStatus(String to_status) { this.to_status = to_status; }

    public String getTriggerSource() { return trigger_source; }
    public void setTriggerSource(String trigger_source) { this.trigger_source = trigger_source; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public Boolean isWasBlocked() { return was_blocked; }
    public void setWasBlocked(Boolean was_blocked) { this.was_blocked = was_blocked; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
