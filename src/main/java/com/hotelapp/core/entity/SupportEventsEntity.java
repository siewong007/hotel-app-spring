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
@Table(name = "support_events")
public class SupportEventsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "conversation_id", columnDefinition = "bigint")
    private Long conversation_id;

    @Column(name = "actor_guest_id", columnDefinition = "bigint")
    private Long actor_guest_id;

    @Column(name = "actor_user_id", columnDefinition = "bigint")
    private Long actor_user_id;

    @Column(name = "event_type", columnDefinition = "varchar(64)")
    private String event_type;

    @Column(name = "from_status", columnDefinition = "varchar(32)")
    private String from_status;

    @Column(name = "to_status", columnDefinition = "varchar(32)")
    private String to_status;

    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConversationId() { return conversation_id; }
    public void setConversationId(Long conversation_id) { this.conversation_id = conversation_id; }

    public Long getActorGuestId() { return actor_guest_id; }
    public void setActorGuestId(Long actor_guest_id) { this.actor_guest_id = actor_guest_id; }

    public Long getActorUserId() { return actor_user_id; }
    public void setActorUserId(Long actor_user_id) { this.actor_user_id = actor_user_id; }

    public String getEventType() { return event_type; }
    public void setEventType(String event_type) { this.event_type = event_type; }

    public String getFromStatus() { return from_status; }
    public void setFromStatus(String from_status) { this.from_status = from_status; }

    public String getToStatus() { return to_status; }
    public void setToStatus(String to_status) { this.to_status = to_status; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
