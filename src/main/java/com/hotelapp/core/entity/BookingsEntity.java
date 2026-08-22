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
@Table(name = "bookings",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"booking_number"}), @UniqueConstraint(columnNames = {"uuid"})})
public class BookingsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid")
    private java.util.UUID uuid;

    @Column(name = "booking_number", columnDefinition = "varchar(50)")
    private String booking_number;

    @Column(name = "folio_number", columnDefinition = "varchar(50)")
    private String folio_number;

    @Column(name = "guest_id")
    private Long guest_id;

    @Column(name = "guest_name", columnDefinition = "varchar(255)")
    private String guest_name;

    @Column(name = "guest_email", columnDefinition = "varchar(255)")
    private String guest_email;

    @Column(name = "guest_phone", columnDefinition = "varchar(20)")
    private String guest_phone;

    @Column(name = "corporate_account_id")
    private java.util.UUID corporate_account_id;

    @Column(name = "room_id")
    private Long room_id;

    @Column(name = "check_in_date")
    private java.time.LocalDate check_in_date;

    @Column(name = "check_out_date")
    private java.time.LocalDate check_out_date;

    @Column(name = "adults")
    private Integer adults;

    @Column(name = "children")
    private Integer children;

    @Column(name = "infants")
    private Integer infants;

    @Column(name = "rate_plan_id")
    private Long rate_plan_id;

    @Column(name = "room_rate", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal room_rate;

    @Column(name = "subtotal", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal subtotal;

    @Column(name = "tax_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal tax_amount;

    @Column(name = "discount_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal discount_amount;

    @Column(name = "discount_percentage", columnDefinition = "numeric(5,2)")
    private java.math.BigDecimal discount_percentage;

    @Column(name = "total_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal total_amount;

    @Column(name = "currency", columnDefinition = "varchar(3)")
    private String currency;

    @Column(name = "rate_override_weekday", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal rate_override_weekday;

    @Column(name = "rate_override_weekend", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal rate_override_weekend;

    @Column(name = "daily_rates")
    private String daily_rates;

    @Column(name = "is_tourist")
    private Boolean is_tourist;

    @Column(name = "tourism_tax_amount", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal tourism_tax_amount;

    @Column(name = "extra_bed_count")
    private Integer extra_bed_count;

    @Column(name = "extra_bed_charge", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal extra_bed_charge;

    @Column(name = "room_card_deposit", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal room_card_deposit;

    @Column(name = "late_checkout_penalty", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal late_checkout_penalty;

    @Column(name = "is_complimentary")
    private Boolean is_complimentary;

    @Column(name = "complimentary_reason", columnDefinition = "text")
    private String complimentary_reason;

    @Column(name = "complimentary_start_date")
    private java.time.LocalDate complimentary_start_date;

    @Column(name = "complimentary_end_date")
    private java.time.LocalDate complimentary_end_date;

    @Column(name = "original_total_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal original_total_amount;

    @Column(name = "complimentary_nights")
    private Integer complimentary_nights;

    @Column(name = "deposit_paid")
    private Boolean deposit_paid;

    @Column(name = "deposit_amount", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal deposit_amount;

    @Column(name = "deposit_paid_at")
    private java.time.OffsetDateTime deposit_paid_at;

    @Column(name = "status", columnDefinition = "varchar(30)")
    private String status;

    @Column(name = "payment_status", columnDefinition = "varchar(30)")
    private String payment_status;

    @Column(name = "payment_method", columnDefinition = "varchar(100)")
    private String payment_method;

    @Column(name = "payment_note", columnDefinition = "text")
    private String payment_note;

    @Column(name = "market_code", columnDefinition = "varchar(50)")
    private String market_code;

    @Column(name = "company_id")
    private Long company_id;

    @Column(name = "company_name", columnDefinition = "varchar(255)")
    private String company_name;

    @Column(name = "check_in_time")
    private java.time.LocalTime check_in_time;

    @Column(name = "check_out_time")
    private java.time.LocalTime check_out_time;

    @Column(name = "actual_check_in")
    private java.time.OffsetDateTime actual_check_in;

    @Column(name = "actual_check_out")
    private java.time.OffsetDateTime actual_check_out;

    @Column(name = "early_check_in")
    private Boolean early_check_in;

    @Column(name = "late_check_out")
    private Boolean late_check_out;

    @Column(name = "pre_checkin_completed")
    private Boolean pre_checkin_completed;

    @Column(name = "pre_checkin_completed_at")
    private java.time.OffsetDateTime pre_checkin_completed_at;

    @Column(name = "pre_checkin_token", columnDefinition = "varchar(255)")
    private String pre_checkin_token;

    @Column(name = "pre_checkin_token_expires_at")
    private java.time.OffsetDateTime pre_checkin_token_expires_at;

    @Column(name = "special_requests", columnDefinition = "text")
    private String special_requests;

    @Column(name = "internal_notes", columnDefinition = "text")
    private String internal_notes;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    @Column(name = "source", columnDefinition = "varchar(50)")
    private String source;

    @Column(name = "post_type", columnDefinition = "varchar(50)")
    private String post_type;

    @Column(name = "channel", columnDefinition = "varchar(50)")
    private String channel;

    @Column(name = "commission_rate", columnDefinition = "numeric(5,2)")
    private java.math.BigDecimal commission_rate;

    @Column(name = "cancelled_at")
    private java.time.OffsetDateTime cancelled_at;

    @Column(name = "cancelled_by")
    private Long cancelled_by;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellation_reason;

    @Column(name = "cancellation_fee", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal cancellation_fee;

    @Column(name = "is_posted")
    private Boolean is_posted;

    @Column(name = "posted_date")
    private java.time.LocalDate posted_date;

    @Column(name = "posted_at")
    private java.time.OffsetDateTime posted_at;

    @Column(name = "posted_by")
    private Long posted_by;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "created_by")
    private Long created_by;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    @Column(name = "updated_by")
    private Long updated_by;

    @Column(name = "tourism_billable_amount", columnDefinition = "numeric(10,2) GENERATED ALWAYS AS ( CASE WHEN is_tourist THEN COALESCE(tourism_tax_amount, (0)::numeric) ELSE (0)::numeric END)")
    private java.math.BigDecimal tourism_billable_amount;

    @Column(name = "cleaning_preference")
    private Boolean cleaning_preference;

    @Column(name = "booking_channel_id")
    private Long booking_channel_id;

    @Column(name = "ota_reference", columnDefinition = "varchar(100)")
    private String ota_reference;

    @Column(name = "commission_type_override", columnDefinition = "varchar(30)")
    private String commission_type_override;

    @Column(name = "commission_value_override", columnDefinition = "numeric(10,2)")
    private java.math.BigDecimal commission_value_override;

    @Column(name = "commission_scope_override", columnDefinition = "varchar(20)")
    private String commission_scope_override;

    @Column(name = "commission_amount", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal commission_amount;

    @Column(name = "net_revenue", columnDefinition = "numeric(12,2)")
    private java.math.BigDecimal net_revenue;

    @Column(name = "portal_request_id", columnDefinition = "varchar(128)")
    private String portal_request_id;

    @Column(name = "nights", insertable = false, updatable = false, columnDefinition = "integer GENERATED ALWAYS AS ((check_out_date - check_in_date))")
    private String nights;

    @Column(name = "total_guests", insertable = false, updatable = false, columnDefinition = "integer GENERATED ALWAYS AS (((adults + children) + infants))")
    private String total_guests;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.util.UUID getUuid() { return uuid; }
    public void setUuid(java.util.UUID uuid) { this.uuid = uuid; }

    public String getBookingNumber() { return booking_number; }
    public void setBookingNumber(String booking_number) { this.booking_number = booking_number; }

    public String getFolioNumber() { return folio_number; }
    public void setFolioNumber(String folio_number) { this.folio_number = folio_number; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getGuestName() { return guest_name; }
    public void setGuestName(String guest_name) { this.guest_name = guest_name; }

    public String getGuestEmail() { return guest_email; }
    public void setGuestEmail(String guest_email) { this.guest_email = guest_email; }

    public String getGuestPhone() { return guest_phone; }
    public void setGuestPhone(String guest_phone) { this.guest_phone = guest_phone; }

    public java.util.UUID getCorporateAccountId() { return corporate_account_id; }
    public void setCorporateAccountId(java.util.UUID corporate_account_id) { this.corporate_account_id = corporate_account_id; }

    public Long getRoomId() { return room_id; }
    public void setRoomId(Long room_id) { this.room_id = room_id; }

    public java.time.LocalDate getCheckInDate() { return check_in_date; }
    public void setCheckInDate(java.time.LocalDate check_in_date) { this.check_in_date = check_in_date; }

    public java.time.LocalDate getCheckOutDate() { return check_out_date; }
    public void setCheckOutDate(java.time.LocalDate check_out_date) { this.check_out_date = check_out_date; }

    public Integer getAdults() { return adults; }
    public void setAdults(Integer adults) { this.adults = adults; }

    public Integer getChildren() { return children; }
    public void setChildren(Integer children) { this.children = children; }

    public Integer getInfants() { return infants; }
    public void setInfants(Integer infants) { this.infants = infants; }

    public Long getRatePlanId() { return rate_plan_id; }
    public void setRatePlanId(Long rate_plan_id) { this.rate_plan_id = rate_plan_id; }

    public java.math.BigDecimal getRoomRate() { return room_rate; }
    public void setRoomRate(java.math.BigDecimal room_rate) { this.room_rate = room_rate; }

    public java.math.BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(java.math.BigDecimal subtotal) { this.subtotal = subtotal; }

    public java.math.BigDecimal getTaxAmount() { return tax_amount; }
    public void setTaxAmount(java.math.BigDecimal tax_amount) { this.tax_amount = tax_amount; }

    public java.math.BigDecimal getDiscountAmount() { return discount_amount; }
    public void setDiscountAmount(java.math.BigDecimal discount_amount) { this.discount_amount = discount_amount; }

    public java.math.BigDecimal getDiscountPercentage() { return discount_percentage; }
    public void setDiscountPercentage(java.math.BigDecimal discount_percentage) { this.discount_percentage = discount_percentage; }

    public java.math.BigDecimal getTotalAmount() { return total_amount; }
    public void setTotalAmount(java.math.BigDecimal total_amount) { this.total_amount = total_amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public java.math.BigDecimal getRateOverrideWeekday() { return rate_override_weekday; }
    public void setRateOverrideWeekday(java.math.BigDecimal rate_override_weekday) { this.rate_override_weekday = rate_override_weekday; }

    public java.math.BigDecimal getRateOverrideWeekend() { return rate_override_weekend; }
    public void setRateOverrideWeekend(java.math.BigDecimal rate_override_weekend) { this.rate_override_weekend = rate_override_weekend; }

    public String getDailyRates() { return daily_rates; }
    public void setDailyRates(String daily_rates) { this.daily_rates = daily_rates; }

    public Boolean isIsTourist() { return is_tourist; }
    public void setIsTourist(Boolean is_tourist) { this.is_tourist = is_tourist; }

    public java.math.BigDecimal getTourismTaxAmount() { return tourism_tax_amount; }
    public void setTourismTaxAmount(java.math.BigDecimal tourism_tax_amount) { this.tourism_tax_amount = tourism_tax_amount; }

    public Integer getExtraBedCount() { return extra_bed_count; }
    public void setExtraBedCount(Integer extra_bed_count) { this.extra_bed_count = extra_bed_count; }

    public java.math.BigDecimal getExtraBedCharge() { return extra_bed_charge; }
    public void setExtraBedCharge(java.math.BigDecimal extra_bed_charge) { this.extra_bed_charge = extra_bed_charge; }

    public java.math.BigDecimal getRoomCardDeposit() { return room_card_deposit; }
    public void setRoomCardDeposit(java.math.BigDecimal room_card_deposit) { this.room_card_deposit = room_card_deposit; }

    public java.math.BigDecimal getLateCheckoutPenalty() { return late_checkout_penalty; }
    public void setLateCheckoutPenalty(java.math.BigDecimal late_checkout_penalty) { this.late_checkout_penalty = late_checkout_penalty; }

    public Boolean isIsComplimentary() { return is_complimentary; }
    public void setIsComplimentary(Boolean is_complimentary) { this.is_complimentary = is_complimentary; }

    public String getComplimentaryReason() { return complimentary_reason; }
    public void setComplimentaryReason(String complimentary_reason) { this.complimentary_reason = complimentary_reason; }

    public java.time.LocalDate getComplimentaryStartDate() { return complimentary_start_date; }
    public void setComplimentaryStartDate(java.time.LocalDate complimentary_start_date) { this.complimentary_start_date = complimentary_start_date; }

    public java.time.LocalDate getComplimentaryEndDate() { return complimentary_end_date; }
    public void setComplimentaryEndDate(java.time.LocalDate complimentary_end_date) { this.complimentary_end_date = complimentary_end_date; }

    public java.math.BigDecimal getOriginalTotalAmount() { return original_total_amount; }
    public void setOriginalTotalAmount(java.math.BigDecimal original_total_amount) { this.original_total_amount = original_total_amount; }

    public Integer getComplimentaryNights() { return complimentary_nights; }
    public void setComplimentaryNights(Integer complimentary_nights) { this.complimentary_nights = complimentary_nights; }

    public Boolean isDepositPaid() { return deposit_paid; }
    public void setDepositPaid(Boolean deposit_paid) { this.deposit_paid = deposit_paid; }

    public java.math.BigDecimal getDepositAmount() { return deposit_amount; }
    public void setDepositAmount(java.math.BigDecimal deposit_amount) { this.deposit_amount = deposit_amount; }

    public java.time.OffsetDateTime getDepositPaidAt() { return deposit_paid_at; }
    public void setDepositPaidAt(java.time.OffsetDateTime deposit_paid_at) { this.deposit_paid_at = deposit_paid_at; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return payment_status; }
    public void setPaymentStatus(String payment_status) { this.payment_status = payment_status; }

    public String getPaymentMethod() { return payment_method; }
    public void setPaymentMethod(String payment_method) { this.payment_method = payment_method; }

    public String getPaymentNote() { return payment_note; }
    public void setPaymentNote(String payment_note) { this.payment_note = payment_note; }

    public String getMarketCode() { return market_code; }
    public void setMarketCode(String market_code) { this.market_code = market_code; }

    public Long getCompanyId() { return company_id; }
    public void setCompanyId(Long company_id) { this.company_id = company_id; }

    public String getCompanyName() { return company_name; }
    public void setCompanyName(String company_name) { this.company_name = company_name; }

    public java.time.LocalTime getCheckInTime() { return check_in_time; }
    public void setCheckInTime(java.time.LocalTime check_in_time) { this.check_in_time = check_in_time; }

    public java.time.LocalTime getCheckOutTime() { return check_out_time; }
    public void setCheckOutTime(java.time.LocalTime check_out_time) { this.check_out_time = check_out_time; }

    public java.time.OffsetDateTime getActualCheckIn() { return actual_check_in; }
    public void setActualCheckIn(java.time.OffsetDateTime actual_check_in) { this.actual_check_in = actual_check_in; }

    public java.time.OffsetDateTime getActualCheckOut() { return actual_check_out; }
    public void setActualCheckOut(java.time.OffsetDateTime actual_check_out) { this.actual_check_out = actual_check_out; }

    public Boolean isEarlyCheckIn() { return early_check_in; }
    public void setEarlyCheckIn(Boolean early_check_in) { this.early_check_in = early_check_in; }

    public Boolean isLateCheckOut() { return late_check_out; }
    public void setLateCheckOut(Boolean late_check_out) { this.late_check_out = late_check_out; }

    public Boolean isPreCheckinCompleted() { return pre_checkin_completed; }
    public void setPreCheckinCompleted(Boolean pre_checkin_completed) { this.pre_checkin_completed = pre_checkin_completed; }

    public java.time.OffsetDateTime getPreCheckinCompletedAt() { return pre_checkin_completed_at; }
    public void setPreCheckinCompletedAt(java.time.OffsetDateTime pre_checkin_completed_at) { this.pre_checkin_completed_at = pre_checkin_completed_at; }

    public String getPreCheckinToken() { return pre_checkin_token; }
    public void setPreCheckinToken(String pre_checkin_token) { this.pre_checkin_token = pre_checkin_token; }

    public java.time.OffsetDateTime getPreCheckinTokenExpiresAt() { return pre_checkin_token_expires_at; }
    public void setPreCheckinTokenExpiresAt(java.time.OffsetDateTime pre_checkin_token_expires_at) { this.pre_checkin_token_expires_at = pre_checkin_token_expires_at; }

    public String getSpecialRequests() { return special_requests; }
    public void setSpecialRequests(String special_requests) { this.special_requests = special_requests; }

    public String getInternalNotes() { return internal_notes; }
    public void setInternalNotes(String internal_notes) { this.internal_notes = internal_notes; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getPostType() { return post_type; }
    public void setPostType(String post_type) { this.post_type = post_type; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public java.math.BigDecimal getCommissionRate() { return commission_rate; }
    public void setCommissionRate(java.math.BigDecimal commission_rate) { this.commission_rate = commission_rate; }

    public java.time.OffsetDateTime getCancelledAt() { return cancelled_at; }
    public void setCancelledAt(java.time.OffsetDateTime cancelled_at) { this.cancelled_at = cancelled_at; }

    public Long getCancelledBy() { return cancelled_by; }
    public void setCancelledBy(Long cancelled_by) { this.cancelled_by = cancelled_by; }

    public String getCancellationReason() { return cancellation_reason; }
    public void setCancellationReason(String cancellation_reason) { this.cancellation_reason = cancellation_reason; }

    public java.math.BigDecimal getCancellationFee() { return cancellation_fee; }
    public void setCancellationFee(java.math.BigDecimal cancellation_fee) { this.cancellation_fee = cancellation_fee; }

    public Boolean isIsPosted() { return is_posted; }
    public void setIsPosted(Boolean is_posted) { this.is_posted = is_posted; }

    public java.time.LocalDate getPostedDate() { return posted_date; }
    public void setPostedDate(java.time.LocalDate posted_date) { this.posted_date = posted_date; }

    public java.time.OffsetDateTime getPostedAt() { return posted_at; }
    public void setPostedAt(java.time.OffsetDateTime posted_at) { this.posted_at = posted_at; }

    public Long getPostedBy() { return posted_by; }
    public void setPostedBy(Long posted_by) { this.posted_by = posted_by; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public Long getCreatedBy() { return created_by; }
    public void setCreatedBy(Long created_by) { this.created_by = created_by; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }

    public Long getUpdatedBy() { return updated_by; }
    public void setUpdatedBy(Long updated_by) { this.updated_by = updated_by; }

    public java.math.BigDecimal getTourismBillableAmount() { return tourism_billable_amount; }
    public void setTourismBillableAmount(java.math.BigDecimal tourism_billable_amount) { this.tourism_billable_amount = tourism_billable_amount; }

    public Boolean isCleaningPreference() { return cleaning_preference; }
    public void setCleaningPreference(Boolean cleaning_preference) { this.cleaning_preference = cleaning_preference; }

    public Long getBookingChannelId() { return booking_channel_id; }
    public void setBookingChannelId(Long booking_channel_id) { this.booking_channel_id = booking_channel_id; }

    public String getOtaReference() { return ota_reference; }
    public void setOtaReference(String ota_reference) { this.ota_reference = ota_reference; }

    public String getCommissionTypeOverride() { return commission_type_override; }
    public void setCommissionTypeOverride(String commission_type_override) { this.commission_type_override = commission_type_override; }

    public java.math.BigDecimal getCommissionValueOverride() { return commission_value_override; }
    public void setCommissionValueOverride(java.math.BigDecimal commission_value_override) { this.commission_value_override = commission_value_override; }

    public String getCommissionScopeOverride() { return commission_scope_override; }
    public void setCommissionScopeOverride(String commission_scope_override) { this.commission_scope_override = commission_scope_override; }

    public java.math.BigDecimal getCommissionAmount() { return commission_amount; }
    public void setCommissionAmount(java.math.BigDecimal commission_amount) { this.commission_amount = commission_amount; }

    public java.math.BigDecimal getNetRevenue() { return net_revenue; }
    public void setNetRevenue(java.math.BigDecimal net_revenue) { this.net_revenue = net_revenue; }

    public String getPortalRequestId() { return portal_request_id; }
    public void setPortalRequestId(String portal_request_id) { this.portal_request_id = portal_request_id; }

    public String getNights() { return nights; }
    public void setNights(String nights) { this.nights = nights; }

    public String getTotalGuests() { return total_guests; }
    public void setTotalGuests(String total_guests) { this.total_guests = total_guests; }
}
