# Security core convention

All controllers authorize through `PermissionGate`, called as the first line
of each handler method, mirroring the Rust router's
`require_permission_helper` calls:

```java
long userId = CurrentUser.require().userId();
permissionGate.check(userId, "bookings:create");
```

- `CurrentUser.require()` returns the `AuthenticatedUser` populated by
  `SessionBoundAuthFilter` (JWT parsed + DB session-active validated). It
  throws a 401-shaped `ApiError` when absent.
- `PermissionGate.check(userId, "resource:action")` enforces one permission,
  with `<resource>:manage` implying every action of that resource.
- `PermissionGate.checkAny(userId, list)` mirrors
  `require_any_permission_helper`.
- Rate limits: inject `RateLimitService` and call
  `rateLimitService.check(RateLimitService.Category.AUTH, clientIp)` before
  the handler body; on `!allowed` throw
  `ApiError.tooManyRequestsRetryAfter(msg, decision.retryAfterSecs())`.

Do not use Spring Security's `@PreAuthorize` for endpoint gates; the
`resource:manage` implication and exact denial envelopes live in
`RbacService`/`PermissionGate` and must stay single-sourced there.
