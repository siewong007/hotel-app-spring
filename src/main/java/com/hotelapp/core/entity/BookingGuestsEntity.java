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
@Table(name = "booking_guests")
public class BookingGuestsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "booking_id")
    private Long booking_id;

    @Column(name = "guest_id")
    private Long guest_id;

    @Column(name = "first_name", columnDefinition = "varchar(100)")
    private String first_name;

    @Column(name = "last_name", columnDefinition = "varchar(100)")
    private String last_name;

    @Column(name = "age_group", columnDefinition = "varchar(20)")
    private String age_group;

    @Column(name = "is_primary")
    private Boolean is_primary;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getFirstName() { return first_name; }
    public void setFirstName(String first_name) { this.first_name = first_name; }

    public String getLastName() { return last_name; }
    public void setLastName(String last_name) { this.last_name = last_name; }

    public String getAgeGroup() { return age_group; }
    public void setAgeGroup(String age_group) { this.age_group = age_group; }

    public Boolean isIsPrimary() { return is_primary; }
    public void setIsPrimary(Boolean is_primary) { this.is_primary = is_primary; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
