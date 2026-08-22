package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class TeamMembersId implements Serializable {

    private Long team_id;
    private Long user_id;

    public Long getTeamId() { return team_id; }
    public void setTeamId(Long team_id) { this.team_id = team_id; }

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamMembersId)) return false;
        TeamMembersId other = (TeamMembersId) o;
        return Objects.equals(this.team_id, other.team_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTeamId(), getUserId());
    }
}
