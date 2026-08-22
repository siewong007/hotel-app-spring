package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class RolePermissionsId implements Serializable {

    private Long role_id;
    private Long permission_id;

    public Long getRoleId() { return role_id; }
    public void setRoleId(Long role_id) { this.role_id = role_id; }

    public Long getPermissionId() { return permission_id; }
    public void setPermissionId(Long permission_id) { this.permission_id = permission_id; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolePermissionsId)) return false;
        RolePermissionsId other = (RolePermissionsId) o;
        return Objects.equals(this.role_id, other.role_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRoleId(), getPermissionId());
    }
}
