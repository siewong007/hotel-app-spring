package com.hotelapp.core.security;

import org.springframework.stereotype.Component;

/**
 * Thin static facade over PermissionGate for controllers that resolve the
 * gate lazily; keeps call sites terse.
 */
@Component
public class PermissionGateHelper {

    private static PermissionGate delegate;

    public PermissionGateHelper(PermissionGate delegate) {
        PermissionGateHelper.delegate = delegate;
    }

    public static void check(long userId, String permission) {
        if (delegate == null) {
            throw new IllegalStateException("PermissionGateHelper not initialized");
        }
        delegate.check(userId, permission);
    }
}
