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
@Table(name = "permissions",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"name"})})
public class PermissionsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "name", columnDefinition = "varchar(100)")
    private String name;

    @Column(name = "resource", columnDefinition = "varchar(50)")
    private String resource;

    @Column(name = "action", columnDefinition = "varchar(20)")
    private String action;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_system_permission", columnDefinition = "boolean DEFAULT false")
    private Boolean is_system_permission;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean isIsSystemPermission() { return is_system_permission; }
    public void setIsSystemPermission(Boolean is_system_permission) { this.is_system_permission = is_system_permission; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
