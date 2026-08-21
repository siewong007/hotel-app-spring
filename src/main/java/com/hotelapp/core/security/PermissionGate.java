package com.hotelapp.core.security;

import com.hotelapp.core.error.ApiError;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Port of the Rust require_permission_helper / require_any_permission_helper
 * gates: throws the exact Forbidden envelope on denial.
 */
@Service
public class PermissionGate {

    private static final Logger log = LoggerFactory.getLogger(PermissionGate.class);

    private final RbacService rbac;

    public PermissionGate(RbacService rbac) {
        this.rbac = rbac;
    }

    public void check(long userId, String permission) {
        if (!rbac.hasPermission(userId, permission)) {
            log.warn("Permission denied: user {} lacks '{}'", userId, permission);
            throw ApiError.forbidden("Missing permission: " + permission);
        }
    }

    public void checkAny(long userId, List<String> permissions) {
        for (String permission : permissions) {
            if (rbac.hasPermission(userId, permission)) {
                return;
            }
        }
        log.warn("Permission denied: user {} lacks any of {}", userId, permissions);
        throw ApiError.forbidden(
                "Missing one of required permissions: " + String.join(", ", permissions));
    }

    public boolean hasRole(long userId, String roleName) {
        return rbac.hasRole(userId, roleName);
    }
}
