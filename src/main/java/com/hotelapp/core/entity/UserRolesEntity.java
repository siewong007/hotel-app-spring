package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "user_roles")
@IdClass(UserRolesId.class)
public class UserRolesEntity {

    @Id
    @Column(name = "user_id")
    private Long user_id;

    @Id
    @Column(name = "role_id")
    private Long role_id;

    @Column(name = "assigned_at")
    private java.time.OffsetDateTime assigned_at;

    @Column(name = "assigned_by")
    private Long assigned_by;

    @Column(name = "expires_at")
    private java.time.OffsetDateTime expires_at;

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public Long getRoleId() { return role_id; }
    public void setRoleId(Long role_id) { this.role_id = role_id; }

    public java.time.OffsetDateTime getAssignedAt() { return assigned_at; }
    public void setAssignedAt(java.time.OffsetDateTime assigned_at) { this.assigned_at = assigned_at; }

    public Long getAssignedBy() { return assigned_by; }
    public void setAssignedBy(Long assigned_by) { this.assigned_by = assigned_by; }

    public java.time.OffsetDateTime getExpiresAt() { return expires_at; }
    public void setExpiresAt(java.time.OffsetDateTime expires_at) { this.expires_at = expires_at; }
}
