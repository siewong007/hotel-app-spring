# Spring Boot Port of hotel-app — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-implement the hotel-app Rust backend as a feature-identical, interface-identical Spring Boot app in `/Volumes/APPLE EXTERNAL SSD /Personal Projects/hotel-app-spring`, bundling the React frontend verbatim.

**Architecture:** Single Maven module, package-per-domain mirroring the Rust layout (`controller → service → repository → dto/entity`). Spring Security filter chain with a custom JWT/session filter and PermissionEvaluator reproduce original auth/RBAC semantics; a global exception handler reproduces the exact error envelope.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Maven wrapper, Spring Data JPA (Hibernate-generated DDL with explicit `@Table`/`@Column` names), PostgreSQL, bucket4j, jjwt, Testcontainers, bcrypt cost 12.

**Spec:** `docs/superpowers/specs/2026-08-22-spring-boot-port-design.md` — read it before starting any task.

## Global Constraints

These bind EVERY task. Verbatim from the spec:

1. **Endpoints**: every route in `docs/api-parity-inventory.txt` exists with identical method + path under `/api`; infra routes `/health`, `/ws/status`, `/uploads/**` stay at root. Path parameter names match.
2. **Error envelope**: errors are `{"error": "<message>"}`. Status map: Database/Internal→500 generic message "Something went wrong on our end. Please try again.", Unauthorized→401, Forbidden→403, BadRequest→400 ("That request couldn't be processed." fallback), NotFound→404 ("We couldn't find what you were looking for."), Conflict→409 ("That action conflicts with the current state."), ServiceUnavailable→503 ("This service is temporarily unavailable."), TooManyRequests→429 (+ `Retry-After` header when retry seconds known), ProfileIncomplete→422 body `{"error","code":"profile_incomplete","missing_profile_fields":[...]}`. Messages are polished: trimmed, internal prefixes stripped ("Bad request: ", "Unauthorized: ", etc.), first char uppercased (rest untouched), terminal punctuation added if missing.
3. **JWT**: HS256, secret from `JWT_SECRET` (≥32 chars enforced at startup). Claims exactly: `sub` (user id as string), `username`, `iss`, `aud`, `exp` (OMITTED in desktop mode), `iat`, `roles: []`, optional `sid`. Every bearer-authenticated request re-validates `sid` against the active session record; missing/invalid sid on staff routes → 401 "Session-bound authentication is required". Guest-portal paths skip this check.
4. **Passwords**: bcrypt strength 12. Existing `$2a$`/`$2b$` hashes must verify.
5. **RBAC**: permission strings `<resource>:<action>`; `<resource>:manage` implies every action of that resource. Guest-portal endpoints use their own token scheme.
6. **CORS**: `ALLOWED_ORIGINS` list mode → credentials + headers Authorization/Content-Type/Accept + methods GET/POST/PUT/PATCH/DELETE/OPTIONS; wildcard mode → permissive no-credentials.
7. **Rate limiting**: in-memory, keyed per route-class like the original; 429 bodies polished; Retry-After where the original sets it.
8. **Security headers** on every response: HSTS `max-age=31536000; includeSubDomains`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 1; mode=block`, CSP `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none';`, `Referrer-Policy: strict-origin-when-cross-origin`.
9. **Business dates**: derived from DB session timezone set from `system_settings.timezone` (replicate `hotel_today`); never JVM-local math for business dates.
10. **Env vars**: read these names at startup: `DATABASE_URL` (parsed into datasource props), `JWT_SECRET`, `ENVIRONMENT`/`APP_ENV`, `BACKEND_PORT` (default 3030), `ALLOWED_ORIGINS`, `TRUST_PROXY_HEADERS`, `HOTEL_DESKTOP_MODE`, pool tuning (`DATABASE_MAX_CONNECTIONS`…), cache TTLs (`RBAC_CACHE_TTL_SECS`=30, `SETTINGS_CACHE_TTL_SECS`=30), PayPal/Google/SMTP/passkey vars from `hotel-app-be/.env.example`.
11. **JSON**: DTO field names byte-identical to serde output of the corresponding Rust model; Jackson fails-on-unknown-behavior mirrors serde defaults (ignore unknown fields).
12. **Money**: `BigDecimal` over `NUMERIC`.
13. **Audit**: every mutating endpoint writes an audit-log row through shared `AuditService` with the same action/resource strings as `services/audit.rs` call sites.
14. **Source of truth**: for any behavior question, read the cited Rust file in `/Volumes/APPLE EXTERNAL SSD /Personal Projects/hotel-app/hotel-app-be/` (always quote paths — volume name has a trailing space). The port matches the source even where the source looks odd.
15. **Verification gate**: `./mvnw verify` green before claiming done. No test may be skipped silently.

