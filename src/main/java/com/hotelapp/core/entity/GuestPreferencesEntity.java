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
@Table(name = "guest_preferences",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"guest_id", "category", "preference_key"})})
public class GuestPreferencesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "guest_id", columnDefinition = "bigint")
    private Long guest_id;

    @Column(name = "category", columnDefinition = "varchar(50)")
    private String category;

    @Column(name = "preference_key", columnDefinition = "varchar(100)")
    private String preference_key;

    @Column(name = "preference_value", columnDefinition = "text")
    private String preference_value;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPreferenceKey() { return preference_key; }
    public void setPreferenceKey(String preference_key) { this.preference_key = preference_key; }

    public String getPreferenceValue() { return preference_value; }
    public void setPreferenceValue(String preference_value) { this.preference_value = preference_value; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
