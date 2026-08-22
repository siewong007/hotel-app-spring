package com.hotelapp.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AccessSnapshot(
        List<String> roles,
        List<String> permissions,
        @JsonProperty("route_policies") List<RouteAccessPolicyDto> routePolicies) {
}
