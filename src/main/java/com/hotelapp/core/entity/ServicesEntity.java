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
@Table(name = "services")
public class ServicesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "name", columnDefinition = "varchar(100)")
    private String name;

    @Column(name = "category", columnDefinition = "varchar(50)")
    private String category;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "unit_price", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal unit_price;

    @Column(name = "unit_type", columnDefinition = "varchar(20)")
    private String unit_type;

    @Column(name = "tax_rate", columnDefinition = "numeric(5,2) DEFAULT 0")
    private java.math.BigDecimal tax_rate;

    @Column(name = "is_taxable", columnDefinition = "boolean DEFAULT true")
    private Boolean is_taxable;

    @Column(name = "is_active", columnDefinition = "boolean DEFAULT true")
    private Boolean is_active;

    @Column(name = "image_url", columnDefinition = "text")
    private String image_url;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public java.math.BigDecimal getUnitPrice() { return unit_price; }
    public void setUnitPrice(java.math.BigDecimal unit_price) { this.unit_price = unit_price; }

    public String getUnitType() { return unit_type; }
    public void setUnitType(String unit_type) { this.unit_type = unit_type; }

    public java.math.BigDecimal getTaxRate() { return tax_rate; }
    public void setTaxRate(java.math.BigDecimal tax_rate) { this.tax_rate = tax_rate; }

    public Boolean isIsTaxable() { return is_taxable; }
    public void setIsTaxable(Boolean is_taxable) { this.is_taxable = is_taxable; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public String getImageUrl() { return image_url; }
    public void setImageUrl(String image_url) { this.image_url = image_url; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
