package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class AuditLogsId implements Serializable {

    private Long id;
    private java.time.OffsetDateTime created_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLogsId)) return false;
        AuditLogsId other = (AuditLogsId) o;
        return Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getCreatedAt());
    }
}
