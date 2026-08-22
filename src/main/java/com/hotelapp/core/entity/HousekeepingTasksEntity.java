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
@Table(name = "housekeeping_tasks")
public class HousekeepingTasksEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "room_id", columnDefinition = "bigint")
    private Long room_id;

    @Column(name = "task_type", columnDefinition = "varchar(50)")
    private String task_type;

    @Column(name = "priority", columnDefinition = "varchar(20)")
    private String priority;

    @Column(name = "status", columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "assigned_to", columnDefinition = "bigint")
    private Long assigned_to;

    @Column(name = "scheduled_date", columnDefinition = "date")
    private java.time.LocalDate scheduled_date;

    @Column(name = "task_date", columnDefinition = "date")
    private java.time.LocalDate task_date;

    @Column(name = "started_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime started_at;

    @Column(name = "completed_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime completed_at;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "inspection_notes", columnDefinition = "text")
    private String inspection_notes;

    @Column(name = "items_used", columnDefinition = "jsonb")
    private String items_used;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long created_by;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoomId() { return room_id; }
    public void setRoomId(Long room_id) { this.room_id = room_id; }

    public String getTaskType() { return task_type; }
    public void setTaskType(String task_type) { this.task_type = task_type; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getAssignedTo() { return assigned_to; }
    public void setAssignedTo(Long assigned_to) { this.assigned_to = assigned_to; }

    public java.time.LocalDate getScheduledDate() { return scheduled_date; }
    public void setScheduledDate(java.time.LocalDate scheduled_date) { this.scheduled_date = scheduled_date; }

    public java.time.LocalDate getTaskDate() { return task_date; }
    public void setTaskDate(java.time.LocalDate task_date) { this.task_date = task_date; }

    public java.time.OffsetDateTime getStartedAt() { return started_at; }
    public void setStartedAt(java.time.OffsetDateTime started_at) { this.started_at = started_at; }

    public java.time.OffsetDateTime getCompletedAt() { return completed_at; }
    public void setCompletedAt(java.time.OffsetDateTime completed_at) { this.completed_at = completed_at; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getInspectionNotes() { return inspection_notes; }
    public void setInspectionNotes(String inspection_notes) { this.inspection_notes = inspection_notes; }

    public String getItemsUsed() { return items_used; }
    public void setItemsUsed(String items_used) { this.items_used = items_used; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
