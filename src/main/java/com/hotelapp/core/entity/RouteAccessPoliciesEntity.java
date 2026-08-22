package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "route_access_policies")
public class RouteAccessPoliciesEntity {

    @Id
    @Column(name = "route_id", columnDefinition = "varchar(100)")
    private String route_id;

    @Column(name = "path", columnDefinition = "varchar(255)")
    private String path;

    @Column(name = "nav_label", columnDefinition = "varchar(100)")
    private String nav_label;

    @Column(name = "nav_group", columnDefinition = "varchar(50)")
    private String nav_group;

    @Column(name = "required_permissions", columnDefinition = "jsonb")
    private String required_permissions;

    @Column(name = "required_roles", columnDefinition = "jsonb")
    private String required_roles;

    @Column(name = "excluded_roles", columnDefinition = "jsonb")
    private String excluded_roles;

    @Column(name = "nav_permissions", columnDefinition = "jsonb")
    private String nav_permissions;

    @Column(name = "nav_roles", columnDefinition = "jsonb")
    private String nav_roles;

    @Column(name = "nav_excluded_roles", columnDefinition = "jsonb")
    private String nav_excluded_roles;

    @Column(name = "is_navigation", columnDefinition = "boolean")
    private Boolean is_navigation;

    @Column(name = "is_system_policy", columnDefinition = "boolean")
    private Boolean is_system_policy;

    @Column(name = "created_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at", columnDefinition = "timestamptz DEFAULT CURRENT_TIMESTAMP")
    private java.time.OffsetDateTime updated_at;

    public String getRouteId() { return route_id; }
    public void setRouteId(String route_id) { this.route_id = route_id; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getNavLabel() { return nav_label; }
    public void setNavLabel(String nav_label) { this.nav_label = nav_label; }

    public String getNavGroup() { return nav_group; }
    public void setNavGroup(String nav_group) { this.nav_group = nav_group; }

    public String getRequiredPermissions() { return required_permissions; }
    public void setRequiredPermissions(String required_permissions) { this.required_permissions = required_permissions; }

    public String getRequiredRoles() { return required_roles; }
    public void setRequiredRoles(String required_roles) { this.required_roles = required_roles; }

    public String getExcludedRoles() { return excluded_roles; }
    public void setExcludedRoles(String excluded_roles) { this.excluded_roles = excluded_roles; }

    public String getNavPermissions() { return nav_permissions; }
    public void setNavPermissions(String nav_permissions) { this.nav_permissions = nav_permissions; }

    public String getNavRoles() { return nav_roles; }
    public void setNavRoles(String nav_roles) { this.nav_roles = nav_roles; }

    public String getNavExcludedRoles() { return nav_excluded_roles; }
    public void setNavExcludedRoles(String nav_excluded_roles) { this.nav_excluded_roles = nav_excluded_roles; }

    public Boolean isIsNavigation() { return is_navigation; }
    public void setIsNavigation(Boolean is_navigation) { this.is_navigation = is_navigation; }

    public Boolean isIsSystemPolicy() { return is_system_policy; }
    public void setIsSystemPolicy(Boolean is_system_policy) { this.is_system_policy = is_system_policy; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
