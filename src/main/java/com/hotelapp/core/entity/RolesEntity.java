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
@Table(name = "roles",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"name"})})
public class RolesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name", columnDefinition = "varchar(50)")
    private String name;

    @Column(name = "display_name", columnDefinition = "varchar(100)")
    private String display_name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_system_role")
    private Boolean is_system_role;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return display_name; }
    public void setDisplayName(String display_name) { this.display_name = display_name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean isIsSystemRole() { return is_system_role; }
    public void setIsSystemRole(Boolean is_system_role) { this.is_system_role = is_system_role; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