## Source map

- Rust backend root: `BE="/Volumes/APPLE EXTERNAL SSD /Personal Projects/hotel-app/hotel-app-be"`
- Route inventory: `docs/api-parity-inventory.txt` (METHOD /api/path -> rust_handler [permission] (rust_file))
- Endpoint list per task = `grep -E '<pattern>' docs/api-parity-inventory.txt`
- Frontend source: `FE="/Volumes/APPLE EXTERNAL SSD /Personal Projects/hotel-app/hotel-web-fe"`

---

### Task 1: Scaffold & core infrastructure

**Files:**
- Create: `pom.xml`, `mvnw`+`.mvn/` (via `spring init` or hand-rolled wrapper files copied from spring-boot maven plugin layout), `src/main/java/com/hotelapp/HotelApplication.java`
- Create: `src/main/java/com/hotelapp/core/config/AppProperties.java` (all env vars from Global Constraint 10), `WebConfig.java` (CORS), `SecurityHeadersFilter.java`, `JacksonConfig.java`
- Create: `src/main/java/com/hotelapp/core/error/ApiError.java`, `ApiExceptionHandler.java`, `ErrorMessagePolisher.java`
- Create: `src/main/java/com/hotelapp/core/web/HealthController.java` (`GET /health`, `GET /ws/status`)
- Create: `src/main/resources/application.yml` (env-driven)
- Test: `src/test/java/com/hotelapp/core/error/ErrorMessagePolisherTest.java`, `.../HealthIT.java`

**Interfaces (produced for all later tasks):**
- `ApiError` enum-style type with static factories: `database(String)`, `unauthorized(String)`, `forbidden(String)`, `badRequest(String)`, `notFound(String)`, `conflict(String)`, `internal(String)`, `serviceUnavailable(String)`, `tooManyRequests(String)`, `tooManyRequestsRetryAfter(String, long)`, `profileIncomplete(java.util.List<String>)` — each rendering per Global Constraint 2 via a `@RestControllerAdvice`.
- `AppProperties` bean exposing typed accessors used by later tasks (jwtSecret, environment, desktopMode, allowedOrigins…).
- `polish(String raw, String fallback)` static method replicating the Rust `polish_message`.

Steps:
- [ ] Init Maven project (Java 21, Boot 3.5.x): deps `spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`, `-actuator`(optional off by default), postgresql driver, `bucket4j-core`, `jjwt-api/impl/jackson`, `testcontainers` junit-jupiter+postgresql, `spring-security-test`.
- [ ] `application.yml`: `server.port=${BACKEND_PORT:3030}`; datasource parsed from `DATABASE_URL`; jackson `FAIL_ON_UNKNOWN_PROPERTIES=false`; open-in-view false.
- [ ] Implement `ErrorMessagePolisher` porting `polish_message` from `BE/src/core/error.rs` exactly (prefix strip list, uppercase-first-only, terminal punctuation). Unit-test against the exact cases in that file's behavior.
- [ ] Implement `ApiError` + handler advice producing Constraint 2 bodies/statuses incl. ProfileIncomplete special body and Retry-After header.
- [ ] Health controller: `{"status":"ok"}` after `SELECT 1`; else 503 `{"status":"error","message":"database unreachable"}`. `/ws/status` → `{"status":"connected"}`.
- [ ] CORS + security-header filters per Constraints 6/8.
- [ ] Write `HealthIT` (Testcontainers postgres) asserting both bodies.
- [ ] `./mvnw verify` green → commit `feat: scaffold core infrastructure, error envelope, health routes`.

### Task 2: Security core (Spring Security-first)

**Files:**
- Create: `core/security/JwtService.java`, `SessionBoundAuthFilter.java`, `GuestPortalBypassAware` logic inside the filter, `SecurityConfig.java` (SecurityFilterChain), `HotelPermissionEvaluator.java`, `AppMethodSecurity.java`, `RestAuthenticationEntryPoint.java`, `RestAccessDeniedHandler.java`, `core/security/RateLimitService.java` (bucket4j buckets mirroring `BE/src/core/rate_limiter.rs`)
- Modify: `SecurityConfig` wires CORS + filters + stateless sessions
- Test: `JwtServiceTest` (claims round-trip, exp omission in desktop mode), `SecurityGateIT` (401/403 envelope shapes, sid revocation, guest-portal bypass)

