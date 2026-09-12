package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "refresh_tokens",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"token_hash"})})
public class RefreshTokensEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private java.util.UUID id;

    @Column(name = "user_id", columnDefinition = "bigint")
    private Long user_id;

    @Column(name = "token_hash", columnDefinition = "varchar(255)")
    private String token_hash;

    @Column(name = "device_info", columnDefinition = "jsonb")
    private String device_info;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ip_address;

    @Column(name = "user_agent", columnDefinition = "text")
    private String user_agent;

    @Column(name = "expires_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime expires_at;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "last_used_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime last_used_at;

    @Column(name = "is_revoked", columnDefinition = "boolean DEFAULT false")
    private Boolean is_revoked;

    @Column(name = "revoked_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime revoked_at;

    @Column(name = "revoked_by", columnDefinition = "bigint")
    private Long revoked_by;

    @Column(name = "client_timezone", columnDefinition = "text")
    private String client_timezone;

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public String getTokenHash() { return token_hash; }
    public void setTokenHash(String token_hash) { this.token_hash = token_hash; }

    public String getDeviceInfo() { return device_info; }
    public void setDeviceInfo(String device_info) { this.device_info = device_info; }

    public String getIpAddress() { return ip_address; }
    public void setIpAddress(String ip_address) { this.ip_address = ip_address; }

    public String getUserAgent() { return user_agent; }
    public void setUserAgent(String user_agent) { this.user_agent = user_agent; }

    public java.time.OffsetDateTime getExpiresAt() { return expires_at; }
    public void setExpiresAt(java.time.OffsetDateTime expires_at) { this.expires_at = expires_at; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getLastUsedAt() { return last_used_at; }
    public void setLastUsedAt(java.time.OffsetDateTime last_used_at) { this.last_used_at = last_used_at; }

    public Boolean isIsRevoked() { return is_revoked; }
    public void setIsRevoked(Boolean is_revoked) { this.is_revoked = is_revoked; }

    public java.time.OffsetDateTime getRevokedAt() { return revoked_at; }
    public void setRevokedAt(java.time.OffsetDateTime revoked_at) { this.revoked_at = revoked_at; }

    public Long getRevokedBy() { return revoked_by; }
    public void setRevokedBy(Long revoked_by) { this.revoked_by = revoked_by; }

    public String getClientTimezone() { return client_timezone; }
    public void setClientTimezone(String client_timezone) { this.client_timezone = client_timezone; }
}
