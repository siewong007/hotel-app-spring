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
@Table(name = "ekyc_decision_history")
public class EkycDecisionHistoryEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "application_id", columnDefinition = "bigint")
    private Long application_id;

    @Column(name = "actor_id", columnDefinition = "bigint")
    private Long actor_id;

    @Column(name = "action", columnDefinition = "varchar(100)")
    private String action;

    @Column(name = "from_status", columnDefinition = "varchar(50)")
    private String from_status;

    @Column(name = "to_status", columnDefinition = "varchar(50)")
    private String to_status;

    @Column(name = "reason_code", columnDefinition = "varchar(80)")
    private String reason_code;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getApplicationId() { return application_id; }
    public void setApplicationId(Long application_id) { this.application_id = application_id; }

    public Long getActorId() { return actor_id; }
    public void setActorId(Long actor_id) { this.actor_id = actor_id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getFromStatus() { return from_status; }
    public void setFromStatus(String from_status) { this.from_status = from_status; }

    public String getToStatus() { return to_status; }
    public void setToStatus(String to_status) { this.to_status = to_status; }

    public String getReasonCode() { return reason_code; }
    public void setReasonCode(String reason_code) { this.reason_code = reason_code; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
