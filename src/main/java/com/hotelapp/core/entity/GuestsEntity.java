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
@Table(name = "guests",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"uuid"})})
public class GuestsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid")
    private java.util.UUID uuid;

    @Column(name = "full_name", columnDefinition = "varchar(255)")
    private String full_name;

    @Column(name = "first_name", columnDefinition = "varchar(100)")
    private String first_name;

    @Column(name = "last_name", columnDefinition = "varchar(100)")
    private String last_name;

    @Column(name = "email", columnDefinition = "varchar(255)")
    private String email;

    @Column(name = "phone", columnDefinition = "varchar(20)")
    private String phone;

    @Column(name = "title", columnDefinition = "varchar(20)")
    private String title;

    @Column(name = "alt_phone", columnDefinition = "varchar(20)")
    private String alt_phone;

    @Column(name = "date_of_birth")
    private java.time.LocalDate date_of_birth;

    @Column(name = "nationality", columnDefinition = "varchar(100)")
    private String nationality;

    @Column(name = "ic_number", columnDefinition = "varchar(50)")
    private String ic_number;

    @Column(name = "address_line_1", columnDefinition = "varchar(255)")
    private String address_line_1;

    @Column(name = "address_line_2", columnDefinition = "varchar(255)")
    private String address_line_2;

    @Column(name = "city", columnDefinition = "varchar(100)")
    private String city;

    @Column(name = "state", columnDefinition = "varchar(100)")
    private String state;

    @Column(name = "postal_code", columnDefinition = "varchar(20)")
    private String postal_code;

    @Column(name = "country", columnDefinition = "varchar(100)")
    private String country;

    @Column(name = "id_type")
    private String id_type;

    @Column(name = "id_number", columnDefinition = "varchar(100)")
    private String id_number;

    @Column(name = "id_expiry")
    private java.time.LocalDate id_expiry;

    @Column(name = "id_country", columnDefinition = "varchar(100)")
    private String id_country;

    @Column(name = "language_preference", columnDefinition = "varchar(10)")
    private String language_preference;

    @Column(name = "communication_preference", columnDefinition = "varchar(50)")
    private String communication_preference;

    @Column(name = "marketing_opt_in")
    private Boolean marketing_opt_in;

    @Column(name = "vip_status", columnDefinition = "varchar(20)")
    private String vip_status;

    @Column(name = "company_name", columnDefinition = "varchar(255)")
    private String company_name;

    @Column(name = "job_title", columnDefinition = "varchar(100)")
    private String job_title;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "special_requests", columnDefinition = "text")
    private String special_requests;

    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    @Column(name = "total_stays")
    private Integer total_stays;

    @Column(name = "total_spend", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal total_spend;

    @Column(name = "average_rating", columnDefinition = "numeric(3,2)")
    private java.math.BigDecimal average_rating;

    @Column(name = "complimentary_nights_credit")
    private Integer complimentary_nights_credit;

    @Column(name = "is_blacklisted")
    private Boolean is_blacklisted;

    @Column(name = "blacklist_reason", columnDefinition = "text")
    private String blacklist_reason;

    @Column(name = "is_active")
    private Boolean is_active;

    @Column(name = "guest_type")
    private String guest_type;

    @Column(name = "discount_percentage")
    private Integer discount_percentage;

    @Column(name = "tourism_type")
    private String tourism_type;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "created_by")
    private Long created_by;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    @Column(name = "updated_by")
    private Long updated_by;

    @Column(name = "deleted_at")
    private java.time.OffsetDateTime deleted_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.util.UUID getUuid() { return uuid; }
    public void setUuid(java.util.UUID uuid) { this.uuid = uuid; }

    public String getFullName() { return full_name; }
    public void setFullName(String full_name) { this.full_name = full_name; }

    public String getFirstName() { return first_name; }
    public void setFirstName(String first_name) { this.first_name = first_name; }

    public String getLastName() { return last_name; }
    public void setLastName(String last_name) { this.last_name = last_name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAltPhone() { return alt_phone; }
    public void setAltPhone(String alt_phone) { this.alt_phone = alt_phone; }

    public java.time.LocalDate getDateOfBirth() { return date_of_birth; }
    public void setDateOfBirth(java.time.LocalDate date_of_birth) { this.date_of_birth = date_of_birth; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public String getIcNumber() { return ic_number; }
    public void setIcNumber(String ic_number) { this.ic_number = ic_number; }

    public String getAddressLine1() { return address_line_1; }
    public void setAddressLine1(String address_line_1) { this.address_line_1 = address_line_1; }

    public String getAddressLine2() { return address_line_2; }
    public void setAddressLine2(String address_line_2) { this.address_line_2 = address_line_2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPostalCode() { return postal_code; }
    public void setPostalCode(String postal_code) { this.postal_code = postal_code; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getIdType() { return id_type; }
    public void setIdType(String id_type) { this.id_type = id_type; }

    public String getIdNumber() { return id_number; }
    public void setIdNumber(String id_number) { this.id_number = id_number; }

    public java.time.LocalDate getIdExpiry() { return id_expiry; }
    public void setIdExpiry(java.time.LocalDate id_expiry) { this.id_expiry = id_expiry; }

    public String getIdCountry() { return id_country; }
    public void setIdCountry(String id_country) { this.id_country = id_country; }

    public String getLanguagePreference() { return language_preference; }
    public void setLanguagePreference(String language_preference) { this.language_preference = language_preference; }

    public String getCommunicationPreference() { return communication_preference; }
    public void setCommunicationPreference(String communication_preference) { this.communication_preference = communication_preference; }

    public Boolean isMarketingOptIn() { return marketing_opt_in; }
    public void setMarketingOptIn(Boolean marketing_opt_in) { this.marketing_opt_in = marketing_opt_in; }

    public String getVipStatus() { return vip_status; }
    public void setVipStatus(String vip_status) { this.vip_status = vip_status; }

    public String getCompanyName() { return company_name; }
    public void setCompanyName(String company_name) { this.company_name = company_name; }

    public String getJobTitle() { return job_title; }
    public void setJobTitle(String job_title) { this.job_title = job_title; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getSpecialRequests() { return special_requests; }
    public void setSpecialRequests(String special_requests) { this.special_requests = special_requests; }

    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }

    public Integer getTotalStays() { return total_stays; }
    public void setTotalStays(Integer total_stays) { this.total_stays = total_stays; }

    public java.math.BigDecimal getTotalSpend() { return total_spend; }
    public void setTotalSpend(java.math.BigDecimal total_spend) { this.total_spend = total_spend; }

    public java.math.BigDecimal getAverageRating() { return average_rating; }
    public void setAverageRating(java.math.BigDecimal average_rating) { this.average_rating = average_rating; }

    public Integer getComplimentaryNightsCredit() { return complimentary_nights_credit; }
    public void setComplimentaryNightsCredit(Integer complimentary_nights_credit) { this.complimentary_nights_credit = complimentary_nights_credit; }

    public Boolean isIsBlacklisted() { return is_blacklisted; }
    public void setIsBlacklisted(Boolean is_blacklisted) { this.is_blacklisted = is_blacklisted; }

    public String getBlacklistReason() { return blacklist_reason; }
    public void setBlacklistReason(String blacklist_reason) { this.blacklist_reason = blacklist_reason; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public String getGuestType() { return guest_type; }
    public void setGuestType(String guest_type) { this.guest_type = guest_type; }

    public Integer getDiscountPercentage() { return discount_percentage; }
    public void setDiscountPercentage(Integer discount_percentage) { this.discount_percentage = discount_percentage; }

    public String getTourismType() { return tourism_type; }
    public void setTourismType(String tourism_type) { this.tourism_type = tourism_type; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }

    public Long getUpdatedBy() { return updated_by; }
    public void setUpdatedBy(Long updated_by) { this.updated_by = updated_by; }

    public java.time.OffsetDateTime getDeletedAt() { return deleted_at; }
    public void setDeletedAt(java.time.OffsetDateTime deleted_at) { this.deleted_at = deleted_at; }
}
