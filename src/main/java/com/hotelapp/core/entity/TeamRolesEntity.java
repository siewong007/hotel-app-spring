package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "team_roles")
@IdClass(TeamRolesId.class)
public class TeamRolesEntity {

    @Id
    @Column(name = "team_id")
    private Long team_id;

    @Id
    @Column(name = "role_id")
    private Long role_id;

    @Column(name = "granted_at")
    private java.time.OffsetDateTime granted_at;

    @Column(name = "granted_by")
    private Long granted_by;

    public Long getTeamId() { return team_id; }
    public void setTeamId(Long team_id) { this.team_id = team_id; }

    public Long getRoleId() { return role_id; }
    public void setRoleId(Long role_id) { this.role_id = role_id; }

    public java.time.OffsetDateTime getGrantedAt() { return granted_at; }
    public void setGrantedAt(java.time.OffsetDateTime granted_at) { this.granted_at = granted_at; }

    public Long getGrantedBy() { return granted_by; }
    public void setGrantedBy(Long granted_by) { this.granted_by = granted_by; }
}
