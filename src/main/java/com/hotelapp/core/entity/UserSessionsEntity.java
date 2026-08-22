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
@Table(name = "user_sessions",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"session_id"})})
public class UserSessionsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "session_id")
    private java.util.UUID session_id;

    @Column(name = "user_id")
    private Long user_id;

    @Column(name = "ip_address")
    private String ip_address;

    @Column(name = "user_agent", columnDefinition = "text")
    private String user_agent;

    @Column(name = "device_info")
    private String device_info;

    @Column(name = "started_at")
    private java.time.OffsetDateTime started_at;

    @Column(name = "last_activity_at")
    private java.time.OffsetDateTime last_activity_at;

    @Column(name = "expires_at")
    private java.time.OffsetDateTime expires_at;

    @Column(name = "is_active")
    private Boolean is_active;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.util.UUID getSessionId() { return session_id; }
    public void setSessionId(java.util.UUID session_id) { this.session_id = session_id; }

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public String getIpAddress() { return ip_address; }
    public void setIpAddress(String ip_address) { this.ip_address = ip_address; }

    public String getUserAgent() { return user_agent; }
    public void setUserAgent(String user_agent) { this.user_agent = user_agent; }

    public String getDeviceInfo() { return device_info; }
    public void setDeviceInfo(String device_info) { this.device_info = device_info; }

    public java.time.OffsetDateTime getStartedAt() { return started_at; }
    public void setStartedAt(java.time.OffsetDateTime started_at) { this.started_at = started_at; }

    public java.time.OffsetDateTime getLastActivityAt() { return last_activity_at; }
    public void setLastActivityAt(java.time.OffsetDateTime last_activity_at) { this.last_activity_at = last_activity_at; }

    public java.time.OffsetDateTime getExpiresAt() { return expires_at; }
    public void setExpiresAt(java.time.OffsetDateTime expires_at) { this.expires_at = expires_at; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }
}
