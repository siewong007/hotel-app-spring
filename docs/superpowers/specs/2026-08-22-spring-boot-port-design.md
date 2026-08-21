# Spring Boot Port of hotel-app — Design

Date: 2026-08-22
Source of truth: `/Volumes/APPLE EXTERNAL SSD /Personal Projects/hotel-app` (Rust/Axum backend, React frontend)
Target: `/Volumes/APPLE EXTERNAL SSD /Personal Projects/hotel-app-spring`

## Goal

Re-implement the hotel-app Rust backend as a Spring Boot application that is **feature-identical and interface-identical**: every HTTP endpoint (270 total, see `docs/api-parity-inventory.txt`) matches the original path, method, request/response JSON shape, status codes, error envelope, and auth/RBAC behavior — so the existing React frontend works unchanged. The React frontend is bundled verbatim in the new repo.

## Approved decisions

| Decision | Choice |
| --- | --- |
| Scope | Spring Boot backend + copy of `hotel-web-fe` as `frontend/` (unchanged). No Tauri desktop re-packaging. |
| Database | JPA/Hibernate-generated schema; entities pin explicit `@Table`/`@Column` names matching the original baseline SQL so generated DDL aligns table/column names with the original. |
| Architecture | **Spring Security-first**: use Spring Security filter chain + method security, customized to reproduce original semantics exactly. |
| Stack | Java 21, Spring Boot 3.5.x, Maven (`./mvnw`), Spring Data JPA, PostgreSQL JDBC, bucket4j, jjwt (or nimbus-jose), Testcontainers. |
| Layout | Single Maven module; package-per-domain mirroring the Rust domain layout. |

## Global fidelity contract

1. **Endpoints**: all routes from `docs/api-parity-inventory.txt` exist with identical method + path under `/api`, except infra routes `/health`, `/ws/status`, `/uploads/**` which stay at root. Path parameter names match (`{id}` etc.).
2. **Error envelope**: every error body is `{"error": "<message>"}`. Status mapping per source `core/error.rs`: Database/Internal→500 (generic "Something went wrong on our end. Please try again."), Unauthorized→401, Forbidden→403, BadRequest→400, NotFound→404, Conflict→409, ServiceUnavailable→503, TooManyRequests→429 (+ `Retry-After` header when retry seconds known), ProfileIncomplete→422 with body `{"error","code":"profile_incomplete","missing_profile_fields":[...]}`. Messages pass through the same "polish" rules (trim, strip internal prefixes, uppercase first char, terminal punctuation).
3. **Auth**: HS256 JWTs signed by `JWT_SECRET` (min 32 chars). Claims identical: `sub` (user id string), `username`, `iss`, `aud`, optional `exp` (desktop mode omits), `iat`, `roles[]`, optional `sid`. Every bearer request re-checks its `sid` against active sessions in the DB (logout invalidates immediately). Refresh tokens: random 512-bit, stored SHA-256-hashed in `refresh_tokens`, rotated on use, HttpOnly cookie. Guest portal uses a separate bearer scheme; on guest-portal paths a JWT-parse failure falls through to the guest validator instead of failing.
4. **Passwords**: bcrypt cost 12 (`BCryptPasswordEncoder`, strength 12) — compatible with existing hashes.
5. **RBAC**: `resource:action` checks; `<resource>:manage` implies all actions of that resource. Implemented as a custom Spring Security `PermissionEvaluator` replicating the original `check_permission` SQL semantics, plus `anyOf` variants for `require_any_permission_helper` call sites.
6. **CORS/rate limits/headers**: CORS from `ALLOWED_ORIGINS` (list mode allows credentials + Authorization/Content-Type/Accept; wildcard mode for desktop). In-memory rate limiting mirroring original per-route limiters incl. 429 + Retry-After. Security headers identical (HSTS, nosniff, DENY, XSS, CSP, referrer-policy).
7. **Business dates**: business-day math reads the timezone from `system_settings.timezone` via DB session time zone (replicating `hotel_today`); never JVM-local date math for business dates.
8. **Env vars**: same names/semantics as `hotel-app-be/.env.example`. The app reads `DATABASE_URL` directly (parsed into datasource properties) so deployment scripts stay compatible; likewise `JWT_SECRET`, `ENVIRONMENT`/`APP_ENV`, `BACKEND_PORT` (3030), `ALLOWED_ORIGINS`, `TRUST_PROXY_HEADERS`, pool tuning, cache TTLs, PayPal/Google/SMTP/passkey vars, `HOTEL_DESKTOP_MODE`.
9. **JSON field naming**: DTOs serialize field-for-field identical to serde output (camelCase where serde uses camelCase, matching each model). Jackson configured to match; unknown-field tolerance mirrors serde defaults.
10. **Pagination**: query-param and response shapes ported field-for-field from the original models (e.g. `PaginatedResponse<T>`).

