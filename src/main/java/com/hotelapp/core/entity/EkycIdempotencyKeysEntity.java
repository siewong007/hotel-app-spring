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
@Table(name = "ekyc_idempotency_keys",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"application_id", "actor_id", "idempotency_key"})})
public class EkycIdempotencyKeysEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "application_id")
    private Long application_id;

    @Column(name = "actor_id")
    private Long actor_id;

    @Column(name = "idempotency_key", columnDefinition = "varchar(160)")
    private String idempotency_key;

    @Column(name = "action", columnDefinition = "varchar(100)")
    private String action;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getApplicationId() { return application_id; }
    public void setApplicationId(Long application_id) { this.application_id = application_id; }

    public Long getActorId() { return actor_id; }
    public void setActorId(Long actor_id) { this.actor_id = actor_id; }

    public String getIdempotencyKey() { return idempotency_key; }
    public void setIdempotencyKey(String idempotency_key) { this.idempotency_key = idempotency_key; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
