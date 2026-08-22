package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "support_guest_request_idempotency_keys")
@IdClass(SupportGuestRequestIdempotencyKeysId.class)
public class SupportGuestRequestIdempotencyKeysEntity {

    @Id
    @Column(name = "guest_id", columnDefinition = "bigint")
    private Long guest_id;

    @Id
    @Column(name = "idempotency_key", columnDefinition = "varchar(128)")
    private String idempotency_key;

    @Column(name = "conversation_id", columnDefinition = "bigint")
    private Long conversation_id;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getIdempotencyKey() { return idempotency_key; }
    public void setIdempotencyKey(String idempotency_key) { this.idempotency_key = idempotency_key; }

    public Long getConversationId() { return conversation_id; }
    public void setConversationId(Long conversation_id) { this.conversation_id = conversation_id; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
