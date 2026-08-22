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
@Table(name = "booking_channels",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"name"})})
public class BookingChannelsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "name", columnDefinition = "varchar(120)")
    private String name;

    @Column(name = "channel_type", columnDefinition = "varchar(30)")
    private String channel_type;

    @Column(name = "default_commission_type", columnDefinition = "varchar(30)")
    private String default_commission_type;

    @Column(name = "default_commission_value", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal default_commission_value;

    @Column(name = "default_commission_scope", columnDefinition = "varchar(20)")
    private String default_commission_scope;

    @Column(name = "is_active", columnDefinition = "boolean")
    private Boolean is_active;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getChannelType() { return channel_type; }
    public void setChannelType(String channel_type) { this.channel_type = channel_type; }

    public String getDefaultCommissionType() { return default_commission_type; }
    public void setDefaultCommissionType(String default_commission_type) { this.default_commission_type = default_commission_type; }

    public java.math.BigDecimal getDefaultCommissionValue() { return default_commission_value; }
    public void setDefaultCommissionValue(java.math.BigDecimal default_commission_value) { this.default_commission_value = default_commission_value; }

    public String getDefaultCommissionScope() { return default_commission_scope; }
    public void setDefaultCommissionScope(String default_commission_scope) { this.default_commission_scope = default_commission_scope; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
