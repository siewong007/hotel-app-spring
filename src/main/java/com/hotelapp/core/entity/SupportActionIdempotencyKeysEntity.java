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
@Table(name = "support_action_idempotency_keys",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"conversation_id", "actor_user_id", "idempotency_key"})})
public class SupportActionIdempotencyKeysEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "conversation_id", columnDefinition = "bigint")
    private Long conversation_id;

    @Column(name = "actor_user_id", columnDefinition = "bigint")
    private Long actor_user_id;

    @Column(name = "idempotency_key", columnDefinition = "varchar(128)")
    private String idempotency_key;

    @Column(name = "action", columnDefinition = "varchar(64)")
    private String action;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConversationId() { return conversation_id; }
    public void setConversationId(Long conversation_id) { this.conversation_id = conversation_id; }

    public Long getActorUserId() { return actor_user_id; }
    public void setActorUserId(Long actor_user_id) { this.actor_user_id = actor_user_id; }

    public String getIdempotencyKey() { return idempotency_key; }
    public void setIdempotencyKey(String idempotency_key) { this.idempotency_key = idempotency_key; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
