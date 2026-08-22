package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "room_status_transitions")
@IdClass(RoomStatusTransitionsId.class)
public class RoomStatusTransitionsEntity {

    @Id
    @Column(name = "from_status", columnDefinition = "varchar(20)")
    private String from_status;

    @Id
    @Column(name = "to_status", columnDefinition = "varchar(20)")
    private String to_status;

    @Column(name = "is_allowed")
    private Boolean is_allowed;

    @Column(name = "requires_permission", columnDefinition = "varchar(100)")
    private String requires_permission;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    public String getFromStatus() { return from_status; }
    public void setFromStatus(String from_status) { this.from_status = from_status; }

    public String getToStatus() { return to_status; }
    public void setToStatus(String to_status) { this.to_status = to_status; }

    public Boolean isIsAllowed() { return is_allowed; }
    public void setIsAllowed(Boolean is_allowed) { this.is_allowed = is_allowed; }

    public String getRequiresPermission() { return requires_permission; }
    public void setRequiresPermission(String requires_permission) { this.requires_permission = requires_permission; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
