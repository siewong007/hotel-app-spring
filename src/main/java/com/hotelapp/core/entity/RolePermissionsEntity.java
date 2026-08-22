package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "role_permissions")
@IdClass(RolePermissionsId.class)
public class RolePermissionsEntity {

    @Id
    @Column(name = "role_id", columnDefinition = "bigint")
    private Long role_id;

    @Id
    @Column(name = "permission_id", columnDefinition = "bigint")
    private Long permission_id;

    @Column(name = "granted_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime granted_at;

    @Column(name = "granted_by", columnDefinition = "bigint")
    private Long granted_by;

    public Long getRoleId() { return role_id; }
    public void setRoleId(Long role_id) { this.role_id = role_id; }

    public Long getPermissionId() { return permission_id; }
    public void setPermissionId(Long permission_id) { this.permission_id = permission_id; }

    public java.time.OffsetDateTime getGrantedAt() { return granted_at; }
    public void setGrantedAt(java.time.OffsetDateTime granted_at) { this.granted_at = granted_at; }

    public Long getGrantedBy() { return granted_by; }
    public void setGrantedBy(Long granted_by) { this.granted_by = granted_by; }
}
