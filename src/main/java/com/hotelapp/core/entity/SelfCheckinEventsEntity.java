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
@Table(name = "self_checkin_events")
public class SelfCheckinEventsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "booking_id")
    private Long booking_id;

    @Column(name = "guest_id")
    private Long guest_id;

    @Column(name = "ekyc_verification_id")
    private Long ekyc_verification_id;

    @Column(name = "user_id")
    private Long user_id;

    @Column(name = "checked_in_at")
    private java.time.OffsetDateTime checked_in_at;

    @Column(name = "room_key_issued")
    private Boolean room_key_issued;

    @Column(name = "digital_key_sent")
    private Boolean digital_key_sent;

    @Column(name = "device_type", columnDefinition = "varchar(100)")
    private String device_type;

    @Column(name = "checkin_location", columnDefinition = "varchar(255)")
    private String checkin_location;

    @Column(name = "event_type", columnDefinition = "varchar(100)")
    private String event_type;

    @Column(name = "source", columnDefinition = "varchar(100)")
    private String source;

    @Column(name = "event_data", columnDefinition = "text")
    private String event_data;

    @Column(name = "ip_address", columnDefinition = "varchar(64)")
    private String ip_address;

    @Column(name = "user_agent", columnDefinition = "text")
    private String user_agent;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public Long getEkycVerificationId() { return ekyc_verification_id; }
    public void setEkycVerificationId(Long ekyc_verification_id) { this.ekyc_verification_id = ekyc_verification_id; }

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public java.time.OffsetDateTime getCheckedInAt() { return checked_in_at; }
    public void setCheckedInAt(java.time.OffsetDateTime checked_in_at) { this.checked_in_at = checked_in_at; }

    public Boolean isRoomKeyIssued() { return room_key_issued; }
    public void setRoomKeyIssued(Boolean room_key_issued) { this.room_key_issued = room_key_issued; }

    public Boolean isDigitalKeySent() { return digital_key_sent; }
    public void setDigitalKeySent(Boolean digital_key_sent) { this.digital_key_sent = digital_key_sent; }

    public String getDeviceType() { return device_type; }
    public void setDeviceType(String device_type) { this.device_type = device_type; }

    public String getCheckinLocation() { return checkin_location; }
    public void setCheckinLocation(String checkin_location) { this.checkin_location = checkin_location; }

    public String getEventType() { return event_type; }
    public void setEventType(String event_type) { this.event_type = event_type; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getEventData() { return event_data; }
    public void setEventData(String event_data) { this.event_data = event_data; }

    public String getIpAddress() { return ip_address; }
    public void setIpAddress(String ip_address) { this.ip_address = ip_address; }

    public String getUserAgent() { return user_agent; }
    public void setUserAgent(String user_agent) { this.user_agent = user_agent; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
