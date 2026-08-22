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
@Table(name = "loyalty_memberships",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"guest_id", "program_id"}), @UniqueConstraint(columnNames = {"member_number"})})
public class LoyaltyMembershipsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "guest_id", columnDefinition = "bigint")
    private Long guest_id;

    @Column(name = "program_id", columnDefinition = "bigint")
    private Long program_id;

    @Column(name = "tier_id", columnDefinition = "bigint")
    private Long tier_id;

    @Column(name = "member_number", columnDefinition = "varchar(50)")
    private String member_number;

    @Column(name = "points_balance", columnDefinition = "integer DEFAULT 0")
    private Integer points_balance;

    @Column(name = "lifetime_points", columnDefinition = "integer DEFAULT 0")
    private Integer lifetime_points;

    @Column(name = "status", columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "enrolled_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime enrolled_at;

    @Column(name = "expires_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime expires_at;

    @Column(name = "last_activity_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime last_activity_at;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public Long getProgramId() { return program_id; }
    public void setProgramId(Long program_id) { this.program_id = program_id; }

    public Long getTierId() { return tier_id; }
    public void setTierId(Long tier_id) { this.tier_id = tier_id; }

    public String getMemberNumber() { return member_number; }
    public void setMemberNumber(String member_number) { this.member_number = member_number; }

    public Integer getPointsBalance() { return points_balance; }
    public void setPointsBalance(Integer points_balance) { this.points_balance = points_balance; }

    public Integer getLifetimePoints() { return lifetime_points; }
    public void setLifetimePoints(Integer lifetime_points) { this.lifetime_points = lifetime_points; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public java.time.OffsetDateTime getEnrolledAt() { return enrolled_at; }
    public void setEnrolledAt(java.time.OffsetDateTime enrolled_at) { this.enrolled_at = enrolled_at; }

    public java.time.OffsetDateTime getExpiresAt() { return expires_at; }
    public void setExpiresAt(java.time.OffsetDateTime expires_at) { this.expires_at = expires_at; }

    public java.time.OffsetDateTime getLastActivityAt() { return last_activity_at; }
    public void setLastActivityAt(java.time.OffsetDateTime last_activity_at) { this.last_activity_at = last_activity_at; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
