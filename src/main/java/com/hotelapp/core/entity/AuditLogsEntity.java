package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "audit_logs")
@IdClass(AuditLogsId.class)
public class AuditLogsEntity {

    @Id
    @Column(name = "id", columnDefinition = "bigint GENERATED ALWAYS AS IDENTITY")
    private Long id;

    @Column(name = "user_id", columnDefinition = "bigint")
    private Long user_id;

    @Column(name = "action", columnDefinition = "varchar(100)")
    private String action;

    @Column(name = "resource_type", columnDefinition = "varchar(50)")
    private String resource_type;

    @Column(name = "resource_id", columnDefinition = "bigint")
    private Long resource_id;

    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ip_address;

    @Column(name = "user_agent", columnDefinition = "text")
    private String user_agent;

    @Id
    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getResourceType() { return resource_type; }
    public void setResourceType(String resource_type) { this.resource_type = resource_type; }

    public Long getResourceId() { return resource_id; }
    public void setResourceId(Long resource_id) { this.resource_id = resource_id; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getIpAddress() { return ip_address; }
    public void setIpAddress(String ip_address) { this.ip_address = ip_address; }

    public String getUserAgent() { return user_agent; }
    public void setUserAgent(String user_agent) { this.user_agent = user_agent; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
