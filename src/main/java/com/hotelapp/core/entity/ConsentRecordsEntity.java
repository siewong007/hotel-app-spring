package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "consent_records")
public class ConsentRecordsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "subject_type", columnDefinition = "varchar(20)")
    private String subject_type;

    @Column(name = "user_id", columnDefinition = "bigint")
    private Long user_id;

    @Column(name = "guest_id", columnDefinition = "bigint")
    private Long guest_id;

    @Column(name = "booking_id", columnDefinition = "bigint")
    private Long booking_id;

    @Column(name = "document_type", columnDefinition = "varchar(40)")
    private String document_type;

    @Column(name = "document_version", columnDefinition = "varchar(40)")
    private String document_version;

    @Column(name = "locale", columnDefinition = "varchar(10) DEFAULT 'en'")
    private String locale;

    @Column(name = "granted", columnDefinition = "boolean")
    private Boolean granted;

    @Column(name = "source", columnDefinition = "varchar(40)")
    private String source;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ip_address;

    @Column(name = "user_agent", columnDefinition = "text")
    private String user_agent;

    @Column(name = "withdrawn_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime withdrawn_at;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSubjectType() { return subject_type; }
    public void setSubjectType(String subject_type) { this.subject_type = subject_type; }

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public String getDocumentType() { return document_type; }
    public void setDocumentType(String document_type) { this.document_type = document_type; }

    public String getDocumentVersion() { return document_version; }
    public void setDocumentVersion(String document_version) { this.document_version = document_version; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public Boolean getGranted() { return granted; }
    public void setGranted(Boolean granted) { this.granted = granted; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getIpAddress() { return ip_address; }
    public void setIpAddress(String ip_address) { this.ip_address = ip_address; }

    public String getUserAgent() { return user_agent; }
    public void setUserAgent(String user_agent) { this.user_agent = user_agent; }

    public java.time.OffsetDateTime getWithdrawnAt() { return withdrawn_at; }
    public void setWithdrawnAt(java.time.OffsetDateTime withdrawn_at) { this.withdrawn_at = withdrawn_at; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
