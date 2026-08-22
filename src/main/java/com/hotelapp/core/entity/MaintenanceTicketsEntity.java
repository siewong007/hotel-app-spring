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
@Table(name = "maintenance_tickets",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"ticket_number"})})
public class MaintenanceTicketsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "room_id", columnDefinition = "bigint")
    private Long room_id;

    @Column(name = "ticket_number", columnDefinition = "varchar(50)")
    private String ticket_number;

    @Column(name = "title", columnDefinition = "varchar(255)")
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "category", columnDefinition = "varchar(50)")
    private String category;

    @Column(name = "priority", columnDefinition = "varchar(20)")
    private String priority;

    @Column(name = "status", columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "assigned_to", columnDefinition = "bigint")
    private Long assigned_to;

    @Column(name = "reported_by", columnDefinition = "bigint")
    private Long reported_by;

    @Column(name = "estimated_cost", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal estimated_cost;

    @Column(name = "actual_cost", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal actual_cost;

    @Column(name = "estimated_hours", columnDefinition = "numeric(5,2)")
    private java.math.BigDecimal estimated_hours;

    @Column(name = "actual_hours", columnDefinition = "numeric(5,2)")
    private java.math.BigDecimal actual_hours;

    @Column(name = "scheduled_date", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime scheduled_date;

    @Column(name = "started_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime started_at;

    @Column(name = "resolved_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime resolved_at;

    @Column(name = "resolution_notes", columnDefinition = "text")
    private String resolution_notes;

    @Column(name = "images", columnDefinition = "jsonb")
    private String images;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoomId() { return room_id; }
    public void setRoomId(Long room_id) { this.room_id = room_id; }

    public String getTicketNumber() { return ticket_number; }
    public void setTicketNumber(String ticket_number) { this.ticket_number = ticket_number; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getAssignedTo() { return assigned_to; }
    public void setAssignedTo(Long assigned_to) { this.assigned_to = assigned_to; }

    public Long getReportedBy() { return reported_by; }
    public void setReportedBy(Long reported_by) { this.reported_by = reported_by; }

    public java.math.BigDecimal getEstimatedCost() { return estimated_cost; }
    public void setEstimatedCost(java.math.BigDecimal estimated_cost) { this.estimated_cost = estimated_cost; }

    public java.math.BigDecimal getActualCost() { return actual_cost; }
    public void setActualCost(java.math.BigDecimal actual_cost) { this.actual_cost = actual_cost; }

    public java.math.BigDecimal getEstimatedHours() { return estimated_hours; }
    public void setEstimatedHours(java.math.BigDecimal estimated_hours) { this.estimated_hours = estimated_hours; }

    public java.math.BigDecimal getActualHours() { return actual_hours; }
    public void setActualHours(java.math.BigDecimal actual_hours) { this.actual_hours = actual_hours; }

    public java.time.OffsetDateTime getScheduledDate() { return scheduled_date; }
    public void setScheduledDate(java.time.OffsetDateTime scheduled_date) { this.scheduled_date = scheduled_date; }

    public java.time.OffsetDateTime getStartedAt() { return started_at; }
    public void setStartedAt(java.time.OffsetDateTime started_at) { this.started_at = started_at; }

    public java.time.OffsetDateTime getResolvedAt() { return resolved_at; }
    public void setResolvedAt(java.time.OffsetDateTime resolved_at) { this.resolved_at = resolved_at; }

    public String getResolutionNotes() { return resolution_notes; }
    public void setResolutionNotes(String resolution_notes) { this.resolution_notes = resolution_notes; }

    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
