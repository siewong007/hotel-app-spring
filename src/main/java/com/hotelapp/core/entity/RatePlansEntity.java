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
@Table(name = "rate_plans",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"code"}), @UniqueConstraint(columnNames = {"name"})})
public class RatePlansEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name", columnDefinition = "varchar(100)")
    private String name;

    @Column(name = "code", columnDefinition = "varchar(20)")
    private String code;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "plan_type", columnDefinition = "varchar(50)")
    private String plan_type;

    @Column(name = "adjustment_type", columnDefinition = "varchar(20)")
    private String adjustment_type;

    @Column(name = "adjustment_value", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal adjustment_value;

    @Column(name = "valid_from")
    private java.time.LocalDate valid_from;

    @Column(name = "valid_to")
    private java.time.LocalDate valid_to;

    @Column(name = "applies_monday")
    private Boolean applies_monday;

    @Column(name = "applies_tuesday")
    private Boolean applies_tuesday;

    @Column(name = "applies_wednesday")
    private Boolean applies_wednesday;

    @Column(name = "applies_thursday")
    private Boolean applies_thursday;

    @Column(name = "applies_friday")
    private Boolean applies_friday;

    @Column(name = "applies_saturday")
    private Boolean applies_saturday;

    @Column(name = "applies_sunday")
    private Boolean applies_sunday;

    @Column(name = "min_nights")
    private Integer min_nights;

    @Column(name = "max_nights")
    private Integer max_nights;

    @Column(name = "min_advance_booking")
    private Integer min_advance_booking;

    @Column(name = "max_advance_booking")
    private Integer max_advance_booking;

    @Column(name = "blackout_dates")
    private String blackout_dates;

    @Column(name = "is_active")
    private Boolean is_active;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "created_by")
    private Long created_by;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPlanType() { return plan_type; }
    public void setPlanType(String plan_type) { this.plan_type = plan_type; }

    public String getAdjustmentType() { return adjustment_type; }
    public void setAdjustmentType(String adjustment_type) { this.adjustment_type = adjustment_type; }

    public java.math.BigDecimal getAdjustmentValue() { return adjustment_value; }
    public void setAdjustmentValue(java.math.BigDecimal adjustment_value) { this.adjustment_value = adjustment_value; }

    public java.time.LocalDate getValidFrom() { return valid_from; }
    public void setValidFrom(java.time.LocalDate valid_from) { this.valid_from = valid_from; }

    public java.time.LocalDate getValidTo() { return valid_to; }
    public void setValidTo(java.time.LocalDate valid_to) { this.valid_to = valid_to; }

    public Boolean isAppliesMonday() { return applies_monday; }
    public void setAppliesMonday(Boolean applies_monday) { this.applies_monday = applies_monday; }

    public Boolean isAppliesTuesday() { return applies_tuesday; }
    public void setAppliesTuesday(Boolean applies_tuesday) { this.applies_tuesday = applies_tuesday; }

    public Boolean isAppliesWednesday() { return applies_wednesday; }
    public void setAppliesWednesday(Boolean applies_wednesday) { this.applies_wednesday = applies_wednesday; }

    public Boolean isAppliesThursday() { return applies_thursday; }
    public void setAppliesThursday(Boolean applies_thursday) { this.applies_thursday = applies_thursday; }

    public Boolean isAppliesFriday() { return applies_friday; }
    public void setAppliesFriday(Boolean applies_friday) { this.applies_friday = applies_friday; }

    public Boolean isAppliesSaturday() { return applies_saturday; }
    public void setAppliesSaturday(Boolean applies_saturday) { this.applies_saturday = applies_saturday; }

    public Boolean isAppliesSunday() { return applies_sunday; }
    public void setAppliesSunday(Boolean applies_sunday) { this.applies_sunday = applies_sunday; }

    public Integer getMinNights() { return min_nights; }
    public void setMinNights(Integer min_nights) { this.min_nights = min_nights; }

    public Integer getMaxNights() { return max_nights; }
    public void setMaxNights(Integer max_nights) { this.max_nights = max_nights; }

    public Integer getMinAdvanceBooking() { return min_advance_booking; }
    public void setMinAdvanceBooking(Integer min_advance_booking) { this.min_advance_booking = min_advance_booking; }

    public Integer getMaxAdvanceBooking() { return max_advance_booking; }
    public void setMaxAdvanceBooking(Integer max_advance_booking) { this.max_advance_booking = max_advance_booking; }

    public String getBlackoutDates() { return blackout_dates; }
    public void setBlackoutDates(String blackout_dates) { this.blackout_dates = blackout_dates; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
