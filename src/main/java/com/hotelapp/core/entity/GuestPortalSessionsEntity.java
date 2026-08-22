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
@Table(name = "guest_portal_sessions",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"token_hash"})})
public class GuestPortalSessionsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "guest_id", columnDefinition = "bigint")
    private Long guest_id;

    @Column(name = "token_hash", columnDefinition = "text")
    private String token_hash;

    @Column(name = "expires_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime expires_at;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "last_used_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime last_used_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getTokenHash() { return token_hash; }
    public void setTokenHash(String token_hash) { this.token_hash = token_hash; }

    public java.time.OffsetDateTime getExpiresAt() { return expires_at; }
    public void setExpiresAt(java.time.OffsetDateTime expires_at) { this.expires_at = expires_at; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getLastUsedAt() { return last_used_at; }
    public void setLastUsedAt(java.time.OffsetDateTime last_used_at) { this.last_used_at = last_used_at; }
}
