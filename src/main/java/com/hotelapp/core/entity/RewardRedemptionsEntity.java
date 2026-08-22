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
@Table(name = "reward_redemptions",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"redemption_code"})})
public class RewardRedemptionsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "membership_id")
    private Long membership_id;

    @Column(name = "reward_id")
    private Long reward_id;

    @Column(name = "booking_id")
    private Long booking_id;

    @Column(name = "points_spent")
    private Integer points_spent;

    @Column(name = "status", columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "redemption_code", columnDefinition = "varchar(50)")
    private String redemption_code;

    @Column(name = "redeemed_at")
    private java.time.OffsetDateTime redeemed_at;

    @Column(name = "used_at")
    private java.time.OffsetDateTime used_at;

    @Column(name = "expires_at")
    private java.time.OffsetDateTime expires_at;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMembershipId() { return membership_id; }
    public void setMembershipId(Long membership_id) { this.membership_id = membership_id; }

    public Long getRewardId() { return reward_id; }
    public void setRewardId(Long reward_id) { this.reward_id = reward_id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public Integer getPointsSpent() { return points_spent; }
    public void setPointsSpent(Integer points_spent) { this.points_spent = points_spent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRedemptionCode() { return redemption_code; }
    public void setRedemptionCode(String redemption_code) { this.redemption_code = redemption_code; }

    public java.time.OffsetDateTime getRedeemedAt() { return redeemed_at; }
    public void setRedeemedAt(java.time.OffsetDateTime redeemed_at) { this.redeemed_at = redeemed_at; }

    public java.time.OffsetDateTime getUsedAt() { return used_at; }
    public void setUsedAt(java.time.OffsetDateTime used_at) { this.used_at = used_at; }

    public java.time.OffsetDateTime getExpiresAt() { return expires_at; }
    public void setExpiresAt(java.time.OffsetDateTime expires_at) { this.expires_at = expires_at; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
