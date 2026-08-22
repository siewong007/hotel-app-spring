package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "two_factor_challenges")
@IdClass(TwoFactorChallengesId.class)
public class TwoFactorChallengesEntity {

    @Id
    @Column(name = "user_id")
    private Long user_id;

    @Column(name = "challenge_code", columnDefinition = "varchar(255)")
    private String challenge_code;

    @Id
    @Column(name = "purpose", columnDefinition = "varchar(50)")
    private String purpose;

    @Column(name = "expires_at")
    private java.time.OffsetDateTime expires_at;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public String getChallengeCode() { return challenge_code; }
    public void setChallengeCode(String challenge_code) { this.challenge_code = challenge_code; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public java.time.OffsetDateTime getExpiresAt() { return expires_at; }
    public void setExpiresAt(java.time.OffsetDateTime expires_at) { this.expires_at = expires_at; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
