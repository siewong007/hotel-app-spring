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
@Table(name = "loyalty_program_rules")
public class LoyaltyProgramRulesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "points_per_currency_unit", columnDefinition = "numeric(10,4)")
    private java.math.BigDecimal points_per_currency_unit;

    @Column(name = "tier_qualification_metric", columnDefinition = "varchar(20)")
    private String tier_qualification_metric;

    @Column(name = "point_expiry_months", columnDefinition = "integer")
    private Integer point_expiry_months;

    @Column(name = "redemption_approval_required", columnDefinition = "boolean")
    private Boolean redemption_approval_required;

    @Column(name = "earning_enabled", columnDefinition = "boolean")
    private Boolean earning_enabled;

    @Column(name = "min_eligible_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal min_eligible_amount;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.math.BigDecimal getPointsPerCurrencyUnit() { return points_per_currency_unit; }
    public void setPointsPerCurrencyUnit(java.math.BigDecimal points_per_currency_unit) { this.points_per_currency_unit = points_per_currency_unit; }

    public String getTierQualificationMetric() { return tier_qualification_metric; }
    public void setTierQualificationMetric(String tier_qualification_metric) { this.tier_qualification_metric = tier_qualification_metric; }

    public Integer getPointExpiryMonths() { return point_expiry_months; }
    public void setPointExpiryMonths(Integer point_expiry_months) { this.point_expiry_months = point_expiry_months; }

    public Boolean isRedemptionApprovalRequired() { return redemption_approval_required; }
    public void setRedemptionApprovalRequired(Boolean redemption_approval_required) { this.redemption_approval_required = redemption_approval_required; }

    public Boolean isEarningEnabled() { return earning_enabled; }
    public void setEarningEnabled(Boolean earning_enabled) { this.earning_enabled = earning_enabled; }

    public java.math.BigDecimal getMinEligibleAmount() { return min_eligible_amount; }
    public void setMinEligibleAmount(java.math.BigDecimal min_eligible_amount) { this.min_eligible_amount = min_eligible_amount; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
