package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserPermissionsId implements Serializable {

    private Long user_id;
    private Long permission_id;

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public Long getPermissionId() { return permission_id; }
    public void setPermissionId(Long permission_id) { this.permission_id = permission_id; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPermissionsId)) return false;
        UserPermissionsId other = (UserPermissionsId) o;
        return Objects.equals(this.user_id, other.user_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUserId(), getPermissionId());
    }
}
