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
@Table(name = "night_audit_details")
public class NightAuditDetailsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "audit_run_id", columnDefinition = "bigint")
    private Long audit_run_id;

    @Column(name = "booking_id", columnDefinition = "bigint")
    private Long booking_id;

    @Column(name = "room_id", columnDefinition = "bigint")
    private Long room_id;

    @Column(name = "record_type", columnDefinition = "varchar(50)")
    private String record_type;

    @Column(name = "action", columnDefinition = "varchar(50)")
    private String action;

    @Column(name = "data", columnDefinition = "jsonb")
    private String data;

    @Column(name = "created_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAuditRunId() { return audit_run_id; }
    public void setAuditRunId(Long audit_run_id) { this.audit_run_id = audit_run_id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public Long getRoomId() { return room_id; }
    public void setRoomId(Long room_id) { this.room_id = room_id; }

    public String getRecordType() { return record_type; }
    public void setRecordType(String record_type) { this.record_type = record_type; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
