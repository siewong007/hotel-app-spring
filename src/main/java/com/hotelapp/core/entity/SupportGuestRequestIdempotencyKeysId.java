package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class SupportGuestRequestIdempotencyKeysId implements Serializable {

    private Long guest_id;
    private String idempotency_key;

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getIdempotencyKey() { return idempotency_key; }
    public void setIdempotencyKey(String idempotency_key) { this.idempotency_key = idempotency_key; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupportGuestRequestIdempotencyKeysId)) return false;
        SupportGuestRequestIdempotencyKeysId other = (SupportGuestRequestIdempotencyKeysId) o;
        return Objects.equals(this.guest_id, other.guest_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getGuestId(), getIdempotencyKey());
    }
}
