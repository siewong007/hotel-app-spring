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
@Table(name = "email_templates",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"code"})})
public class EmailTemplatesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "code", columnDefinition = "varchar(50)")
    private String code;

    @Column(name = "name", columnDefinition = "varchar(100)")
    private String name;

    @Column(name = "subject", columnDefinition = "varchar(255)")
    private String subject;

    @Column(name = "body_html", columnDefinition = "text")
    private String body_html;

    @Column(name = "body_text", columnDefinition = "text")
    private String body_text;

    @Column(name = "variables", columnDefinition = "jsonb")
    private String variables;

    @Column(name = "is_active", columnDefinition = "boolean DEFAULT true")
    private Boolean is_active;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBodyHtml() { return body_html; }
    public void setBodyHtml(String body_html) { this.body_html = body_html; }

    public String getBodyText() { return body_text; }
    public void setBodyText(String body_text) { this.body_text = body_text; }

    public String getVariables() { return variables; }
    public void setVariables(String variables) { this.variables = variables; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