## Architecture

```
hotel-app-spring/
├── pom.xml
├── src/main/java/com/hotelapp/
│   ├── HotelApplication.java
│   ├── core/            # config props, security config+filters, error handling,
│   │                    # rate limiting, db/timezone support, seeding
│   └── <domain>/        # controller / service / repository / dto / entity per domain
├── src/main/resources/  # application.yml (env-driven)
├── src/test/java/...    # unit + @SpringBootTest(Testcontainers) tests
├── frontend/            # verbatim copy of hotel-web-fe (bun/vite)
├── deploy/              # Dockerfile.api, nginx FE image, docker-compose.yml
└── docs/
```

Domain list (packages): `auth`, `profile`, `users`, `rbac`, `rooms`, `rates`, `guests`, `bookings`, `bookingchannels`, `housekeeping`, `maintenance`, `payments`, `ledgers`, `loyalty`, `promotions`, `ekyc`, `guestportal`, `analytics`, `settings`, `audit`, `nightaudit`, `search`, `datatransfer`, `teams`, `support`, `communications`, `companies`, `passkey`, `twofactor`, `webhooks`.

Request flow: `SecurityFilterChain` (CORS → rate limit → JWT/session filter) → `@RestController` (thin, `@PreAuthorize` via PermissionEvaluator) → service (transactions, audit events, cross-entity rules) → Spring Data repository → PostgreSQL. Mutating endpoints write audit-log rows through a shared `AuditService`, free-text sanitized like `utils/sanitization.rs`.

## Data layer

- Entities annotated with exact original table/column names; Hibernate `ddl-auto=update` for dev; schema exported for production reference.
- Reference-data seeder (`ApplicationRunner`, idempotent) replicates `database/postgres/seed.sql` essentials: roles, permissions, role_permissions, route_access_policies, system settings (incl. timezone), initial admin account.
- Money/decimal columns map to `NUMERIC` via `BigDecimal`.

## Frontend & deployment

- `frontend/` = verbatim copy of `hotel-web-fe`. Dev: vite :3000 proxies to backend :3030 (config unchanged). Prod: nginx serves built assets, proxies `/api`, `/uploads`, `/health` to the api container — mirroring `deploy/docker-compose.prod.yml` topology with the Spring api image.

## Background work & integrations

- `@Scheduled` night-audit scheduler, payment-receipt scheduler; SMTP email worker behind existing env switches; PayPal client and Google Identity flows via Spring `RestClient`; TOTP 2FA; WebAuthn/passkey endpoints ported.

## Testing strategy

- Unit tests per service (money math, policy logic, polish rules).
- Integration tests: `@SpringBootTest` + Testcontainers PostgreSQL; assert real status codes, envelope bodies, auth flows (login/refresh/logout/2FA), RBAC gates, and representative happy paths per domain.
- Gate: `./mvnw verify` green. Parity reviewer checks implemented handlers against `docs/api-parity-inventory.txt`.

## Execution

Subagent-driven development: scaffold/core first, then domains in dependency order:
auth/RBAC/users/profile → rooms/rates/guests/companies → bookings(+channels) → housekeeping/maintenance/payments/ledgers → loyalty/promotions/eKYC/guest-portal/guest_booking → analytics/reports/search/settings/audit/night-audit/datatransfer → teams/support/communications/webhooks/passkey/2FA → seeder hardening → frontend bundle → docker/deploy → final whole-branch parity review.

## Non-goals

- Tauri desktop shell re-packaging (frontend/backend remain desktop-compatible via env flags, but no installer pipeline).
- OpenAPI generation (README-based docs remain).
- Multi-instance caching (rate limiting/caching stay in-memory, as in the original).
