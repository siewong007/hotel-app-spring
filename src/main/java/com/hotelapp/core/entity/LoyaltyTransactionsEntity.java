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
@Table(name = "loyalty_transactions")
public class LoyaltyTransactionsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;

    @Column(name = "member_id", columnDefinition = "bigint")
    private Long member_id;

    @Column(name = "account_id", columnDefinition = "bigint")
    private Long account_id;

    @Column(name = "transaction_type", columnDefinition = "varchar(20)")
    private String transaction_type;

    @Column(name = "points_delta", columnDefinition = "integer")
    private Integer points_delta;

    @Column(name = "available_delta", columnDefinition = "integer")
    private Integer available_delta;

    @Column(name = "balance_after", columnDefinition = "integer")
    private Integer balance_after;

    @Column(name = "source_type", columnDefinition = "varchar(50)")
    private String source_type;

    @Column(name = "source_id", columnDefinition = "bigint")
    private Long source_id;

    @Column(name = "booking_id", columnDefinition = "bigint")
    private Long booking_id;

    @Column(name = "payment_id", columnDefinition = "bigint")
    private Long payment_id;

    @Column(name = "invoice_id", columnDefinition = "bigint")
    private Long invoice_id;

    @Column(name = "related_transaction_id", columnDefinition = "bigint")
    private Long related_transaction_id;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;

    @Column(name = "actor_user_id", columnDefinition = "bigint")
    private Long actor_user_id;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMemberId() { return member_id; }
    public void setMemberId(Long member_id) { this.member_id = member_id; }

    public Long getAccountId() { return account_id; }
    public void setAccountId(Long account_id) { this.account_id = account_id; }

    public String getTransactionType() { return transaction_type; }
    public void setTransactionType(String transaction_type) { this.transaction_type = transaction_type; }

    public Integer getPointsDelta() { return points_delta; }
    public void setPointsDelta(Integer points_delta) { this.points_delta = points_delta; }

    public Integer getAvailableDelta() { return available_delta; }
    public void setAvailableDelta(Integer available_delta) { this.available_delta = available_delta; }

    public Integer getBalanceAfter() { return balance_after; }
    public void setBalanceAfter(Integer balance_after) { this.balance_after = balance_after; }

    public String getSourceType() { return source_type; }
    public void setSourceType(String source_type) { this.source_type = source_type; }

    public Long getSourceId() { return source_id; }
    public void setSourceId(Long source_id) { this.source_id = source_id; }

    public Long getBookingId() { return booking_id; }
    public void setBookingId(Long booking_id) { this.booking_id = booking_id; }

    public Long getPaymentId() { return payment_id; }
    public void setPaymentId(Long payment_id) { this.payment_id = payment_id; }

    public Long getInvoiceId() { return invoice_id; }
    public void setInvoiceId(Long invoice_id) { this.invoice_id = invoice_id; }

    public Long getRelatedTransactionId() { return related_transaction_id; }
    public void setRelatedTransactionId(Long related_transaction_id) { this.related_transaction_id = related_transaction_id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Long getActorUserId() { return actor_user_id; }
    public void setActorUserId(Long actor_user_id) { this.actor_user_id = actor_user_id; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
