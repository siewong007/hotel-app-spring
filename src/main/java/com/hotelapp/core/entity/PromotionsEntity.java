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
@Table(name = "promotions",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"slug"})})
public class PromotionsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "slug", columnDefinition = "varchar(120)")
    private String slug;

    @Column(name = "name", columnDefinition = "varchar(160)")
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "terms", columnDefinition = "text")
    private String terms;

    @Column(name = "status", columnDefinition = "varchar(16)")
    private String status;

    @Column(name = "promotion_kind", columnDefinition = "varchar(16)")
    private String promotion_kind;

    @Column(name = "discount_type", columnDefinition = "varchar(24)")
    private String discount_type;

    @Column(name = "discount_value", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal discount_value;

    @Column(name = "max_discount_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal max_discount_amount;

    @Column(name = "currency", columnDefinition = "varchar(3)")
    private String currency;

    @Column(name = "claim_starts_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime claim_starts_at;

    @Column(name = "claim_ends_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime claim_ends_at;

    @Column(name = "stay_starts_on", columnDefinition = "date")
    private java.time.LocalDate stay_starts_on;

    @Column(name = "stay_ends_on", columnDefinition = "date")
    private java.time.LocalDate stay_ends_on;

    @Column(name = "min_nights", columnDefinition = "integer")
    private Integer min_nights;

    @Column(name = "max_nights", columnDefinition = "integer")
    private Integer max_nights;

    @Column(name = "min_subtotal", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal min_subtotal;

    @Column(name = "claim_limit", columnDefinition = "integer")
    private Integer claim_limit;

    @Column(name = "claimed_count", columnDefinition = "integer")
    private Integer claimed_count;

    @Column(name = "per_guest_limit", columnDefinition = "integer")
    private Integer per_guest_limit;

    @Column(name = "is_public", columnDefinition = "boolean")
    private Boolean is_public;

    @Column(name = "is_cancellable", columnDefinition = "boolean")
    private Boolean is_cancellable;

    @Column(name = "version", columnDefinition = "integer")
    private Integer version;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long created_by;

    @Column(name = "updated_by", columnDefinition = "bigint")
    private Long updated_by;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPromotionKind() { return promotion_kind; }
    public void setPromotionKind(String promotion_kind) { this.promotion_kind = promotion_kind; }

    public String getDiscountType() { return discount_type; }
    public void setDiscountType(String discount_type) { this.discount_type = discount_type; }

    public java.math.BigDecimal getDiscountValue() { return discount_value; }
    public void setDiscountValue(java.math.BigDecimal discount_value) { this.discount_value = discount_value; }

    public java.math.BigDecimal getMaxDiscountAmount() { return max_discount_amount; }
    public void setMaxDiscountAmount(java.math.BigDecimal max_discount_amount) { this.max_discount_amount = max_discount_amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public java.time.OffsetDateTime getClaimStartsAt() { return claim_starts_at; }
    public void setClaimStartsAt(java.time.OffsetDateTime claim_starts_at) { this.claim_starts_at = claim_starts_at; }

    public java.time.OffsetDateTime getClaimEndsAt() { return claim_ends_at; }
    public void setClaimEndsAt(java.time.OffsetDateTime claim_ends_at) { this.claim_ends_at = claim_ends_at; }

    public java.time.LocalDate getStayStartsOn() { return stay_starts_on; }
    public void setStayStartsOn(java.time.LocalDate stay_starts_on) { this.stay_starts_on = stay_starts_on; }

    public java.time.LocalDate getStayEndsOn() { return stay_ends_on; }
    public void setStayEndsOn(java.time.LocalDate stay_ends_on) { this.stay_ends_on = stay_ends_on; }

    public Integer getMinNights() { return min_nights; }
    public void setMinNights(Integer min_nights) { this.min_nights = min_nights; }

    public Integer getMaxNights() { return max_nights; }
    public void setMaxNights(Integer max_nights) { this.max_nights = max_nights; }

    public java.math.BigDecimal getMinSubtotal() { return min_subtotal; }
    public void setMinSubtotal(java.math.BigDecimal min_subtotal) { this.min_subtotal = min_subtotal; }

    public Integer getClaimLimit() { return claim_limit; }
    public void setClaimLimit(Integer claim_limit) { this.claim_limit = claim_limit; }

    public Integer getClaimedCount() { return claimed_count; }
    public void setClaimedCount(Integer claimed_count) { this.claimed_count = claimed_count; }

    public Integer getPerGuestLimit() { return per_guest_limit; }
    public void setPerGuestLimit(Integer per_guest_limit) { this.per_guest_limit = per_guest_limit; }

    public Boolean isIsPublic() { return is_public; }
    public void setIsPublic(Boolean is_public) { this.is_public = is_public; }

    public Boolean isIsCancellable() { return is_cancellable; }
    public void setIsCancellable(Boolean is_cancellable) { this.is_cancellable = is_cancellable; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public Long getUpdatedBy() { return updated_by; }
    public void setUpdatedBy(Long updated_by) { this.updated_by = updated_by; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
