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
@Table(name = "guest_reviews")
public class GuestReviewsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "guest_id")
    private Long guest_id;

    @Column(name = "booking_id")
    private Long booking_id;

    @Column(name = "overall_rating", columnDefinition = "numeric(3,2)")
    private java.math.BigDecimal overall_rating;

    @Column(name = "cleanliness_rating", columnDefinition = "numeric(3,2)")
    private java.math.BigDecimal cleanliness_rating;

    @Column(name = "service_rating", columnDefinition = "numeric(3,2)")
    private java.math.BigDecimal service_rating;

    @Column(name = "comfort_rating", columnDefinition = "numeric(3,2)")
    private java.math.BigDecimal comfort_rating;

    @Column(name = "location_rating", columnDefinition = "numeric(3,2)")
    private java.math.BigDecimal location_rating;

    @Column(name = "value_rating", columnDefinition = "numeric(3,2)")
    private java.math.BigDecimal value_rating;

    @Column(name = "title", columnDefinition = "varchar(255)")
    private String title;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "pros", columnDefinition = "text")
    private String pros;

    @Column(name = "cons", columnDefinition = "text")
    private String cons;

    @Column(name = "response", columnDefinition = "text")
    private String response;

    @Column(name = "response_at")
    private java.time.OffsetDateTime response_at;

    @Column(name = "response_by")
    private Long response_by;

    @Column(name = "is_published")
    private Boolean is_published;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public java.math.BigDecimal getOverallRating() { return overall_rating; }
    public void setOverallRating(java.math.BigDecimal overall_rating) { this.overall_rating = overall_rating; }

    public java.math.BigDecimal getCleanlinessRating() { return cleanliness_rating; }
    public void setCleanlinessRating(java.math.BigDecimal cleanliness_rating) { this.cleanliness_rating = cleanliness_rating; }

    public java.math.BigDecimal getServiceRating() { return service_rating; }
    public void setServiceRating(java.math.BigDecimal service_rating) { this.service_rating = service_rating; }

    public java.math.BigDecimal getComfortRating() { return comfort_rating; }
    public void setComfortRating(java.math.BigDecimal comfort_rating) { this.comfort_rating = comfort_rating; }

    public java.math.BigDecimal getLocationRating() { return location_rating; }
    public void setLocationRating(java.math.BigDecimal location_rating) { this.location_rating = location_rating; }

    public java.math.BigDecimal getValueRating() { return value_rating; }
    public void setValueRating(java.math.BigDecimal value_rating) { this.value_rating = value_rating; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getPros() { return pros; }
    public void setPros(String pros) { this.pros = pros; }

    public String getCons() { return cons; }
    public void setCons(String cons) { this.cons = cons; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public java.time.OffsetDateTime getResponseAt() { return response_at; }
    public void setResponseAt(java.time.OffsetDateTime response_at) { this.response_at = response_at; }

    public Long getResponseBy() { return response_by; }
    public void setResponseBy(Long response_by) { this.response_by = response_by; }

    public Boolean isIsPublished() { return is_published; }
    public void setIsPublished(Boolean is_published) { this.is_published = is_published; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
