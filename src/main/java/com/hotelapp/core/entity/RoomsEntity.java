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
@Table(name = "rooms",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"room_number"})})
public class RoomsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "room_number", columnDefinition = "varchar(20)")
    private String room_number;

    @Column(name = "room_type_id", columnDefinition = "bigint")
    private Long room_type_id;

    @Column(name = "floor", columnDefinition = "integer")
    private Integer floor;

    @Column(name = "building", columnDefinition = "varchar(50)")
    private String building;

    @Column(name = "custom_price", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal custom_price;

    @Column(name = "status", columnDefinition = "varchar(20)")
    private String status;

    @Column(name = "status_notes", columnDefinition = "text")
    private String status_notes;

    @Column(name = "reserved_start_date", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime reserved_start_date;

    @Column(name = "reserved_end_date", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime reserved_end_date;

    @Column(name = "maintenance_start_date", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime maintenance_start_date;

    @Column(name = "maintenance_end_date", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime maintenance_end_date;

    @Column(name = "cleaning_start_date", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime cleaning_start_date;

    @Column(name = "cleaning_end_date", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime cleaning_end_date;

    @Column(name = "current_occupancy", columnDefinition = "integer DEFAULT 0")
    private Integer current_occupancy;

    @Column(name = "last_cleaned_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime last_cleaned_at;

    @Column(name = "last_inspected_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime last_inspected_at;

    @Column(name = "inspected_by", columnDefinition = "bigint")
    private Long inspected_by;

    @Column(name = "is_smoking", columnDefinition = "boolean DEFAULT false")
    private Boolean is_smoking;

    @Column(name = "is_accessible", columnDefinition = "boolean DEFAULT false")
    private Boolean is_accessible;

    @Column(name = "has_view", columnDefinition = "boolean DEFAULT false")
    private Boolean has_view;

    @Column(name = "view_type", columnDefinition = "varchar(50)")
    private String view_type;

    @Column(name = "connecting_room_id", columnDefinition = "bigint")
    private Long connecting_room_id;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "is_active", columnDefinition = "boolean DEFAULT true")
    private Boolean is_active;

    @Column(name = "last_posted_status", columnDefinition = "varchar(50)")
    private String last_posted_status;

    @Column(name = "last_posted_date", columnDefinition = "date")
    private java.time.LocalDate last_posted_date;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRoomNumber() { return room_number; }
    public void setRoomNumber(String room_number) { this.room_number = room_number; }

    public Long getRoomTypeId() { return room_type_id; }
    public void setRoomTypeId(Long room_type_id) { this.room_type_id = room_type_id; }

    public Integer getFloor() { return floor; }
    public void setFloor(Integer floor) { this.floor = floor; }

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public java.math.BigDecimal getCustomPrice() { return custom_price; }
    public void setCustomPrice(java.math.BigDecimal custom_price) { this.custom_price = custom_price; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusNotes() { return status_notes; }
    public void setStatusNotes(String status_notes) { this.status_notes = status_notes; }

    public java.time.OffsetDateTime getReservedStartDate() { return reserved_start_date; }
    public void setReservedStartDate(java.time.OffsetDateTime reserved_start_date) { this.reserved_start_date = reserved_start_date; }

    public java.time.OffsetDateTime getReservedEndDate() { return reserved_end_date; }
    public void setReservedEndDate(java.time.OffsetDateTime reserved_end_date) { this.reserved_end_date = reserved_end_date; }

    public java.time.OffsetDateTime getMaintenanceStartDate() { return maintenance_start_date; }
    public void setMaintenanceStartDate(java.time.OffsetDateTime maintenance_start_date) { this.maintenance_start_date = maintenance_start_date; }

    public java.time.OffsetDateTime getMaintenanceEndDate() { return maintenance_end_date; }
    public void setMaintenanceEndDate(java.time.OffsetDateTime maintenance_end_date) { this.maintenance_end_date = maintenance_end_date; }

    public java.time.OffsetDateTime getCleaningStartDate() { return cleaning_start_date; }
    public void setCleaningStartDate(java.time.OffsetDateTime cleaning_start_date) { this.cleaning_start_date = cleaning_start_date; }

    public java.time.OffsetDateTime getCleaningEndDate() { return cleaning_end_date; }
    public void setCleaningEndDate(java.time.OffsetDateTime cleaning_end_date) { this.cleaning_end_date = cleaning_end_date; }

    public Integer getCurrentOccupancy() { return current_occupancy; }
    public void setCurrentOccupancy(Integer current_occupancy) { this.current_occupancy = current_occupancy; }

    public java.time.OffsetDateTime getLastCleanedAt() { return last_cleaned_at; }
    public void setLastCleanedAt(java.time.OffsetDateTime last_cleaned_at) { this.last_cleaned_at = last_cleaned_at; }

    public java.time.OffsetDateTime getLastInspectedAt() { return last_inspected_at; }
    public void setLastInspectedAt(java.time.OffsetDateTime last_inspected_at) { this.last_inspected_at = last_inspected_at; }

    public Long getInspectedBy() { return inspected_by; }
    public void setInspectedBy(Long inspected_by) { this.inspected_by = inspected_by; }

    public Boolean isIsSmoking() { return is_smoking; }
    public void setIsSmoking(Boolean is_smoking) { this.is_smoking = is_smoking; }

    public Boolean isIsAccessible() { return is_accessible; }
    public void setIsAccessible(Boolean is_accessible) { this.is_accessible = is_accessible; }

    public Boolean isHasView() { return has_view; }
    public void setHasView(Boolean has_view) { this.has_view = has_view; }

    public String getViewType() { return view_type; }
    public void setViewType(String view_type) { this.view_type = view_type; }

    public Long getConnectingRoomId() { return connecting_room_id; }
    public void setConnectingRoomId(Long connecting_room_id) { this.connecting_room_id = connecting_room_id; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public String getLastPostedStatus() { return last_posted_status; }
    public void setLastPostedStatus(String last_posted_status) { this.last_posted_status = last_posted_status; }

    public java.time.LocalDate getLastPostedDate() { return last_posted_date; }
    public void setLastPostedDate(java.time.LocalDate last_posted_date) { this.last_posted_date = last_posted_date; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
