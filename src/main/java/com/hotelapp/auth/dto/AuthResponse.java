package com.hotelapp.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AuthResponse(
        @JsonProperty("access_token") String accessToken,
        UserResponse user,
        List<String> roles,
        List<String> permissions,
        @JsonProperty("route_policies") List<RouteAccessPolicyDto> routePolicies,
        @JsonProperty("is_first_login") boolean isFirstLogin,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("recovery_codes_remaining") Integer recoveryCodesRemaining,
        @JsonProperty("profile_complete") boolean profileComplete,
        @JsonProperty("missing_profile_fields") List<String> missingProfileFields) {
}