**Interfaces produced:**
- `JwtService.issueAccessToken(userId, username, roles, sessionId)` / `parse(String token)` returning claims record identical to Rust `Claims` (`BE/src/core/auth.rs:16-31`). Desktop mode omits `exp`.
- Filter behavior: Bearer tokens on non-guest paths require valid signature AND `sid` present AND session active in DB (query the refresh/session table from Task 3 entities); failures → exact 401 bodies from Rust middleware (`extract_claims`, `enforce_active_session` in `BE/src/routes/mod.rs:75-118`).
- Paths containing `/guest-portal/`: JWT parse failure falls through unauthenticated so domain code can apply guest-token validation.
- `hasPermission(userId, "resource:action")` evaluator replicating `check_permission` SQL from `BE/src/core/middleware.rs` incl. `resource:manage` implication; plus `anyOf` helper for `require_any_permission_helper`.
- Annotation convention for all controllers: `@PreAuthorize("@perm.check(#request, 'bookings:create')")` resolved via an `AuthenticatedUser` argument resolver OR a thin `PermissionGate` service called first-line in controllers — pick ONE pattern and document in `core/security/README.md`; later tasks follow it.

Steps:
- [ ] Read `BE/src/core/auth.rs`, `core/middleware.rs`, `routes/mod.rs` fully; write JwtService tests first (claims equality with Rust struct fields).
- [ ] Implement filter chain: stateless, CSRF off, permitAll at chain level (authorization happens per-handler like the original), authentication populated from JWT+DB session check.
- [ ] Entry point/denied handler emit Constraint 2 envelopes.
- [ ] RateLimitService: port bucket sizes/refill rates and which route classes get limiters from `BE/src/core/rate_limiter.rs` + `.layer(Extension(rate_limiters))` call sites in route files; 429s go through ApiError.
- [ ] `./mvnw verify` → commit `feat: security core - jwt, session binding, rbac evaluator, rate limits`.

### Task 3: Core entities + reference-data seeder

**Files:**
- Create: `core/domain/UserEntity.java`, `RoleEntity.java`, `PermissionEntity.java`, `RolePermissionEntity.java`, `UserRoleEntity.java`, `RefreshTokenEntity.java`, `SystemSettingEntity.java`, `RouteAccessPolicyEntity.java` (+ repositories)
- Create: `core/bootstrap/ReferenceDataSeeder.java` (ApplicationRunner, idempotent)
- Create: `core/domain/Support.java` (timezone-aware business-date helpers replicating `BE/src/core/db.rs::hotel_today`)
- Test: `SeederIT` (re-run idempotent; admin login possible after seed), `BusinessDateTest`

