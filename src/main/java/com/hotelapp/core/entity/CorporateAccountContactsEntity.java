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
@Table(name = "corporate_account_contacts")
public class CorporateAccountContactsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "corporate_account_id")
    private java.util.UUID corporate_account_id;

    @Column(name = "name", columnDefinition = "varchar(255)")
    private String name;

    @Column(name = "email", columnDefinition = "varchar(255)")
    private String email;

    @Column(name = "phone", columnDefinition = "varchar(20)")
    private String phone;

    @Column(name = "role", columnDefinition = "varchar(100)")
    private String role;

    @Column(name = "is_primary")
    private Boolean is_primary;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.util.UUID getCorporateAccountId() { return corporate_account_id; }
    public void setCorporateAccountId(java.util.UUID corporate_account_id) { this.corporate_account_id = corporate_account_id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean isIsPrimary() { return is_primary; }
    public void setIsPrimary(Boolean is_primary) { this.is_primary = is_primary; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
}
