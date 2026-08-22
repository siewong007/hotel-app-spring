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
@Table(name = "support_messages")
public class SupportMessagesEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "conversation_id")
    private Long conversation_id;

    @Column(name = "author_type", columnDefinition = "varchar(16)")
    private String author_type;

    @Column(name = "author_guest_id")
    private Long author_guest_id;

    @Column(name = "author_user_id")
    private Long author_user_id;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "client_message_id", columnDefinition = "varchar(128)")
    private String client_message_id;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConversationId() { return conversation_id; }
    public void setConversationId(Long conversation_id) { this.conversation_id = conversation_id; }

    public String getAuthorType() { return author_type; }
    public void setAuthorType(String author_type) { this.author_type = author_type; }

    public Long getAuthorGuestId() { return author_guest_id; }
    public void setAuthorGuestId(Long author_guest_id) { this.author_guest_id = author_guest_id; }

    public Long getAuthorUserId() { return author_user_id; }
    public void setAuthorUserId(Long author_user_id) { this.author_user_id = author_user_id; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getClientMessageId() { return client_message_id; }
    public void setClientMessageId(String client_message_id) { this.client_message_id = client_message_id; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
