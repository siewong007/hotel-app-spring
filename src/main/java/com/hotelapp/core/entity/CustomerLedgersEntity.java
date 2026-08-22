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
@Table(name = "customer_ledgers",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"invoice_number"})})
public class CustomerLedgersEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "company_name", columnDefinition = "varchar(255)")
    private String company_name;

    @Column(name = "company_registration_number", columnDefinition = "varchar(100)")
    private String company_registration_number;

    @Column(name = "contact_person", columnDefinition = "varchar(255)")
    private String contact_person;

    @Column(name = "contact_email", columnDefinition = "varchar(255)")
    private String contact_email;

    @Column(name = "contact_phone", columnDefinition = "varchar(50)")
    private String contact_phone;

    @Column(name = "billing_address_line1", columnDefinition = "varchar(255)")
    private String billing_address_line1;

    @Column(name = "billing_city", columnDefinition = "varchar(100)")
    private String billing_city;

    @Column(name = "billing_state", columnDefinition = "varchar(100)")
    private String billing_state;

    @Column(name = "billing_postal_code", columnDefinition = "varchar(20)")
    private String billing_postal_code;

    @Column(name = "billing_country", columnDefinition = "varchar(100)")
    private String billing_country;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "expense_type", columnDefinition = "varchar(100)")
    private String expense_type;

    @Column(name = "amount", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal amount;

    @Column(name = "currency", columnDefinition = "varchar(3)")
    private String currency;

    @Column(name = "status", columnDefinition = "varchar(50)")
    private String status;

    @Column(name = "paid_amount", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal paid_amount;

    @Column(name = "payment_method", columnDefinition = "varchar(50)")
    private String payment_method;

    @Column(name = "payment_reference", columnDefinition = "varchar(255)")
    private String payment_reference;

    @Column(name = "payment_date")
    private java.time.OffsetDateTime payment_date;

    @Column(name = "booking_id")
    private Long booking_id;

    @Column(name = "guest_id")
    private Long guest_id;

    @Column(name = "invoice_number", columnDefinition = "varchar(100)")
    private String invoice_number;

    @Column(name = "invoice_date")
    private java.time.LocalDate invoice_date;

    @Column(name = "due_date")
    private java.time.LocalDate due_date;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "internal_notes", columnDefinition = "text")
    private String internal_notes;

    @Column(name = "folio_number", columnDefinition = "varchar(50)")
    private String folio_number;

    @Column(name = "folio_type", columnDefinition = "varchar(50)")
    private String folio_type;

    @Column(name = "transaction_type", columnDefinition = "varchar(20)")
    private String transaction_type;

    @Column(name = "post_type", columnDefinition = "varchar(50)")
    private String post_type;

    @Column(name = "department_code", columnDefinition = "varchar(20)")
    private String department_code;

    @Column(name = "transaction_code", columnDefinition = "varchar(20)")
    private String transaction_code;

    @Column(name = "room_number", columnDefinition = "varchar(20)")
    private String room_number;

    @Column(name = "posting_date")
    private java.time.LocalDate posting_date;

    @Column(name = "transaction_date")
    private java.time.LocalDate transaction_date;

    @Column(name = "reference_number", columnDefinition = "varchar(100)")
    private String reference_number;

    @Column(name = "cashier_id")
    private Long cashier_id;

    @Column(name = "is_reversal")
    private Boolean is_reversal;

    @Column(name = "original_transaction_id")
    private Long original_transaction_id;

    @Column(name = "reversal_reason", columnDefinition = "text")
    private String reversal_reason;

    @Column(name = "tax_amount", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal tax_amount;

    @Column(name = "service_charge", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal service_charge;

    @Column(name = "net_amount", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal net_amount;

    @Column(name = "is_posted")
    private Boolean is_posted;

    @Column(name = "posted_at")
    private java.time.OffsetDateTime posted_at;

    @Column(name = "void_at")
    private java.time.OffsetDateTime void_at;

    @Column(name = "void_by")
    private Long void_by;

    @Column(name = "void_reason", columnDefinition = "text")
    private String void_reason;

    @Column(name = "created_by")
    private Long created_by;

    @Column(name = "updated_by")
    private Long updated_by;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    @Column(name = "balance_due", columnDefinition = "numeric(10,2) GENERATED ALWAYS AS ((amount - paid_amount))")
    private java.math.BigDecimal balance_due;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCompanyName() { return company_name; }
    public void setCompanyName(String company_name) { this.company_name = company_name; }

    public String getCompanyRegistrationNumber() { return company_registration_number; }
    public void setCompanyRegistrationNumber(String company_registration_number) { this.company_registration_number = company_registration_number; }

    public String getContactPerson() { return contact_person; }
    public void setContactPerson(String contact_person) { this.contact_person = contact_person; }

    public String getContactEmail() { return contact_email; }
    public void setContactEmail(String contact_email) { this.contact_email = contact_email; }

    public String getContactPhone() { return contact_phone; }
    public void setContactPhone(String contact_phone) { this.contact_phone = contact_phone; }

    public String getBillingAddressLine1() { return billing_address_line1; }
    public void setBillingAddressLine1(String billing_address_line1) { this.billing_address_line1 = billing_address_line1; }

    public String getBillingCity() { return billing_city; }
    public void setBillingCity(String billing_city) { this.billing_city = billing_city; }

    public String getBillingState() { return billing_state; }
    public void setBillingState(String billing_state) { this.billing_state = billing_state; }

    public String getBillingPostalCode() { return billing_postal_code; }
    public void setBillingPostalCode(String billing_postal_code) { this.billing_postal_code = billing_postal_code; }

    public String getBillingCountry() { return billing_country; }
    public void setBillingCountry(String billing_country) { this.billing_country = billing_country; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getExpenseType() { return expense_type; }
    public void setExpenseType(String expense_type) { this.expense_type = expense_type; }

    public java.math.BigDecimal getAmount() { return amount; }
    public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public java.math.BigDecimal getPaidAmount() { return paid_amount; }
    public void setPaidAmount(java.math.BigDecimal paid_amount) { this.paid_amount = paid_amount; }

    public String getPaymentMethod() { return payment_method; }
    public void setPaymentMethod(String payment_method) { this.payment_method = payment_method; }

    public String getPaymentReference() { return payment_reference; }
    public void setPaymentReference(String payment_reference) { this.payment_reference = payment_reference; }

    public java.time.OffsetDateTime getPaymentDate() { return payment_date; }
    public void setPaymentDate(java.time.OffsetDateTime payment_date) { this.payment_date = payment_date; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getInvoiceNumber() { return invoice_number; }
    public void setInvoiceNumber(String invoice_number) { this.invoice_number = invoice_number; }

    public java.time.LocalDate getInvoiceDate() { return invoice_date; }
    public void setInvoiceDate(java.time.LocalDate invoice_date) { this.invoice_date = invoice_date; }

    public java.time.LocalDate getDueDate() { return due_date; }
    public void setDueDate(java.time.LocalDate due_date) { this.due_date = due_date; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getInternalNotes() { return internal_notes; }
    public void setInternalNotes(String internal_notes) { this.internal_notes = internal_notes; }

    public String getFolioNumber() { return folio_number; }
    public void setFolioNumber(String folio_number) { this.folio_number = folio_number; }

    public String getFolioType() { return folio_type; }
    public void setFolioType(String folio_type) { this.folio_type = folio_type; }

    public String getTransactionType() { return transaction_type; }
    public void setTransactionType(String transaction_type) { this.transaction_type = transaction_type; }

    public String getPostType() { return post_type; }
    public void setPostType(String post_type) { this.post_type = post_type; }

    public String getDepartmentCode() { return department_code; }
    public void setDepartmentCode(String department_code) { this.department_code = department_code; }

    public String getTransactionCode() { return transaction_code; }
    public void setTransactionCode(String transaction_code) { this.transaction_code = transaction_code; }

    public String getRoomNumber() { return room_number; }
    public void setRoomNumber(String room_number) { this.room_number = room_number; }

    public java.time.LocalDate getPostingDate() { return posting_date; }
    public void setPostingDate(java.time.LocalDate posting_date) { this.posting_date = posting_date; }

    public java.time.LocalDate getTransactionDate() { return transaction_date; }
    public void setTransactionDate(java.time.LocalDate transaction_date) { this.transaction_date = transaction_date; }

    public String getReferenceNumber() { return reference_number; }
    public void setReferenceNumber(String reference_number) { this.reference_number = reference_number; }

    public Long getCashierId() { return cashier_id; }
    public void setCashierId(Long cashier_id) { this.cashier_id = cashier_id; }

    public Boolean isIsReversal() { return is_reversal; }
    public void setIsReversal(Boolean is_reversal) { this.is_reversal = is_reversal; }

    public Long getOriginalTransactionId() { return original_transaction_id; }
    public void setOriginalTransactionId(Long original_transaction_id) { this.original_transaction_id = original_transaction_id; }

    public String getReversalReason() { return reversal_reason; }
    public void setReversalReason(String reversal_reason) { this.reversal_reason = reversal_reason; }

    public java.math.BigDecimal getTaxAmount() { return tax_amount; }
    public void setTaxAmount(java.math.BigDecimal tax_amount) { this.tax_amount = tax_amount; }

    public java.math.BigDecimal getServiceCharge() { return service_charge; }
    public void setServiceCharge(java.math.BigDecimal service_charge) { this.service_charge = service_charge; }

    public java.math.BigDecimal getNetAmount() { return net_amount; }
    public void setNetAmount(java.math.BigDecimal net_amount) { this.net_amount = net_amount; }

    public Boolean isIsPosted() { return is_posted; }
    public void setIsPosted(Boolean is_posted) { this.is_posted = is_posted; }

    public java.time.OffsetDateTime getPostedAt() { return posted_at; }
    public void setPostedAt(java.time.OffsetDateTime posted_at) { this.posted_at = posted_at; }

    public java.time.OffsetDateTime getVoidAt() { return void_at; }
    public void setVoidAt(java.time.OffsetDateTime void_at) { this.void_at = void_at; }

    public Long getVoidBy() { return void_by; }
    public void setVoidBy(Long void_by) { this.void_by = void_by; }

    public String getVoidReason() { return void_reason; }
    public void setVoidReason(String void_reason) { this.void_reason = void_reason; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public Long getUpdatedBy() { return updated_by; }
    public void setUpdatedBy(Long updated_by) { this.updated_by = updated_by; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }

    public java.math.BigDecimal getBalanceDue() { return balance_due; }
    public void setBalanceDue(java.math.BigDecimal balance_due) { this.balance_due = balance_due; }
}
