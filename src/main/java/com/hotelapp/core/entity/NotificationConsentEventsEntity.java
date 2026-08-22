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
@Table(name = "notification_consent_events")
public class NotificationConsentEventsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "guest_id", columnDefinition = "bigint")
    private Long guest_id;

    @Column(name = "channel", columnDefinition = "varchar(16)")
    private String channel;

    @Column(name = "topic", columnDefinition = "varchar(32)")
    private String topic;

    @Column(name = "action", columnDefinition = "varchar(8)")
    private String action;

    @Column(name = "source", columnDefinition = "varchar(32)")
    private String source;

    @Column(name = "policy_version", columnDefinition = "varchar(32)")
    private String policy_version;

    @Column(name = "actor_type", columnDefinition = "varchar(8)")
    private String actor_type;

    @Column(name = "actor_user_id", columnDefinition = "bigint")
    private Long actor_user_id;

    @Column(name = "ip_address", columnDefinition = "varchar(64)")
    private String ip_address;

    @Column(name = "user_agent", columnDefinition = "text")
    private String user_agent;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getPolicyVersion() { return policy_version; }
    public void setPolicyVersion(String policy_version) { this.policy_version = policy_version; }

    public String getActorType() { return actor_type; }
    public void setActorType(String actor_type) { this.actor_type = actor_type; }

    public Long getActorUserId() { return actor_user_id; }
    public void setActorUserId(Long actor_user_id) { this.actor_user_id = actor_user_id; }

    public String getIpAddress() { return ip_address; }
    public void setIpAddress(String ip_address) { this.ip_address = ip_address; }

    public String getUserAgent() { return user_agent; }
    public void setUserAgent(String user_agent) { this.user_agent = user_agent; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
