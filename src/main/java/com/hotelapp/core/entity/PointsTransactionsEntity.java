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
@Table(name = "points_transactions")
public class PointsTransactionsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "membership_id", columnDefinition = "bigint")
    private Long membership_id;

    @Column(name = "transaction_type", columnDefinition = "varchar(20)")
    private String transaction_type;

    @Column(name = "points", columnDefinition = "integer")
    private Integer points;

    @Column(name = "balance_after", columnDefinition = "integer")
    private Integer balance_after;

    @Column(name = "reference_type", columnDefinition = "varchar(50)")
    private String reference_type;

    @Column(name = "reference_id", columnDefinition = "bigint")
    private Long reference_id;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long created_by;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMembershipId() { return membership_id; }
    public void setMembershipId(Long membership_id) { this.membership_id = membership_id; }

    public String getTransactionType() { return transaction_type; }
    public void setTransactionType(String transaction_type) { this.transaction_type = transaction_type; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public Integer getBalanceAfter() { return balance_after; }
    public void setBalanceAfter(Integer balance_after) { this.balance_after = balance_after; }

    public String getReferenceType() { return reference_type; }
    public void setReferenceType(String reference_type) { this.reference_type = reference_type; }

    public Long getReferenceId() { return reference_id; }
    public void setReferenceId(Long reference_id) { this.reference_id = reference_id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }
}
