package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "passkey_challenges")
public class PasskeyChallengesEntity {

    @Id
    @Column(name = "id")
    private java.util.UUID id;

    @Column(name = "user_id")
    private Long user_id;

    @Column(name = "challenge")
    private String challenge;

    @Column(name = "challenge_type", columnDefinition = "varchar(20)")
    private String challenge_type;

    @Column(name = "expires_at")
    private java.time.OffsetDateTime expires_at;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "used_at")
    private java.time.OffsetDateTime used_at;

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public String getChallenge() { return challenge; }
    public void setChallenge(String challenge) { this.challenge = challenge; }

    public String getChallengeType() { return challenge_type; }
    public void setChallengeType(String challenge_type) { this.challenge_type = challenge_type; }

    public java.time.OffsetDateTime getExpiresAt() { return expires_at; }
    public void setExpiresAt(java.time.OffsetDateTime expires_at) { this.expires_at = expires_at; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUsedAt() { return used_at; }
    public void setUsedAt(java.time.OffsetDateTime used_at) { this.used_at = used_at; }
}
