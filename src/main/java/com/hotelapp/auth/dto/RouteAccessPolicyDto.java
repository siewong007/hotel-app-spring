package com.hotelapp.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RouteAccessPolicyDto(
        @JsonProperty("route_id") String routeId,
        String path,
        @JsonProperty("nav_label") String navLabel,
        @JsonProperty("nav_group") String navGroup,
        @JsonProperty("required_permissions") List<String> requiredPermissions,
        @JsonProperty("required_roles") List<String> requiredRoles,
        @JsonProperty("excluded_roles") List<String> excludedRoles,
        @JsonProperty("nav_permissions") List<String> navPermissions,
        @JsonProperty("nav_roles") List<String> navRoles,
        @JsonProperty("nav_excluded_roles") List<String> navExcludedRoles,
        @JsonProperty("is_navigation") boolean isNavigation) {
}
