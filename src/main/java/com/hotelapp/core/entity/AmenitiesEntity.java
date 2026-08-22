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
@Table(name = "amenities",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"name"})})
public class AmenitiesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name", columnDefinition = "varchar(100)")
    private String name;

    @Column(name = "category", columnDefinition = "varchar(50)")
    private String category;

    @Column(name = "icon", columnDefinition = "varchar(50)")
    private String icon;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_paid")
    private Boolean is_paid;

    @Column(name = "price", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal price;

    @Column(name = "is_active")
    private Boolean is_active;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean isIsPaid() { return is_paid; }
    public void setIsPaid(Boolean is_paid) { this.is_paid = is_paid; }

    public java.math.BigDecimal getPrice() { return price; }
    public void setPrice(java.math.BigDecimal price) { this.price = price; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
