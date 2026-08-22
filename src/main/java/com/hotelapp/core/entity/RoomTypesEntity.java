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
@Table(name = "room_types",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"code"}), @UniqueConstraint(columnNames = {"name"})})
public class RoomTypesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "code", columnDefinition = "varchar(20)")
    private String code;

    @Column(name = "name", columnDefinition = "varchar(100)")
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "base_price", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal base_price;

    @Column(name = "weekday_rate", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal weekday_rate;

    @Column(name = "weekend_rate", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal weekend_rate;

    @Column(name = "max_occupancy")
    private Integer max_occupancy;

    @Column(name = "bed_type", columnDefinition = "varchar(50)")
    private String bed_type;

    @Column(name = "bed_count")
    private Integer bed_count;

    @Column(name = "allows_extra_bed")
    private Boolean allows_extra_bed;

    @Column(name = "max_extra_beds")
    private Integer max_extra_beds;

    @Column(name = "extra_bed_charge", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal extra_bed_charge;

    @Column(name = "keycard_deposit_amount", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal keycard_deposit_amount;

    @Column(name = "service_charge_percentage", columnDefinition = "numeric(5,2)")
    private java.math.BigDecimal service_charge_percentage;

    @Column(name = "size_sqm", columnDefinition = "numeric(6,2)")
    private java.math.BigDecimal size_sqm;

    @Column(name = "size_sqft", columnDefinition = "numeric(6,2)")
    private java.math.BigDecimal size_sqft;

    @Column(name = "floor_range", columnDefinition = "varchar(20)")
    private String floor_range;

    @Column(name = "images")
    private String images;

    @Column(name = "features")
    private String features;

    @Column(name = "is_active")
    private Boolean is_active;

    @Column(name = "sort_order")
    private Integer sort_order;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public java.math.BigDecimal getBasePrice() { return base_price; }
    public void setBasePrice(java.math.BigDecimal base_price) { this.base_price = base_price; }

    public java.math.BigDecimal getWeekdayRate() { return weekday_rate; }
    public void setWeekdayRate(java.math.BigDecimal weekday_rate) { this.weekday_rate = weekday_rate; }

    public java.math.BigDecimal getWeekendRate() { return weekend_rate; }
    public void setWeekendRate(java.math.BigDecimal weekend_rate) { this.weekend_rate = weekend_rate; }

    public Integer getMaxOccupancy() { return max_occupancy; }
    public void setMaxOccupancy(Integer max_occupancy) { this.max_occupancy = max_occupancy; }

    public String getBedType() { return bed_type; }
    public void setBedType(String bed_type) { this.bed_type = bed_type; }

    public Integer getBedCount() { return bed_count; }
    public void setBedCount(Integer bed_count) { this.bed_count = bed_count; }

    public Boolean isAllowsExtraBed() { return allows_extra_bed; }
    public void setAllowsExtraBed(Boolean allows_extra_bed) { this.allows_extra_bed = allows_extra_bed; }

    public Integer getMaxExtraBeds() { return max_extra_beds; }
    public void setMaxExtraBeds(Integer max_extra_beds) { this.max_extra_beds = max_extra_beds; }

    public java.math.BigDecimal getExtraBedCharge() { return extra_bed_charge; }
    public void setExtraBedCharge(java.math.BigDecimal extra_bed_charge) { this.extra_bed_charge = extra_bed_charge; }

    public java.math.BigDecimal getKeycardDepositAmount() { return keycard_deposit_amount; }
    public void setKeycardDepositAmount(java.math.BigDecimal keycard_deposit_amount) { this.keycard_deposit_amount = keycard_deposit_amount; }

    public java.math.BigDecimal getServiceChargePercentage() { return service_charge_percentage; }
    public void setServiceChargePercentage(java.math.BigDecimal service_charge_percentage) { this.service_charge_percentage = service_charge_percentage; }

    public java.math.BigDecimal getSizeSqm() { return size_sqm; }
    public void setSizeSqm(java.math.BigDecimal size_sqm) { this.size_sqm = size_sqm; }

    public java.math.BigDecimal getSizeSqft() { return size_sqft; }
    public void setSizeSqft(java.math.BigDecimal size_sqft) { this.size_sqft = size_sqft; }

    public String getFloorRange() { return floor_range; }
    public void setFloorRange(String floor_range) { this.floor_range = floor_range; }

    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }

    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public Integer getSortOrder() { return sort_order; }
    public void setSortOrder(Integer sort_order) { this.sort_order = sort_order; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
