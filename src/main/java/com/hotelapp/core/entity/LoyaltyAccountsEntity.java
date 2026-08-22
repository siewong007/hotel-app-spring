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
@Table(name = "loyalty_accounts",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"member_id"})})
public class LoyaltyAccountsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id")
    private Long member_id;

    @Column(name = "current_tier_id")
    private Long current_tier_id;

    @Column(name = "lifetime_points")
    private Integer lifetime_points;

    @Column(name = "qualifying_points")
    private Integer qualifying_points;

    @Column(name = "qualifying_nights")
    private Integer qualifying_nights;

    @Column(name = "qualifying_spend", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal qualifying_spend;

    @Column(name = "tier_evaluation_year")
    private Integer tier_evaluation_year;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMemberId() { return member_id; }
    public void setMemberId(Long member_id) { this.member_id = member_id; }

    public Long getCurrentTierId() { return current_tier_id; }
    public void setCurrentTierId(Long current_tier_id) { this.current_tier_id = current_tier_id; }

    public Integer getLifetimePoints() { return lifetime_points; }
    public void setLifetimePoints(Integer lifetime_points) { this.lifetime_points = lifetime_points; }

    public Integer getQualifyingPoints() { return qualifying_points; }
    public void setQualifyingPoints(Integer qualifying_points) { this.qualifying_points = qualifying_points; }

    public Integer getQualifyingNights() { return qualifying_nights; }
    public void setQualifyingNights(Integer qualifying_nights) { this.qualifying_nights = qualifying_nights; }

    public java.math.BigDecimal getQualifyingSpend() { return qualifying_spend; }
    public void setQualifyingSpend(java.math.BigDecimal qualifying_spend) { this.qualifying_spend = qualifying_spend; }

    public Integer getTierEvaluationYear() { return tier_evaluation_year; }
    public void setTierEvaluationYear(Integer tier_evaluation_year) { this.tier_evaluation_year = tier_evaluation_year; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
