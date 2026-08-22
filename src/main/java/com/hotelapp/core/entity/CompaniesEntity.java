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
@Table(name = "companies")
public class CompaniesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "company_name", columnDefinition = "varchar(255)")
    private String company_name;

    @Column(name = "registration_number", columnDefinition = "varchar(100)")
    private String registration_number;

    @Column(name = "contact_person", columnDefinition = "varchar(255)")
    private String contact_person;

    @Column(name = "contact_email", columnDefinition = "varchar(255)")
    private String contact_email;

    @Column(name = "contact_phone", columnDefinition = "varchar(50)")
    private String contact_phone;

    @Column(name = "billing_address", columnDefinition = "text")
    private String billing_address;

    @Column(name = "billing_city", columnDefinition = "varchar(100)")
    private String billing_city;

    @Column(name = "billing_state", columnDefinition = "varchar(100)")
    private String billing_state;

    @Column(name = "billing_postal_code", columnDefinition = "varchar(20)")
    private String billing_postal_code;

    @Column(name = "billing_country", columnDefinition = "varchar(100)")
    private String billing_country;

    @Column(name = "is_active")
    private Boolean is_active;

    @Column(name = "credit_limit", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal credit_limit;

    @Column(name = "payment_terms_days")
    private Integer payment_terms_days;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_by")
    private Long created_by;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCompanyName() { return company_name; }
    public void setCompanyName(String company_name) { this.company_name = company_name; }

    public String getRegistrationNumber() { return registration_number; }
    public void setRegistrationNumber(String registration_number) { this.registration_number = registration_number; }

    public String getContactPerson() { return contact_person; }
    public void setContactPerson(String contact_person) { this.contact_person = contact_person; }

    public String getContactEmail() { return contact_email; }
    public void setContactEmail(String contact_email) { this.contact_email = contact_email; }

    public String getContactPhone() { return contact_phone; }
    public void setContactPhone(String contact_phone) { this.contact_phone = contact_phone; }

    public String getBillingAddress() { return billing_address; }
    public void setBillingAddress(String billing_address) { this.billing_address = billing_address; }

    public String getBillingCity() { return billing_city; }
    public void setBillingCity(String billing_city) { this.billing_city = billing_city; }

    public String getBillingState() { return billing_state; }
    public void setBillingState(String billing_state) { this.billing_state = billing_state; }

    public String getBillingPostalCode() { return billing_postal_code; }
    public void setBillingPostalCode(String billing_postal_code) { this.billing_postal_code = billing_postal_code; }

    public String getBillingCountry() { return billing_country; }
    public void setBillingCountry(String billing_country) { this.billing_country = billing_country; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public java.math.BigDecimal getCreditLimit() { return credit_limit; }
    public void setCreditLimit(java.math.BigDecimal credit_limit) { this.credit_limit = credit_limit; }

    public Integer getPaymentTermsDays() { return payment_terms_days; }
    public void setPaymentTermsDays(Integer payment_terms_days) { this.payment_terms_days = payment_terms_days; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
