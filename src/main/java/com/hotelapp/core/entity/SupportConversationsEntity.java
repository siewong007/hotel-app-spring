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
@Table(name = "support_conversations",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"conversation_number"})})
public class SupportConversationsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "conversation_number", columnDefinition = "varchar(40)")
    private String conversation_number;

    @Column(name = "guest_id", columnDefinition = "bigint")
    private Long guest_id;

    @Column(name = "booking_id", columnDefinition = "bigint")
    private Long booking_id;

    @Column(name = "subject", columnDefinition = "varchar(160)")
    private String subject;

    @Column(name = "category", columnDefinition = "varchar(32)")
    private String category;

    @Column(name = "status", columnDefinition = "varchar(32)")
    private String status;

    @Column(name = "priority", columnDefinition = "varchar(16)")
    private String priority;

    @Column(name = "assigned_team", columnDefinition = "varchar(64)")
    private String assigned_team;

    @Column(name = "assigned_to_user_id", columnDefinition = "bigint")
    private Long assigned_to_user_id;

    @Column(name = "escalation_level", columnDefinition = "smallint")
    private Short escalation_level;

    @Column(name = "escalated_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime escalated_at;

    @Column(name = "first_response_due_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime first_response_due_at;

    @Column(name = "resolution_due_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime resolution_due_at;

    @Column(name = "first_response_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime first_response_at;

    @Column(name = "resolved_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime resolved_at;

    @Column(name = "closed_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime closed_at;

    @Column(name = "resolution_code", columnDefinition = "varchar(64)")
    private String resolution_code;

    @Column(name = "resolution_summary", columnDefinition = "text")
    private String resolution_summary;

    @Column(name = "reopen_count", columnDefinition = "integer")
    private Integer reopen_count;

    @Column(name = "version", columnDefinition = "integer")
    private Integer version;

    @Column(name = "last_activity_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime last_activity_at;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConversationNumber() { return conversation_number; }
    public void setConversationNumber(String conversation_number) { this.conversation_number = conversation_number; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getAssignedTeam() { return assigned_team; }
    public void setAssignedTeam(String assigned_team) { this.assigned_team = assigned_team; }

    public Long getAssignedToUserId() { return assigned_to_user_id; }
    public void setAssignedToUserId(Long assigned_to_user_id) { this.assigned_to_user_id = assigned_to_user_id; }

    public Short getEscalationLevel() { return escalation_level; }
    public void setEscalationLevel(Short escalation_level) { this.escalation_level = escalation_level; }

    public java.time.OffsetDateTime getEscalatedAt() { return escalated_at; }
    public void setEscalatedAt(java.time.OffsetDateTime escalated_at) { this.escalated_at = escalated_at; }

    public java.time.OffsetDateTime getFirstResponseDueAt() { return first_response_due_at; }
    public void setFirstResponseDueAt(java.time.OffsetDateTime first_response_due_at) { this.first_response_due_at = first_response_due_at; }

    public java.time.OffsetDateTime getResolutionDueAt() { return resolution_due_at; }
    public void setResolutionDueAt(java.time.OffsetDateTime resolution_due_at) { this.resolution_due_at = resolution_due_at; }

    public java.time.OffsetDateTime getFirstResponseAt() { return first_response_at; }
    public void setFirstResponseAt(java.time.OffsetDateTime first_response_at) { this.first_response_at = first_response_at; }

    public java.time.OffsetDateTime getResolvedAt() { return resolved_at; }
    public void setResolvedAt(java.time.OffsetDateTime resolved_at) { this.resolved_at = resolved_at; }

    public java.time.OffsetDateTime getClosedAt() { return closed_at; }
    public void setClosedAt(java.time.OffsetDateTime closed_at) { this.closed_at = closed_at; }

    public String getResolutionCode() { return resolution_code; }
    public void setResolutionCode(String resolution_code) { this.resolution_code = resolution_code; }

    public String getResolutionSummary() { return resolution_summary; }
    public void setResolutionSummary(String resolution_summary) { this.resolution_summary = resolution_summary; }

    public Integer getReopenCount() { return reopen_count; }
    public void setReopenCount(Integer reopen_count) { this.reopen_count = reopen_count; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public java.time.OffsetDateTime getLastActivityAt() { return last_activity_at; }
    public void setLastActivityAt(java.time.OffsetDateTime last_activity_at) { this.last_activity_at = last_activity_at; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
