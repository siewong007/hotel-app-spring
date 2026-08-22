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
@Table(name = "guest_notes")
public class GuestNotesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "guest_id", columnDefinition = "bigint")
    private Long guest_id;

    @Column(name = "note_type", columnDefinition = "varchar(50)")
    private String note_type;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "is_alert", columnDefinition = "boolean DEFAULT false")
    private Boolean is_alert;

    @Column(name = "is_private", columnDefinition = "boolean DEFAULT false")
    private Boolean is_private;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long created_by;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getNoteType() { return note_type; }
    public void setNoteType(String note_type) { this.note_type = note_type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Boolean isIsAlert() { return is_alert; }
    public void setIsAlert(Boolean is_alert) { this.is_alert = is_alert; }

    public Boolean isIsPrivate() { return is_private; }
    public void setIsPrivate(Boolean is_private) { this.is_private = is_private; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
