package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "ekyc_reason_codes")
public class EkycReasonCodesEntity {

    @Id
    @Column(name = "code", columnDefinition = "varchar(80)")
    private String code;

    @Column(name = "label", columnDefinition = "varchar(160)")
    private String label;

    @Column(name = "category", columnDefinition = "varchar(80)")
    private String category;

    @Column(name = "requires_details")
    private Boolean requires_details;

    @Column(name = "customer_message_template", columnDefinition = "text")
    private String customer_message_template;

    @Column(name = "is_active")
    private Boolean is_active;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Boolean isRequiresDetails() { return requires_details; }
    public void setRequiresDetails(Boolean requires_details) { this.requires_details = requires_details; }

    public String getCustomerMessageTemplate() { return customer_message_template; }
    public void setCustomerMessageTemplate(String customer_message_template) { this.customer_message_template = customer_message_template; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
