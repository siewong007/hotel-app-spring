package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "corporate_accounts",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"company_registration"})})
public class CorporateAccountsEntity {

    @Id
    @Column(name = "id")
    private java.util.UUID id;

    @Column(name = "name", columnDefinition = "varchar(255)")
    private String name;

    @Column(name = "company_registration", columnDefinition = "varchar(100)")
    private String company_registration;

    @Column(name = "tax_id", columnDefinition = "varchar(100)")
    private String tax_id;

    @Column(name = "industry", columnDefinition = "varchar(100)")
    private String industry;

    @Column(name = "billing_address", columnDefinition = "text")
    private String billing_address;

    @Column(name = "billing_email", columnDefinition = "varchar(255)")
    private String billing_email;

    @Column(name = "billing_phone", columnDefinition = "varchar(20)")
    private String billing_phone;

    @Column(name = "credit_limit", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal credit_limit;

    @Column(name = "credit_balance", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal credit_balance;

    @Column(name = "payment_terms", columnDefinition = "varchar(50)")
    private String payment_terms;

    @Column(name = "discount_percentage", columnDefinition = "numeric(5,2)")
    private java.math.BigDecimal discount_percentage;

    @Column(name = "contract_start")
    private java.time.LocalDate contract_start;

    @Column(name = "contract_end")
    private java.time.LocalDate contract_end;

    @Column(name = "is_active")
    private Boolean is_active;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "created_by")
    private Long created_by;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCompanyRegistration() { return company_registration; }
    public void setCompanyRegistration(String company_registration) { this.company_registration = company_registration; }

    public String getTaxId() { return tax_id; }
    public void setTaxId(String tax_id) { this.tax_id = tax_id; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getBillingAddress() { return billing_address; }
    public void setBillingAddress(String billing_address) { this.billing_address = billing_address; }

    public String getBillingEmail() { return billing_email; }
    public void setBillingEmail(String billing_email) { this.billing_email = billing_email; }

    public String getBillingPhone() { return billing_phone; }
    public void setBillingPhone(String billing_phone) { this.billing_phone = billing_phone; }

    public java.math.BigDecimal getCreditLimit() { return credit_limit; }
    public void setCreditLimit(java.math.BigDecimal credit_limit) { this.credit_limit = credit_limit; }

    public java.math.BigDecimal getCreditBalance() { return credit_balance; }
    public void setCreditBalance(java.math.BigDecimal credit_balance) { this.credit_balance = credit_balance; }

    public String getPaymentTerms() { return payment_terms; }
    public void setPaymentTerms(String payment_terms) { this.payment_terms = payment_terms; }

    public java.math.BigDecimal getDiscountPercentage() { return discount_percentage; }
    public void setDiscountPercentage(java.math.BigDecimal discount_percentage) { this.discount_percentage = discount_percentage; }

    public java.time.LocalDate getContractStart() { return contract_start; }
    public void setContractStart(java.time.LocalDate contract_start) { this.contract_start = contract_start; }

    public java.time.LocalDate getContractEnd() { return contract_end; }
    public void setContractEnd(java.time.LocalDate contract_end) { this.contract_end = contract_end; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
