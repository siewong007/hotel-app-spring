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
@Table(name = "reward_catalog")
public class RewardCatalogEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "program_id")
    private Long program_id;

    @Column(name = "name", columnDefinition = "varchar(100)")
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "category", columnDefinition = "varchar(50)")
    private String category;

    @Column(name = "points_required")
    private Integer points_required;

    @Column(name = "quantity_available")
    private Integer quantity_available;

    @Column(name = "valid_from")
    private java.time.OffsetDateTime valid_from;

    @Column(name = "valid_to")
    private java.time.OffsetDateTime valid_to;

    @Column(name = "is_active")
    private Boolean is_active;

    @Column(name = "terms_conditions", columnDefinition = "text")
    private String terms_conditions;

    @Column(name = "image_url", columnDefinition = "text")
    private String image_url;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProgramId() { return program_id; }
    public void setProgramId(Long program_id) { this.program_id = program_id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getPointsRequired() { return points_required; }
    public void setPointsRequired(Integer points_required) { this.points_required = points_required; }

    public Integer getQuantityAvailable() { return quantity_available; }
    public void setQuantityAvailable(Integer quantity_available) { this.quantity_available = quantity_available; }

    public java.time.OffsetDateTime getValidFrom() { return valid_from; }
    public void setValidFrom(java.time.OffsetDateTime valid_from) { this.valid_from = valid_from; }

    public java.time.OffsetDateTime getValidTo() { return valid_to; }
    public void setValidTo(java.time.OffsetDateTime valid_to) { this.valid_to = valid_to; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public String getTermsConditions() { return terms_conditions; }
    public void setTermsConditions(String terms_conditions) { this.terms_conditions = terms_conditions; }

    public String getImageUrl() { return image_url; }
    public void setImageUrl(String image_url) { this.image_url = image_url; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
