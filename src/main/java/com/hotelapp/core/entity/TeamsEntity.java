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
@Table(name = "teams")
public class TeamsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid")
    private java.util.UUID uuid;

    @Column(name = "code", columnDefinition = "varchar(50)")
    private String code;

    @Column(name = "name", columnDefinition = "varchar(100)")
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_active")
    private Boolean is_active;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "created_by")
    private Long created_by;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    @Column(name = "updated_by")
    private Long updated_by;

    @Column(name = "deleted_at")
    private java.time.OffsetDateTime deleted_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.util.UUID getUuid() { return uuid; }
    public void setUuid(java.util.UUID uuid) { this.uuid = uuid; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }

    public Long getUpdatedBy() { return updated_by; }
    public void setUpdatedBy(Long updated_by) { this.updated_by = updated_by; }

    public java.time.OffsetDateTime getDeletedAt() { return deleted_at; }
    public void setDeletedAt(java.time.OffsetDateTime deleted_at) { this.deleted_at = deleted_at; }
}
