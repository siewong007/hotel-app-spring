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
@Table(name = "user_guests",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "guest_id"})})
public class UserGuestsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long user_id;

    @Column(name = "guest_id")
    private Long guest_id;

    @Column(name = "relationship_type", columnDefinition = "varchar(50)")
    private String relationship_type;

    @Column(name = "can_book_for")
    private Boolean can_book_for;

    @Column(name = "can_view_bookings")
    private Boolean can_view_bookings;

    @Column(name = "can_modify")
    private Boolean can_modify;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "linked_by")
    private Long linked_by;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getRelationshipType() { return relationship_type; }
    public void setRelationshipType(String relationship_type) { this.relationship_type = relationship_type; }

    public Boolean isCanBookFor() { return can_book_for; }
    public void setCanBookFor(Boolean can_book_for) { this.can_book_for = can_book_for; }

    public Boolean isCanViewBookings() { return can_view_bookings; }
    public void setCanViewBookings(Boolean can_view_bookings) { this.can_view_bookings = can_view_bookings; }

    public Boolean isCanModify() { return can_modify; }
    public void setCanModify(Boolean can_modify) { this.can_modify = can_modify; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getLinkedBy() { return linked_by; }
    public void setLinkedBy(Long linked_by) { this.linked_by = linked_by; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
