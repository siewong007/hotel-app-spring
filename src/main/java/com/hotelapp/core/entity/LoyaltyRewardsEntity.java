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
@Table(name = "loyalty_rewards")
public class LoyaltyRewardsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name", columnDefinition = "varchar(120)")
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "category", columnDefinition = "varchar(50)")
    private String category;

    @Column(name = "points_cost")
    private Integer points_cost;

    @Column(name = "minimum_tier_id")
    private Long minimum_tier_id;

    @Column(name = "requires_approval")
    private Boolean requires_approval;

    @Column(name = "is_active")
    private Boolean is_active;

    @Column(name = "inventory_count")
    private Integer inventory_count;

    @Column(name = "valid_from")
    private java.time.LocalDate valid_from;

    @Column(name = "valid_to")
    private java.time.LocalDate valid_to;

    @Column(name = "terms_conditions", columnDefinition = "text")
    private String terms_conditions;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getPointsCost() { return points_cost; }
    public void setPointsCost(Integer points_cost) { this.points_cost = points_cost; }

    public Long getMinimumTierId() { return minimum_tier_id; }
    public void setMinimumTierId(Long minimum_tier_id) { this.minimum_tier_id = minimum_tier_id; }

    public Boolean isRequiresApproval() { return requires_approval; }
    public void setRequiresApproval(Boolean requires_approval) { this.requires_approval = requires_approval; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public Integer getInventoryCount() { return inventory_count; }
    public void setInventoryCount(Integer inventory_count) { this.inventory_count = inventory_count; }

    public java.time.LocalDate getValidFrom() { return valid_from; }
    public void setValidFrom(java.time.LocalDate valid_from) { this.valid_from = valid_from; }

    public java.time.LocalDate getValidTo() { return valid_to; }
    public void setValidTo(java.time.LocalDate valid_to) { this.valid_to = valid_to; }

    public String getTermsConditions() { return terms_conditions; }
    public void setTermsConditions(String terms_conditions) { this.terms_conditions = terms_conditions; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
