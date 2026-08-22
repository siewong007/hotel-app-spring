package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "passkeys",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"credential_id"})})
public class PasskeysEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private java.util.UUID id;

    @Column(name = "user_id", columnDefinition = "bigint")
    private Long user_id;

    @Column(name = "credential_id", columnDefinition = "bytea")
    private String credential_id;

    @Column(name = "public_key", columnDefinition = "bytea")
    private String public_key;

    @Column(name = "counter", columnDefinition = "bigint DEFAULT 0")
    private Long counter;

    @Column(name = "transports", columnDefinition = "text[]")
    private String[] transports;

    @Column(name = "device_type", columnDefinition = "varchar(50)")
    private String device_type;

    @Column(name = "device_name", columnDefinition = "varchar(255)")
    private String device_name;

    @Column(name = "aaguid", columnDefinition = "uuid")
    private java.util.UUID aaguid;

    @Column(name = "backup_eligible", columnDefinition = "boolean DEFAULT false")
    private Boolean backup_eligible;

    @Column(name = "backup_state", columnDefinition = "boolean DEFAULT false")
    private Boolean backup_state;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "last_used_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime last_used_at;

    @Column(name = "is_active", columnDefinition = "boolean DEFAULT true")
    private Boolean is_active;

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public String getCredentialId() { return credential_id; }
    public void setCredentialId(String credential_id) { this.credential_id = credential_id; }

    public String getPublicKey() { return public_key; }
    public void setPublicKey(String public_key) { this.public_key = public_key; }

    public Long getCounter() { return counter; }
    public void setCounter(Long counter) { this.counter = counter; }

    public String[] getTransports() { return transports; }
    public void setTransports(String[] transports) { this.transports = transports; }

    public String getDeviceType() { return device_type; }
    public void setDeviceType(String device_type) { this.device_type = device_type; }

    public String getDeviceName() { return device_name; }
    public void setDeviceName(String device_name) { this.device_name = device_name; }

    public java.util.UUID getAaguid() { return aaguid; }
    public void setAaguid(java.util.UUID aaguid) { this.aaguid = aaguid; }

    public Boolean isBackupEligible() { return backup_eligible; }
    public void setBackupEligible(Boolean backup_eligible) { this.backup_eligible = backup_eligible; }

    public Boolean isBackupState() { return backup_state; }
    public void setBackupState(Boolean backup_state) { this.backup_state = backup_state; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getLastUsedAt() { return last_used_at; }
    public void setLastUsedAt(java.time.OffsetDateTime last_used_at) { this.last_used_at = last_used_at; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }
}
