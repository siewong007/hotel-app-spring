package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "team_members")
@IdClass(TeamMembersId.class)
public class TeamMembersEntity {

    @Id
    @Column(name = "team_id", columnDefinition = "bigint")
    private Long team_id;

    @Id
    @Column(name = "user_id", columnDefinition = "bigint")
    private Long user_id;

    @Column(name = "is_lead", columnDefinition = "boolean")
    private Boolean is_lead;

    @Column(name = "joined_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime joined_at;

    @Column(name = "added_by", columnDefinition = "bigint")
    private Long added_by;

    @Column(name = "expires_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime expires_at;

    public Long getTeamId() { return team_id; }
    public void setTeamId(Long team_id) { this.team_id = team_id; }

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public Boolean isIsLead() { return is_lead; }
    public void setIsLead(Boolean is_lead) { this.is_lead = is_lead; }

    public java.time.OffsetDateTime getJoinedAt() { return joined_at; }
    public void setJoinedAt(java.time.OffsetDateTime joined_at) { this.joined_at = joined_at; }

    public Long getAddedBy() { return added_by; }
    public void setAddedBy(Long added_by) { this.added_by = added_by; }

    public java.time.OffsetDateTime getExpiresAt() { return expires_at; }
    public void setExpiresAt(java.time.OffsetDateTime expires_at) { this.expires_at = expires_at; }
}