Notes:
- Entity table/column names copied from `BE/database/postgres/migrations/0001_v1_baseline.sql` definitions of: users, roles, permissions, role_permissions, user_roles, refresh_tokens, system_settings, route_access_policies. Only these domains now; other domains add their own entities in their tasks reading the same baseline file.
- Seeder ports the REFERENCE rows from `BE/database/postgres/seed.sql`: roles, permissions catalog, role_permissions mapping, system settings (incl. timezone), initial admin user (bcrypt-12 password matching seed's documented default), route_access_policies. Idempotent: re-run changes nothing.
- Steps: [ ] map tables → [ ] port reference data extraction from seed.sql → [ ] seeder IT → [ ] `./mvnw verify` → commit `feat: core entities + idempotent reference-data seeder`.

### Task 4: Auth domain

Endpoints (grep `'/api/auth/' docs/api-parity-inventory.txt` excluding 2fa/passkey): login, register, refresh, logout, verify-email, resend-verification (+ any others listed).
**Files:** create package `auth/` (AuthController, AuthService, dtos, UserCredentialsValidator); modify security config only if route-specific rate limits needed.
Port from `BE/src/routes/auth.rs`, `handlers/auth.rs`, `services/auth.rs`. Refresh cookie: HttpOnly, same flags as Rust (`BE/src/handlers/auth.rs` cookie building). Token rotation on refresh. Register validation messages match validator derives.
Tests: `AuthFlowIT` — register→login→refresh(rotation observed)→logout(session inactive)→old access token rejected with exact 401 body; wrong-password 401 body; lockout/rate-limit 429 if original has one.
Steps: [ ] failing ITs → [ ] implement → [ ] green + `./mvnw verify` → commit `feat(auth): login/register/refresh/logout/email-verification`.

### Task 5: RBAC + Users + Profile domains

Endpoints: grep `'/api/rbac|/api/users|/api/profile'`.
**Files:** packages `rbac/`, `users/`, `profile/`; reuse Task 3 entities.
Port `BE/src/routes/{rbac,users,profile}.rs` + handlers/services. RBAC snapshot shape must match frontend expectation (`access_snapshot` handler). Password change verifies old password (bcrypt) then rotates.
Tests: `RbacUsersProfileIT` covering snapshot fields, role assignment replace semantics (`PUT /users/{id}/roles`), profile patch + password change flows, permission gates returning exact 403s.
Commit `feat(rbac,users,profile): administration surface`.

### Task 6: Rooms + Rates + Booking channels

Endpoints: grep `'/api/rooms|/api/room-types|/api/rate|/api/channel'`.
Entities from baseline SQL: rooms, room_types, room_rates, rate_plans (+ channel tables). Port `BE/src/routes/{rooms,rates,booking_channels}.rs`, handlers/services. Availability search SQL semantics preserved (date overlap rules) using JPQL/native queries with identical predicates; business dates via Task 3 helpers.
Tests: `RoomsRatesIT` — CRUD gates (rooms:read/write/update/delete), availability search correctness on overlapping bookings seeded inline, status change side effects.
Commit `feat(rooms,rates,channels): inventory and pricing configuration`.

### Task 7: Guests + Companies

Endpoints: grep `'/api/guests|/api/companies'` EXCLUDING guest-portal and credits routes owned by bookings file.
Port `BE/src/routes/{guests,companies}.rs` + services. Sanitize free text like `BE/src/utils/sanitization.rs` (port Sanitizer into `core/text/Sanitizer.java` here, reused later).
Tests: `GuestsCompaniesIT` CRUD + search + linked accounts.
Commit `feat(guests,companies): profiles and corporate accounts`.

### Task 8: Bookings domain

Largest task. Endpoints: grep `'/api/bookings|/api/complimentary|/api/rate-codes|/api/market-codes'`.
Port `BE/src/routes/bookings.rs`, `handlers/bookings.rs`, `services/booking(s).rs`, `auto_checkin.rs`, `promotion_pricing.rs` hooks. Transactional check-in funnel (staff/self/ekyc auto) updating booking+room+payment+timeline+audit atomically (one @Transactional method). Void reverses payment/loyalty effects; reactivate rechecks availability.
Tests: `BookingLifecycleIT` — create→checkin→timeline→void→reactivate happy path with exact response shapes; complimentary mark/update/remove/convert-credits; book-with-credits; stats; advisory.
Commit `feat(bookings): full booking lifecycle`.

### Task 9: Housekeeping + Maintenance

Endpoints: grep `'/api/housekeeping|/api/maintenance'`.
Tests cover cleaning-task creation on checkout (integration hook from Task 8's turnover logic if already stubbed there), status transitions, maintenance events CRUD.
Commit `feat(housekeeping,maintenance)`.

### Task 10: Payments + Invoices

Endpoints: grep `'/api/payments|/api/invoices'`.
Port `BE/src/routes/payments.rs` + `services/payments.rs`, invoice_numbers service (sequence generation identical), deposit refund workflow, PayPal client via RestClient behind `PAYPAL_ENABLED` flag (`BE/src/services/paypal_client.rs`), admin approve/reject pending payments.
Money: BigDecimal; totals math mirrored from service tests in `BE/tests/` where present.
Tests: `PaymentsInvoicesIT` — calculate/record/refund-deposit/preview/generate/list with deterministic fixtures; PayPal disabled-path 503 ServiceUnavailable body parity.
Commit `feat(payments,invoices)`.

### Task 11: Ledgers

Endpoints: grep `'/api/ledgers'`.
Port routes/service; void/reverse semantics; summaries aggregation SQL parity.
Tests: `LedgersIT`.
Commit `feat(ledgers)`.

### Task 12: Loyalty module

Endpoints: grep `'/api/loyalty|/api/admin/loyalty'`.
Port `BE/src/modules/loyalty/**` (programs, memberships, points, rewards, redemptions, rules, member-facing views, socket-status JSON endpoint). Permission constants LOYALTY_READ/MANAGE sets resolved in Task 2 pattern.
Tests: `LoyaltyIT` — earn on checkout integration if wired in Task 10 flow, redeem/reject/approve flows.
Commit `feat(loyalty)`.

### Task 13: Promotions module

Endpoints: grep `'/api/promotions'`.
Port `BE/src/modules/promotions/**` incl. pricing interplay used by Task 8.
Tests: `PromotionsIT`.
Commit `feat(promotions)`.

### Task 14: eKYC + uploads handling

Endpoints: grep `'/api/ekyc'`.
Multipart upload storage under `uploads/` layout matching Rust (`private_uploads` vs public), authenticated download routes, admin review actions; auto check-in eligibility hook reused from Task 8.
Tests: `EkycIT` — upload→submit→status→admin approve→eligibility flips.
Commit `feat(ekyc): document upload and review workflow`.

### Task 15: Guest portal + public guest booking

Endpoints: grep `'/api/guest-portal|/api/portal|/api/public'`.
Separate bearer scheme: portal session token issued by verify; `me/*` endpoints; cancel eligible booking; bank-transfer receipt upload; pre-checkin; google guest registration (`BE/src/services/google_identity.rs`); online inventory + public availability hub (`BE/src/modules/guest_booking/**`).
Security wiring: guest paths bypass staff JWT (Task 2), implement `GuestTokenService` validating portal tokens.
Tests: `GuestPortalIT` — verify→me→pre-checkin→cancel; receipt submission appears in admin pending payments (Task 10).
Commit `feat(guest-portal,guest-booking): public guest surfaces`.

### Task 16: Analytics + Reports + Search

Endpoints: grep `'/api/analytics|/api/reports|/api/search'`.
Aggregation queries ported preserving exact numeric shaping (BigDecimal scale) and date-window logic on business dates.
Tests: `AnalyticsReportsSearchIT` with fixed fixtures asserting numbers.
Commit `feat(analytics,reports,search)`.

### Task 17: Night audit + Audit logs + Settings + Data transfer

Endpoints: grep `'/api/night-audit|/api/audit-logs|/api/settings|/api/data-transfer'`.
Night audit preview/run/get with posting eligibility rules from `BE/src/services/night_audit.rs` + scheduler (`night_audit_scheduler.rs`) as @Scheduled; CSV export streaming for audit logs; settings PATCH invalidation of settings cache; export/import zip semantics from `services/data_transfer.rs`.
Tests: `NightAuditSettingsAuditIT`.
Commit `feat(night-audit,audit,settings,data-transfer)`.

### Task 18: Teams + Support + Communications

Endpoints: grep `'/api/teams|/api/support|/api/communications'` (+ email worker env switches; SMTP worker as @Scheduled conditional bean).
Tests per domain happy path + permission gates.
Commit `feat(teams,support,communications)`.

### Task 19: Webhooks + Passkey + Two-factor auth

Endpoints: grep `'/api/webhooks|/api/passkey|/api/auth/2fa'`.
TOTP (RFC 6238, base32 secrets, otpauth URL identical format), WebAuthn server flows ported from `BE/src/services/passkey.rs` (challenge storage, credential model), webhook receivers with signature checks as in source.
Tests: `TwofaPasskeyWebhooksIT` (TOTP vector known-answer test; passkey ceremony happy path with soft-attestation fixture).
Commit `feat(webhooks,passkey,2fa)`.

### Task 20: Frontend bundle

**Files:** copy `FE` → `frontend/` EXCLUDING node_modules, dist, coverage, build logs; keep bun.lock, vite.config.ts proxy targets unchanged (dev proxy hits :3030 = our port).
Add root `Makefile` targets: `make fe-install`, `fe-dev`, `fe-build`. Verify `bun run typecheck && bun run lint && bun run test` pass UNCHANGED (no FE edits allowed — if something fails because of BE behavior, fix the BE).
Commit `chore: bundle react frontend verbatim`.

### Task 21: Docker/deploy topology

**Files:** `Dockerfile.api` (multi-stage maven build), `deploy/nginx.conf` + `deploy/Dockerfile.frontend`, `docker-compose.yml` mirroring `BE/../deploy/docker-compose.prod.yml` service topology (postgres + api + nginx) with required `POSTGRES_PASSWORD`/`JWT_SECRET` `:?` guards, `.env.example` at repo root documenting every var from Constraint 10.
Verify: `docker compose config` validates; api image builds.
Commit `build: compose stack - postgres + spring api + nginx frontend`.

### Task 22: Parity sweep & final verification

- [ ] Script-check: every line of `docs/api-parity-inventory.txt` has a matching Spring mapping (write a test that reflects over request mappings and compares against the inventory file committed in-repo; fail listing missing ones).
- [ ] Full `./mvnw verify`, FE gates re-run.
- [ ] Update README.md of new repo (quick start, env vars, parity statement).
Commit `test: endpoint parity enforcement + docs`.

---

## Self-review notes

- Spec coverage: all 270 inventoried endpoints are claimed by Tasks 4–19 via grep patterns over the inventory file; infra routes Task 1; parity enforcement Task 22.
- Type consistency: `ApiError` factories and `PermissionGate` pattern defined in Tasks 1–2 are referenced by all controller tasks; seeder entities (Task 3) precede all domain tasks that need auth in ITs.
- Placeholders: none — each task cites authoritative Rust files and concrete test scenarios; exact wire shapes come from those files at implementation time (they ARE the spec).
