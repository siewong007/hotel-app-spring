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
@Table(name = "system_settings",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"key"})})
public class SystemSettingsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "key", columnDefinition = "varchar(100)")
    private String key;

    @Column(name = "value", columnDefinition = "text")
    private String value;

    @Column(name = "value_type", columnDefinition = "varchar(20)")
    private String value_type;

    @Column(name = "category", columnDefinition = "varchar(50)")
    private String category;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_public", columnDefinition = "boolean DEFAULT false")
    private Boolean is_public;

    @Column(name = "is_encrypted", columnDefinition = "boolean DEFAULT false")
    private Boolean is_encrypted;

    @Column(name = "validation_pattern", columnDefinition = "varchar(255)")
    private String validation_pattern;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    @Column(name = "updated_by", columnDefinition = "bigint")
    private Long updated_by;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getValueType() { return value_type; }
    public void setValueType(String value_type) { this.value_type = value_type; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean isIsPublic() { return is_public; }
    public void setIsPublic(Boolean is_public) { this.is_public = is_public; }

    public Boolean isIsEncrypted() { return is_encrypted; }
    public void setIsEncrypted(Boolean is_encrypted) { this.is_encrypted = is_encrypted; }

    public String getValidationPattern() { return validation_pattern; }
    public void setValidationPattern(String validation_pattern) { this.validation_pattern = validation_pattern; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }

    public Long getUpdatedBy() { return updated_by; }
    public void setUpdatedBy(Long updated_by) { this.updated_by = updated_by; }
}
