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
@Table(name = "email_campaigns")
public class EmailCampaignsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "name", columnDefinition = "varchar(160)")
    private String name;

    @Column(name = "campaign_type", columnDefinition = "varchar(16)")
    private String campaign_type;

    @Column(name = "topic", columnDefinition = "varchar(32)")
    private String topic;

    @Column(name = "status", columnDefinition = "varchar(16)")
    private String status;

    @Column(name = "subject", columnDefinition = "varchar(255)")
    private String subject;

    @Column(name = "body_html", columnDefinition = "text")
    private String body_html;

    @Column(name = "body_text", columnDefinition = "text")
    private String body_text;

    @Column(name = "template_id", columnDefinition = "bigint")
    private Long template_id;

    @Column(name = "promotion_id", columnDefinition = "bigint")
    private Long promotion_id;

    @Column(name = "scheduled_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime scheduled_at;

    @Column(name = "started_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime started_at;

    @Column(name = "completed_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime completed_at;

    @Column(name = "cancelled_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime cancelled_at;

    @Column(name = "total_recipients", columnDefinition = "integer")
    private Integer total_recipients;

    @Column(name = "sent_count", columnDefinition = "integer")
    private Integer sent_count;

    @Column(name = "failed_count", columnDefinition = "integer")
    private Integer failed_count;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long created_by;

    @Column(name = "cancelled_by", columnDefinition = "bigint")
    private Long cancelled_by;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCampaignType() { return campaign_type; }
    public void setCampaignType(String campaign_type) { this.campaign_type = campaign_type; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBodyHtml() { return body_html; }
    public void setBodyHtml(String body_html) { this.body_html = body_html; }

    public String getBodyText() { return body_text; }
    public void setBodyText(String body_text) { this.body_text = body_text; }

    public Long getTemplateId() { return template_id; }
    public void setTemplateId(Long template_id) { this.template_id = template_id; }

    public Long getPromotionId() { return promotion_id; }
    public void setPromotionId(Long promotion_id) { this.promotion_id = promotion_id; }

    public java.time.OffsetDateTime getScheduledAt() { return scheduled_at; }
    public void setScheduledAt(java.time.OffsetDateTime scheduled_at) { this.scheduled_at = scheduled_at; }

    public java.time.OffsetDateTime getStartedAt() { return started_at; }
    public void setStartedAt(java.time.OffsetDateTime started_at) { this.started_at = started_at; }

    public java.time.OffsetDateTime getCompletedAt() { return completed_at; }
    public void setCompletedAt(java.time.OffsetDateTime completed_at) { this.completed_at = completed_at; }

    public java.time.OffsetDateTime getCancelledAt() { return cancelled_at; }
    public void setCancelledAt(java.time.OffsetDateTime cancelled_at) { this.cancelled_at = cancelled_at; }

    public Integer getTotalRecipients() { return total_recipients; }
    public void setTotalRecipients(Integer total_recipients) { this.total_recipients = total_recipients; }

    public Integer getSentCount() { return sent_count; }
    public void setSentCount(Integer sent_count) { this.sent_count = sent_count; }

    public Integer getFailedCount() { return failed_count; }
    public void setFailedCount(Integer failed_count) { this.failed_count = failed_count; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public Long getCancelledBy() { return cancelled_by; }
    public void setCancelledBy(Long cancelled_by) { this.cancelled_by = cancelled_by; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
