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
@Table(name = "guest_documents")
public class GuestDocumentsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "guest_id", columnDefinition = "bigint")
    private Long guest_id;

    @Column(name = "document_type", columnDefinition = "varchar(50)")
    private String document_type;

    @Column(name = "document_number", columnDefinition = "varchar(100)")
    private String document_number;

    @Column(name = "file_url", columnDefinition = "text")
    private String file_url;

    @Column(name = "is_verified", columnDefinition = "boolean DEFAULT false")
    private Boolean is_verified;

    @Column(name = "verified_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime verified_at;

    @Column(name = "verified_by", columnDefinition = "bigint")
    private Long verified_by;

    @Column(name = "expires_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime expires_at;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getDocumentType() { return document_type; }
    public void setDocumentType(String document_type) { this.document_type = document_type; }

    public String getDocumentNumber() { return document_number; }
    public void setDocumentNumber(String document_number) { this.document_number = document_number; }

    public String getFileUrl() { return file_url; }
    public void setFileUrl(String file_url) { this.file_url = file_url; }

    public Boolean isIsVerified() { return is_verified; }
    public void setIsVerified(Boolean is_verified) { this.is_verified = is_verified; }

    public java.time.OffsetDateTime getVerifiedAt() { return verified_at; }
    public void setVerifiedAt(java.time.OffsetDateTime verified_at) { this.verified_at = verified_at; }

    public Long getVerifiedBy() { return verified_by; }
    public void setVerifiedBy(Long verified_by) { this.verified_by = verified_by; }

    public java.time.OffsetDateTime getExpiresAt() { return expires_at; }
    public void setExpiresAt(java.time.OffsetDateTime expires_at) { this.expires_at = expires_at; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
