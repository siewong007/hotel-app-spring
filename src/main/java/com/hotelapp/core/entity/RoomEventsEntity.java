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
@Table(name = "room_events")
public class RoomEventsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "room_id", columnDefinition = "bigint")
    private Long room_id;

    @Column(name = "event_type", columnDefinition = "varchar(50)")
    private String event_type;

    @Column(name = "status", columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "priority", columnDefinition = "varchar(20)")
    private String priority;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "scheduled_date", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime scheduled_date;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long created_by;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoomId() { return room_id; }
    public void setRoomId(Long room_id) { this.room_id = room_id; }

    public String getEventType() { return event_type; }
    public void setEventType(String event_type) { this.event_type = event_type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public java.time.OffsetDateTime getScheduledDate() { return scheduled_date; }
    public void setScheduledDate(java.time.OffsetDateTime scheduled_date) { this.scheduled_date = scheduled_date; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
