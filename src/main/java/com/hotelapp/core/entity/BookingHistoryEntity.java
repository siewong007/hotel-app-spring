package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "booking_history")
public class BookingHistoryEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private java.util.UUID id;

    @Column(name = "booking_id", columnDefinition = "bigint")
    private Long booking_id;

    @Column(name = "previous_status", columnDefinition = "varchar(50)")
    private String previous_status;

    @Column(name = "new_status", columnDefinition = "varchar(50)")
    private String new_status;

    @Column(name = "changed_by", columnDefinition = "bigint")
    private Long changed_by;

    @Column(name = "change_reason", columnDefinition = "text")
    private String change_reason;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public String getPreviousStatus() { return previous_status; }
    public void setPreviousStatus(String previous_status) { this.previous_status = previous_status; }

    public String getNewStatus() { return new_status; }
    public void setNewStatus(String new_status) { this.new_status = new_status; }

    public Long getChangedBy() { return changed_by; }
    public void setChangedBy(Long changed_by) { this.changed_by = changed_by; }

    public String getChangeReason() { return change_reason; }
    public void setChangeReason(String change_reason) { this.change_reason = change_reason; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
