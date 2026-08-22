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
@Table(name = "loyalty_programs",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"name"})})
public class LoyaltyProgramsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "name", columnDefinition = "varchar(100)")
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "points_per_dollar", columnDefinition = "numeric(10,4) DEFAULT 1.0")
    private java.math.BigDecimal points_per_dollar;

    @Column(name = "currency", columnDefinition = "varchar(3)")
    private String currency;

    @Column(name = "is_active", columnDefinition = "boolean DEFAULT true")
    private Boolean is_active;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public java.math.BigDecimal getPointsPerDollar() { return points_per_dollar; }
    public void setPointsPerDollar(java.math.BigDecimal points_per_dollar) { this.points_per_dollar = points_per_dollar; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
