package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class TwoFactorChallengesId implements Serializable {

    private Long user_id;
    private String purpose;

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TwoFactorChallengesId)) return false;
        TwoFactorChallengesId other = (TwoFactorChallengesId) o;
        return Objects.equals(this.user_id, other.user_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUserId(), getPurpose());
    }
}
