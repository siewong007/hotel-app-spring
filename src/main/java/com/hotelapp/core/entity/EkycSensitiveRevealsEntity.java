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
@Table(name = "ekyc_sensitive_reveals")
public class EkycSensitiveRevealsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "application_id")
    private Long application_id;

    @Column(name = "actor_id")
    private Long actor_id;

    @Column(name = "field_name", columnDefinition = "varchar(80)")
    private String field_name;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getApplicationId() { return application_id; }
    public void setApplicationId(Long application_id) { this.application_id = application_id; }

    public Long getActorId() { return actor_id; }
    public void setActorId(Long actor_id) { this.actor_id = actor_id; }

    public String getFieldName() { return field_name; }
    public void setFieldName(String field_name) { this.field_name = field_name; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
