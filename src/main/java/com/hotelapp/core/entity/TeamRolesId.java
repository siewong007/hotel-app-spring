package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class TeamRolesId implements Serializable {

    private Long team_id;
    private Long role_id;

    public Long getTeamId() { return team_id; }
    public void setTeamId(Long team_id) { this.team_id = team_id; }

    public Long getRoleId() { return role_id; }
    public void setRoleId(Long role_id) { this.role_id = role_id; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamRolesId)) return false;
        TeamRolesId other = (TeamRolesId) o;
        return Objects.equals(this.team_id, other.team_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTeamId(), getRoleId());
    }
}
