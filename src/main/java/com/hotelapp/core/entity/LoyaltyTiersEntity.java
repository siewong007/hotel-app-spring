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
@Table(name = "loyalty_tiers",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"program_id", "name"})})
public class LoyaltyTiersEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "program_id", columnDefinition = "bigint")
    private Long program_id;

    @Column(name = "name", columnDefinition = "varchar(50)")
    private String name;

    @Column(name = "min_points", columnDefinition = "integer")
    private Integer min_points;

    @Column(name = "max_points", columnDefinition = "integer")
    private Integer max_points;

    @Column(name = "benefits", columnDefinition = "jsonb")
    private String benefits;

    @Column(name = "discount_percentage", columnDefinition = "numeric(5,2) DEFAULT 0")
    private java.math.BigDecimal discount_percentage;

    @Column(name = "points_multiplier", columnDefinition = "numeric(4,2) DEFAULT 1.0")
    private java.math.BigDecimal points_multiplier;

    @Column(name = "color", columnDefinition = "varchar(7)")
    private String color;

    @Column(name = "icon", columnDefinition = "varchar(100)")
    private String icon;

    @Column(name = "sort_order", columnDefinition = "integer DEFAULT 0")
    private Integer sort_order;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "code", columnDefinition = "varchar(50)")
    private String code;

    @Column(name = "min_nights", columnDefinition = "integer")
    private Integer min_nights;

    @Column(name = "min_spend", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal min_spend;

    @Column(name = "is_active", columnDefinition = "boolean")
    private Boolean is_active;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProgramId() { return program_id; }
    public void setProgramId(Long program_id) { this.program_id = program_id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getMinPoints() { return min_points; }
    public void setMinPoints(Integer min_points) { this.min_points = min_points; }

    public Integer getMaxPoints() { return max_points; }
    public void setMaxPoints(Integer max_points) { this.max_points = max_points; }

    public String getBenefits() { return benefits; }
    public void setBenefits(String benefits) { this.benefits = benefits; }

    public java.math.BigDecimal getDiscountPercentage() { return discount_percentage; }
    public void setDiscountPercentage(java.math.BigDecimal discount_percentage) { this.discount_percentage = discount_percentage; }

    public java.math.BigDecimal getPointsMultiplier() { return points_multiplier; }
    public void setPointsMultiplier(java.math.BigDecimal points_multiplier) { this.points_multiplier = points_multiplier; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getSortOrder() { return sort_order; }
    public void setSortOrder(Integer sort_order) { this.sort_order = sort_order; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Integer getMinNights() { return min_nights; }
    public void setMinNights(Integer min_nights) { this.min_nights = min_nights; }

    public java.math.BigDecimal getMinSpend() { return min_spend; }
    public void setMinSpend(java.math.BigDecimal min_spend) { this.min_spend = min_spend; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
