package com.hotelapp.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record UserResponse(
        long id,
        String username,
        String email,
        @JsonProperty("full_name") String fullName,
        String phone,
        @JsonProperty("is_active") boolean isActive,
        List<String> roles,
        List<String> permissions,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static UserResponse from(long id, String username, String email, String fullName,
            String phone, boolean isActive, Instant createdAt, Instant updatedAt) {
        return new UserResponse(id, username, email, fullName, phone, isActive,
                List.of(), List.of(), createdAt, updatedAt);
    }
}
