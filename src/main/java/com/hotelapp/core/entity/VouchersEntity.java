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
@Table(name = "vouchers",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"code"})})
public class VouchersEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "promotion_id", columnDefinition = "bigint")
    private Long promotion_id;

    @Column(name = "guest_id", columnDefinition = "bigint")
    private Long guest_id;

    @Column(name = "code", columnDefinition = "varchar(64)")
    private String code;

    @Column(name = "status", columnDefinition = "varchar(16)")
    private String status;

    @Column(name = "source", columnDefinition = "varchar(16)")
    private String source;

    @Column(name = "expires_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime expires_at;

    @Column(name = "redeemed_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime redeemed_at;

    @Column(name = "revoked_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime revoked_at;

    @Column(name = "revoked_by", columnDefinition = "bigint")
    private Long revoked_by;

    @Column(name = "revocation_reason", columnDefinition = "text")
    private String revocation_reason;

    @Column(name = "issued_by", columnDefinition = "bigint")
    private Long issued_by;

    @Column(name = "claimed_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime claimed_at;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    @Column(name = "source_reference", columnDefinition = "varchar(64)")
    private String source_reference;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPromotionId() { return promotion_id; }
    public void setPromotionId(Long promotion_id) { this.promotion_id = promotion_id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public java.time.OffsetDateTime getExpiresAt() { return expires_at; }
    public void setExpiresAt(java.time.OffsetDateTime expires_at) { this.expires_at = expires_at; }

    public java.time.OffsetDateTime getRedeemedAt() { return redeemed_at; }
    public void setRedeemedAt(java.time.OffsetDateTime redeemed_at) { this.redeemed_at = redeemed_at; }

    public java.time.OffsetDateTime getRevokedAt() { return revoked_at; }
    public void setRevokedAt(java.time.OffsetDateTime revoked_at) { this.revoked_at = revoked_at; }

    public Long getRevokedBy() { return revoked_by; }
    public void setRevokedBy(Long revoked_by) { this.revoked_by = revoked_by; }

    public String getRevocationReason() { return revocation_reason; }
    public void setRevocationReason(String revocation_reason) { this.revocation_reason = revocation_reason; }

    public Long getIssuedBy() { return issued_by; }
    public void setIssuedBy(Long issued_by) { this.issued_by = issued_by; }

    public java.time.OffsetDateTime getClaimedAt() { return claimed_at; }
    public void setClaimedAt(java.time.OffsetDateTime claimed_at) { this.claimed_at = claimed_at; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }

    public String getSourceReference() { return source_reference; }
    public void setSourceReference(String source_reference) { this.source_reference = source_reference; }
}
