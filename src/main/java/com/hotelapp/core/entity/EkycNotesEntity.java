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
@Table(name = "ekyc_notes")
public class EkycNotesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "application_id")
    private Long application_id;

    @Column(name = "note_type", columnDefinition = "varchar(40)")
    private String note_type;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "customer_visible")
    private Boolean customer_visible;

    @Column(name = "created_by")
    private Long created_by;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getApplicationId() { return application_id; }
    public void setApplicationId(Long application_id) { this.application_id = application_id; }

    public String getNoteType() { return note_type; }
    public void setNoteType(String note_type) { this.note_type = note_type; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Boolean isCustomerVisible() { return customer_visible; }
    public void setCustomerVisible(Boolean customer_visible) { this.customer_visible = customer_visible; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
