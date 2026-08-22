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
@Table(name = "notification_subscriptions",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"guest_id", "channel", "topic"})})
public class NotificationSubscriptionsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "guest_id")
    private Long guest_id;

    @Column(name = "channel", columnDefinition = "varchar(16)")
    private String channel;

    @Column(name = "topic", columnDefinition = "varchar(32)")
    private String topic;

    @Column(name = "subscribed")
    private Boolean subscribed;

    @Column(name = "source", columnDefinition = "varchar(32)")
    private String source;

    @Column(name = "policy_version", columnDefinition = "varchar(32)")
    private String policy_version;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public Boolean isSubscribed() { return subscribed; }
    public void setSubscribed(Boolean subscribed) { this.subscribed = subscribed; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getPolicyVersion() { return policy_version; }
    public void setPolicyVersion(String policy_version) { this.policy_version = policy_version; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
