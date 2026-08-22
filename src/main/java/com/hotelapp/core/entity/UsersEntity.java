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
@Table(name = "users",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"email"}), @UniqueConstraint(columnNames = {"username"}), @UniqueConstraint(columnNames = {"uuid"})})
public class UsersEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid")
    private java.util.UUID uuid;

    @Column(name = "username", columnDefinition = "varchar(100)")
    private String username;

    @Column(name = "email", columnDefinition = "varchar(255)")
    private String email;

    @Column(name = "password_hash", columnDefinition = "varchar(255)")
    private String password_hash;

    @Column(name = "full_name", columnDefinition = "varchar(255)")
    private String full_name;

    @Column(name = "phone", columnDefinition = "varchar(20)")
    private String phone;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatar_url;

    @Column(name = "user_type")
    private String user_type;

    @Column(name = "guest_id")
    private Long guest_id;

    @Column(name = "is_active")
    private Boolean is_active;

    @Column(name = "is_verified")
    private Boolean is_verified;

    @Column(name = "is_locked")
    private Boolean is_locked;

    @Column(name = "is_super_admin")
    private Boolean is_super_admin;

    @Column(name = "email_verification_token", columnDefinition = "varchar(255)")
    private String email_verification_token;

    @Column(name = "email_token_expires_at")
    private java.time.OffsetDateTime email_token_expires_at;

    @Column(name = "two_factor_enabled")
    private Boolean two_factor_enabled;

    @Column(name = "two_factor_secret", columnDefinition = "varchar(255)")
    private String two_factor_secret;

    @Column(name = "two_factor_recovery_codes", columnDefinition = "text[]")
    private String[] two_factor_recovery_codes;

    @Column(name = "failed_login_attempts")
    private Integer failed_login_attempts;

    @Column(name = "locked_until")
    private java.time.OffsetDateTime locked_until;

    @Column(name = "last_login_at")
    private java.time.OffsetDateTime last_login_at;

    @Column(name = "last_login_ip")
    private String last_login_ip;

    @Column(name = "password_changed_at")
    private java.time.OffsetDateTime password_changed_at;

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

    @Column(name = "google_subject", columnDefinition = "varchar(255)")
    private String google_subject;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.util.UUID getUuid() { return uuid; }
    public void setUuid(java.util.UUID uuid) { this.uuid = uuid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return password_hash; }
    public void setPasswordHash(String password_hash) { this.password_hash = password_hash; }

    public String getFullName() { return full_name; }
    public void setFullName(String full_name) { this.full_name = full_name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAvatarUrl() { return avatar_url; }
    public void setAvatarUrl(String avatar_url) { this.avatar_url = avatar_url; }

    public String getUserType() { return user_type; }
    public void setUserType(String user_type) { this.user_type = user_type; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public Boolean isIsActive() { return is_active; }
    public void setIsActive(Boolean is_active) { this.is_active = is_active; }

    public Boolean isIsVerified() { return is_verified; }
    public void setIsVerified(Boolean is_verified) { this.is_verified = is_verified; }

    public Boolean isIsLocked() { return is_locked; }
    public void setIsLocked(Boolean is_locked) { this.is_locked = is_locked; }

    public Boolean isIsSuperAdmin() { return is_super_admin; }
    public void setIsSuperAdmin(Boolean is_super_admin) { this.is_super_admin = is_super_admin; }

    public String getEmailVerificationToken() { return email_verification_token; }
    public void setEmailVerificationToken(String email_verification_token) { this.email_verification_token = email_verification_token; }

    public java.time.OffsetDateTime getEmailTokenExpiresAt() { return email_token_expires_at; }
    public void setEmailTokenExpiresAt(java.time.OffsetDateTime email_token_expires_at) { this.email_token_expires_at = email_token_expires_at; }

    public Boolean isTwoFactorEnabled() { return two_factor_enabled; }
    public void setTwoFactorEnabled(Boolean two_factor_enabled) { this.two_factor_enabled = two_factor_enabled; }

    public String getTwoFactorSecret() { return two_factor_secret; }
    public void setTwoFactorSecret(String two_factor_secret) { this.two_factor_secret = two_factor_secret; }

    public String[] getTwoFactorRecoveryCodes() { return two_factor_recovery_codes; }
    public void setTwoFactorRecoveryCodes(String[] two_factor_recovery_codes) { this.two_factor_recovery_codes = two_factor_recovery_codes; }

    public Integer getFailedLoginAttempts() { return failed_login_attempts; }
    public void setFailedLoginAttempts(Integer failed_login_attempts) { this.failed_login_attempts = failed_login_attempts; }

    public java.time.OffsetDateTime getLockedUntil() { return locked_until; }
    public void setLockedUntil(java.time.OffsetDateTime locked_until) { this.locked_until = locked_until; }

    public java.time.OffsetDateTime getLastLoginAt() { return last_login_at; }
    public void setLastLoginAt(java.time.OffsetDateTime last_login_at) { this.last_login_at = last_login_at; }

    public String getLastLoginIp() { return last_login_ip; }
    public void setLastLoginIp(String last_login_ip) { this.last_login_ip = last_login_ip; }

    public java.time.OffsetDateTime getPasswordChangedAt() { return password_changed_at; }
    public void setPasswordChangedAt(java.time.OffsetDateTime password_changed_at) { this.password_changed_at = password_changed_at; }

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

    public String getGoogleSubject() { return google_subject; }
    public void setGoogleSubject(String google_subject) { this.google_subject = google_subject; }
}
