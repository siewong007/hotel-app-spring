package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "user_permissions")
@IdClass(UserPermissionsId.class)
public class UserPermissionsEntity {

    @Id
    @Column(name = "user_id", columnDefinition = "bigint")
    private Long user_id;

    @Id
    @Column(name = "permission_id", columnDefinition = "bigint")
    private Long permission_id;

    @Column(name = "assigned_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime assigned_at;

    @Column(name = "assigned_by", columnDefinition = "bigint")
    private Long assigned_by;

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public Long getPermissionId() { return permission_id; }
    public void setPermissionId(Long permission_id) { this.permission_id = permission_id; }

    public java.time.OffsetDateTime getAssignedAt() { return assigned_at; }
    public void setAssignedAt(java.time.OffsetDateTime assigned_at) { this.assigned_at = assigned_at; }

    public Long getAssignedBy() { return assigned_by; }
    public void setAssignedBy(Long assigned_by) { this.assigned_by = assigned_by; }
}
