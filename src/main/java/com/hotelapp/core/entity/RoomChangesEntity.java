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
@Table(name = "room_changes")
public class RoomChangesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "booking_id", columnDefinition = "bigint")
    private Long booking_id;

    @Column(name = "from_room_id", columnDefinition = "bigint")
    private Long from_room_id;

    @Column(name = "to_room_id", columnDefinition = "bigint")
    private Long to_room_id;

    @Column(name = "guest_id", columnDefinition = "bigint")
    private Long guest_id;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "changed_by", columnDefinition = "bigint")
    private Long changed_by;

    @Column(name = "changed_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime changed_at;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public Long getFromRoomId() { return from_room_id; }
    public void setFromRoomId(Long from_room_id) { this.from_room_id = from_room_id; }

    public Long getToRoomId() { return to_room_id; }
    public void setToRoomId(Long to_room_id) { this.to_room_id = to_room_id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getChangedBy() { return changed_by; }
    public void setChangedBy(Long changed_by) { this.changed_by = changed_by; }

    public java.time.OffsetDateTime getChangedAt() { return changed_at; }
    public void setChangedAt(java.time.OffsetDateTime changed_at) { this.changed_at = changed_at; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
