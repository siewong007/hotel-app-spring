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
@Table(name = "invoices",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"invoice_number"}), @UniqueConstraint(columnNames = {"uuid"})})
public class InvoicesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "uuid", columnDefinition = "uuid")
    private java.util.UUID uuid;

    @Column(name = "invoice_number", columnDefinition = "varchar(50)")
    private String invoice_number;

    @Column(name = "booking_id", columnDefinition = "bigint")
    private Long booking_id;

    @Column(name = "bill_to_guest_id", columnDefinition = "bigint")
    private Long bill_to_guest_id;

    @Column(name = "bill_to_corporate_id", columnDefinition = "uuid")
    private java.util.UUID bill_to_corporate_id;

    @Column(name = "billing_name", columnDefinition = "varchar(255)")
    private String billing_name;

    @Column(name = "billing_address", columnDefinition = "text")
    private String billing_address;

    @Column(name = "billing_email", columnDefinition = "varchar(255)")
    private String billing_email;

    @Column(name = "tax_id", columnDefinition = "varchar(100)")
    private String tax_id;

    @Column(name = "issue_date", columnDefinition = "date")
    private java.time.LocalDate issue_date;

    @Column(name = "due_date", columnDefinition = "date")
    private java.time.LocalDate due_date;

    @Column(name = "subtotal", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal subtotal;

    @Column(name = "tax_amount", columnDefinition = "numeric(12,2) DEFAULT 0")
    private java.math.BigDecimal tax_amount;

    @Column(name = "discount_amount", columnDefinition = "numeric(12,2) DEFAULT 0")
    private java.math.BigDecimal discount_amount;

    @Column(name = "total_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal total_amount;

    @Column(name = "paid_amount", columnDefinition = "numeric(12,2) DEFAULT 0")
    private java.math.BigDecimal paid_amount;

    @Column(name = "currency", columnDefinition = "varchar(3)")
    private String currency;

    @Column(name = "line_items", columnDefinition = "jsonb")
    private String line_items;

    @Column(name = "status", columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "pdf_url", columnDefinition = "text")
    private String pdf_url;

    @Column(name = "invoice_type", columnDefinition = "varchar(50)")
    private String invoice_type;

    @Column(name = "payment_terms", columnDefinition = "text")
    private String payment_terms;

    @Column(name = "room_charges", columnDefinition = "numeric(12,2) DEFAULT 0")
    private java.math.BigDecimal room_charges;

    @Column(name = "service_charges", columnDefinition = "numeric(12,2) DEFAULT 0")
    private java.math.BigDecimal service_charges;

    @Column(name = "additional_charges", columnDefinition = "numeric(12,2) DEFAULT 0")
    private java.math.BigDecimal additional_charges;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "terms", columnDefinition = "text")
    private String terms;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long created_by;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    @Column(name = "sent_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime sent_at;

    @Column(name = "paid_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime paid_at;

    @Column(name = "balance_due", columnDefinition = "numeric(12,2) GENERATED ALWAYS AS ((total_amount - paid_amount)) STORED")
    private java.math.BigDecimal balance_due;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.util.UUID getUuid() { return uuid; }
    public void setUuid(java.util.UUID uuid) { this.uuid = uuid; }

    public String getInvoiceNumber() { return invoice_number; }
    public void setInvoiceNumber(String invoice_number) { this.invoice_number = invoice_number; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public Long getBillToGuestId() { return bill_to_guest_id; }
    public void setBillToGuestId(Long bill_to_guest_id) { this.bill_to_guest_id = bill_to_guest_id; }

    public java.util.UUID getBillToCorporateId() { return bill_to_corporate_id; }
    public void setBillToCorporateId(java.util.UUID bill_to_corporate_id) { this.bill_to_corporate_id = bill_to_corporate_id; }

    public String getBillingName() { return billing_name; }
    public void setBillingName(String billing_name) { this.billing_name = billing_name; }

    public String getBillingAddress() { return billing_address; }
    public void setBillingAddress(String billing_address) { this.billing_address = billing_address; }

    public String getBillingEmail() { return billing_email; }
    public void setBillingEmail(String billing_email) { this.billing_email = billing_email; }

    public String getTaxId() { return tax_id; }
    public void setTaxId(String tax_id) { this.tax_id = tax_id; }

    public java.time.LocalDate getIssueDate() { return issue_date; }
    public void setIssueDate(java.time.LocalDate issue_date) { this.issue_date = issue_date; }

    public java.time.LocalDate getDueDate() { return due_date; }
    public void setDueDate(java.time.LocalDate due_date) { this.due_date = due_date; }

    public java.math.BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(java.math.BigDecimal subtotal) { this.subtotal = subtotal; }

    public java.math.BigDecimal getTaxAmount() { return tax_amount; }
    public void setTaxAmount(java.math.BigDecimal tax_amount) { this.tax_amount = tax_amount; }

    public java.math.BigDecimal getDiscountAmount() { return discount_amount; }
    public void setDiscountAmount(java.math.BigDecimal discount_amount) { this.discount_amount = discount_amount; }

    public java.math.BigDecimal getTotalAmount() { return total_amount; }
    public void setTotalAmount(java.math.BigDecimal total_amount) { this.total_amount = total_amount; }

    public java.math.BigDecimal getPaidAmount() { return paid_amount; }
    public void setPaidAmount(java.math.BigDecimal paid_amount) { this.paid_amount = paid_amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getLineItems() { return line_items; }
    public void setLineItems(String line_items) { this.line_items = line_items; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPdfUrl() { return pdf_url; }
    public void setPdfUrl(String pdf_url) { this.pdf_url = pdf_url; }

    public String getInvoiceType() { return invoice_type; }
    public void setInvoiceType(String invoice_type) { this.invoice_type = invoice_type; }

    public String getPaymentTerms() { return payment_terms; }
    public void setPaymentTerms(String payment_terms) { this.payment_terms = payment_terms; }

    public java.math.BigDecimal getRoomCharges() { return room_charges; }
    public void setRoomCharges(java.math.BigDecimal room_charges) { this.room_charges = room_charges; }

    public java.math.BigDecimal getServiceCharges() { return service_charges; }
    public void setServiceCharges(java.math.BigDecimal service_charges) { this.service_charges = service_charges; }

    public java.math.BigDecimal getAdditionalCharges() { return additional_charges; }
    public void setAdditionalCharges(java.math.BigDecimal additional_charges) { this.additional_charges = additional_charges; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }

    public java.time.OffsetDateTime getSentAt() { return sent_at; }
    public void setSentAt(java.time.OffsetDateTime sent_at) { this.sent_at = sent_at; }

    public java.time.OffsetDateTime getPaidAt() { return paid_at; }
    public void setPaidAt(java.time.OffsetDateTime paid_at) { this.paid_at = paid_at; }

    public java.math.BigDecimal getBalanceDue() { return balance_due; }
    public void setBalanceDue(java.math.BigDecimal balance_due) { this.balance_due = balance_due; }
}
