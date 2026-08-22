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
@Table(name = "loyalty_redemptions")
public class LoyaltyRedemptionsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id")
    private Long member_id;

    @Column(name = "reward_id")
    private Long reward_id;

    @Column(name = "transaction_id")
    private Long transaction_id;

    @Column(name = "points_spent")
    private Integer points_spent;

    @Column(name = "status", columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "requested_at")
    private java.time.OffsetDateTime requested_at;

    @Column(name = "reviewed_by")
    private Long reviewed_by;

    @Column(name = "reviewed_at")
    private java.time.OffsetDateTime reviewed_at;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejection_reason;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMemberId() { return member_id; }
    public void setMemberId(Long member_id) { this.member_id = member_id; }

    public Long getRewardId() { return reward_id; }
    public void setRewardId(Long reward_id) { this.reward_id = reward_id; }

    public Long getTransactionId() { return transaction_id; }
    public void setTransactionId(Long transaction_id) { this.transaction_id = transaction_id; }

    public Integer getPointsSpent() { return points_spent; }
    public void setPointsSpent(Integer points_spent) { this.points_spent = points_spent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public java.time.OffsetDateTime getRequestedAt() { return requested_at; }
    public void setRequestedAt(java.time.OffsetDateTime requested_at) { this.requested_at = requested_at; }

    public Long getReviewedBy() { return reviewed_by; }
    public void setReviewedBy(Long reviewed_by) { this.reviewed_by = reviewed_by; }

    public java.time.OffsetDateTime getReviewedAt() { return reviewed_at; }
    public void setReviewedAt(java.time.OffsetDateTime reviewed_at) { this.reviewed_at = reviewed_at; }

    public String getRejectionReason() { return rejection_reason; }
    public void setRejectionReason(String rejection_reason) { this.rejection_reason = rejection_reason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
