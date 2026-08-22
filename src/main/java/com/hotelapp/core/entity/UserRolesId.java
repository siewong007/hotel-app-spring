package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserRolesId implements Serializable {

    private Long user_id;
    private Long role_id;

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public Long getRoleId() { return role_id; }
    public void setRoleId(Long role_id) { this.role_id = role_id; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserRolesId)) return false;
        UserRolesId other = (UserRolesId) o;
        return Objects.equals(this.user_id, other.user_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUserId(), getRoleId());
    }
}
