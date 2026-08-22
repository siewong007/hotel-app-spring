package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "loyalty_members",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"guest_id"}), @UniqueConstraint(columnNames = {"member_number"})})
public class LoyaltyMembersEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "guest_id")
    private Long guest_id;

    @Column(name = "member_number", columnDefinition = "varchar(50)")
    private String member_number;

    @Column(name = "status", columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "enrolled_at")
    private java.time.OffsetDateTime enrolled_at;

    @Column(name = "closed_at")
    private java.time.OffsetDateTime closed_at;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getMemberNumber() { return member_number; }
    public void setMemberNumber(String member_number) { this.member_number = member_number; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public java.time.OffsetDateTime getEnrolledAt() { return enrolled_at; }
    public void setEnrolledAt(java.time.OffsetDateTime enrolled_at) { this.enrolled_at = enrolled_at; }

    public java.time.OffsetDateTime getClosedAt() { return closed_at; }
    public void setClosedAt(java.time.OffsetDateTime closed_at) { this.closed_at = closed_at; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
