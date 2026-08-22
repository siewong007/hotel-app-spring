package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "booking_modifications")
public class BookingModificationsEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private java.util.UUID id;

    @Column(name = "booking_id", columnDefinition = "bigint")
    private Long booking_id;

    @Column(name = "modification_type", columnDefinition = "varchar(50)")
    private String modification_type;

    @Column(name = "old_value", columnDefinition = "jsonb")
    private String old_value;

    @Column(name = "new_value", columnDefinition = "jsonb")
    private String new_value;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "price_adjustment", columnDefinition = "numeric(10,2) DEFAULT 0")
    private java.math.BigDecimal price_adjustment;

    @Column(name = "modified_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime modified_at;

    @Column(name = "modified_by", columnDefinition = "bigint")
    private Long modified_by;

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public String getModificationType() { return modification_type; }
    public void setModificationType(String modification_type) { this.modification_type = modification_type; }

    public String getOldValue() { return old_value; }
    public void setOldValue(String old_value) { this.old_value = old_value; }

    public String getNewValue() { return new_value; }
    public void setNewValue(String new_value) { this.new_value = new_value; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public java.math.BigDecimal getPriceAdjustment() { return price_adjustment; }
    public void setPriceAdjustment(java.math.BigDecimal price_adjustment) { this.price_adjustment = price_adjustment; }

    public java.time.OffsetDateTime getModifiedAt() { return modified_at; }
    public void setModifiedAt(java.time.OffsetDateTime modified_at) { this.modified_at = modified_at; }

    public Long getModifiedBy() { return modified_by; }
    public void setModifiedBy(Long modified_by) { this.modified_by = modified_by; }
}
