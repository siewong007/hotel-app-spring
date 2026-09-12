# Mirror Upstream hotel-app (Aug 22 → Sep 11) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port every upstream `hotel-app` backend change made since the port baseline (commit `b7bce0a8`, 2026-08-22) through `origin/master` HEAD (2026-09-11) into this Spring Boot repo — 91 new endpoints (270 → 361), schema patches 0008–0016, and behavior deltas on existing endpoints — then re-sync the verbatim frontend.

**Architecture:** Same package-per-domain layout as the original port. Fat `@RestController`s per package backed by `core/entity` JPA entities; new reference data goes into `src/main/resources/db/reference-data.sql` (idempotent upserts like the existing settings block); new tables become new entities in `core/entity/` (Hibernate DDL).

**Spec:** `docs/superpowers/specs/2026-08-22-spring-boot-port-design.md` — the fidelity contract still binds every task (error envelope, JWT/session, RBAC, polish rules, JSON field naming, business dates, audit logging).

## Source map

- Rust backend root: `BE="/Volumes/APPLE EXTERNAL SSD /Personal Projects/hotel-app/hotel-app-be"`
- Baseline pin: `b7bce0a8` (upstream `origin/master` as of 2026-08-22, when this port was cut)
- Delta for any file: `git -C "$BE/.." diff b7bce0a8..origin/master -- <paths>`
- Route truth: `"/Volumes/APPLE EXTERNAL SSD /Personal Projects/hotel-app/docs/api/openapi.json"` (CI-verified against registered routes; 361 routes)
- Patches: `BE/database/postgres/patches/0008_*.sql` … `0016_*.sql`
- Frontend source: `FE="/Volumes/APPLE EXTERNAL SSD /Personal Projects/hotel-app/hotel-web-fe"`

## Upstream delta summary (already analyzed)

Schema patches to fold into entities + reference-data.sql:

| Patch | Content | Spring action |
| --- | --- | --- |
| 0008 | `email_deliveries.kind`/`campaign_link` + suppression `topic` CHECKs gain `checkout_receipt`, `pre_arrival_reminder` | Entity `@Check`/columnDefinition only if the port models CHECKs — verify; behavior lands with Task 6/7 |
| 0009 | setting `unpaid_hold_release_hours`='24' (number, booking, private) | reference-data.sql row |
| 0010 | new table `consent_records` | new `ConsentRecordsEntity` |
| 0011 | `guests.full_name` → `nick_name` (+ index/constraint renames) | `GuestsEntity` field rename + all refs (Task 3) |
| 0012 | new table `payment_retry_capabilities` | new `PaymentRetryCapabilitiesEntity` |
| 0013 | settings `require_two_factor_roles`='', `require_two_factor_grace_days`='14' | reference-data.sql rows |
| 0014 | `refresh_tokens.client_timezone` text nullable | `RefreshTokensEntity` field |
| 0015 | blank `totp_issuer_name`/`passkey_relying_party_name` values + new descriptions (empty = fall back to `hotel_name`) | update reference-data.sql rows |
| 0016 | setting `hotel_business_number`='SA2012724' (string, general, **public**) | reference-data.sql row |

**Endpoint delta (measured against actual Spring mappings, not the stale inventory):** upstream now registers **361** routes; Spring maps **292**. **88 upstream routes have no Spring mapping** (grouped in tasks below) and **19 Spring mappings are stale/phantom** — upstream renamed or restructured them (Task 12). `guests` also gains `first_name`/`last_name` exposure in payloads (columns exist in current upstream baseline, lines 1560–1561 — check `GuestsEntity` has them).

Stale mappings to reconcile in Task 12 (Spring path → upstream truth):

| Spring (stale) | Upstream | Action |
| --- | --- | --- |
| `GET /api/rooms/{id}` | `GET /api/rooms/{id}/detailed` | rename |
| `PUT /api/rooms/{id}` | `PATCH /api/rooms/{id}` | method change |
| `PUT /api/room-types/{id}` | `PATCH /api/room-types/{id}` | method change |
| `PUT /api/rate-plans/{id}` | `PATCH /api/rate-plans/{id}` | method change |
| `GET /api/night-audit/runs{,/{id}}` | `GET /api/night-audit{,/{id}}` | rename |
| `GET /api/guest-portal/me/summary` | `GET /api/guest-portal/me` | rename (Task 4 owns the new `/me` handler) |
| `GET /api/passkey/register/options` | `POST /api/auth/passkey/register/start` (+`finish`) | rename + method (Task 10 owns) |
| `GET /api/communications/{deliveries,suppressions,templates}`, `POST /api/communications/campaigns` | moved under `/api/admin/communications/*` | remove stale, port admin versions (Task 6 owns) |
| `POST /api/promotions`, `DELETE /api/promotions/{id}` | `/api/admin/promotions` POST/PUT; no DELETE | move to admin paths (Task 8 owns) |
| `DELETE /api/admin/loyalty/rewards/{id}` | gone upstream | remove (verify in `modules/loyalty/routes.rs` first) |
| `GET /api/loyalty/programs`, `POST /api/loyalty/rewards/redeem` | `/api/loyalty/{enroll,me,me/activity,rewards,rewards/{id}/redeem}` | reconcile member-surface naming (Task 12) |
| `POST /api/data-transfer/export` | `GET /api/data-transfer/export` + new `GET /export/preview` | method change + add preview (Task 11 owns) |
| `POST /api/bookings/{id}/checkout` | not registered upstream | remove or map to the real checkout flow — verify in `routes/bookings.rs` |

**Concurrent-session note:** the upstream repo is shared — always diff against `origin/master`, never the working tree.

---

### Task 1: Regenerate the parity inventory

**Files:**
- Modify: `docs/api-parity-inventory.txt` (append 91 new routes in existing format `METHOD /api/path -> handler [permission] (rust_file)`)

Handler names + permission gates come from the upstream route registration for each new path — grep the file named in the route prefix:

| Prefix | Registration file |
| --- | --- |
| `/api/admin/communications`, `/api/communications` | `BE/src/modules/communications/routes.rs` |
| `/api/admin/promotions`, `/api/promotions`, `/api/admin/vouchers`, portal promotions/vouchers | `BE/src/modules/promotions/routes.rs` |
| `/api/support` | `BE/src/modules/support/routes.rs` |
| `/api/guest-portal/*`, `/api/booking/*`, `/api/admin/online-inventory` | `BE/src/routes/guest_portal.rs`, `BE/src/modules/guest_booking/routes.rs` |
| `/api/auth/login/lookup` | `BE/src/routes/auth.rs` |
| `/api/data-transfer/import` | `BE/src/routes/data_transfer.rs` |
| `/api/bookings/{id}/release` | `BE/src/routes/bookings.rs` |

Steps:
- [x] For each of the 91 `(METHOD, path)` pairs present in `openapi.json` but absent from the inventory (recompute the diff — do not trust a stale list; ~3 are already mapped in Spring even though unlisted), find the `.route()` line in the registration file above; record handler fn name and `.layer(middleware::require_permission(...))`/`require_any_permission` gate exactly as written.
- [x] Append lines to `docs/api-parity-inventory.txt` sorted with the existing entries (the file is roughly grouped by prefix — keep new lines adjacent to their prefix siblings). Also fix any stale lines matching the Task 12 rename table so the inventory documents upstream's *current* 361, not 270 old + 91 new blindly.
- [x] Run the openapi-vs-Spring-mappings diff (the script used to produce the "88 truly missing" list — save it as `tools/check_openapi_parity.py` alongside `check_parity.py`, since the openapi skeleton is now the route truth). Expect 88 missing; that output is the live TODO for Tasks 4–11.
- [x] Commit `chore(docs): extend parity inventory to 361 upstream routes`.

### Task 2: Schema sync — entities + reference data (patches 0008–0016)

**Files:**
- Create: `src/main/java/com/hotelapp/core/entity/ConsentRecordsEntity.java`, `PaymentRetryCapabilitiesEntity.java`
- Modify: `src/main/java/com/hotelapp/core/entity/RefreshTokensEntity.java` (+`clientTimezone` → `client_timezone` text nullable), `src/main/java/com/hotelapp/core/entity/GuestsEntity.java` (rename `fullName`→`nickName`, column `nick_name`; add `firstName`/`lastName` if absent)
- Modify: `src/main/resources/db/reference-data.sql`
- Modify: `src/main/java/com/hotelapp/core/bootstrap/ReferenceDataSeeder.java` only if the settings block isn't picked up generically (it is — verify)

Entity column lists (from the patch DDL — copy names/types exactly):
- `consent_records`: `id` bigint identity PK; `subject_type` varchar(20) not null; `user_id`,`guest_id`,`booking_id` bigint nullable FKs; `document_type` varchar(40) not null (terms_of_service|privacy_notice|payment_terms|ekyc_biometric); `document_version` varchar(40) not null; `locale` varchar(10) default 'en' (en|ms); `granted` boolean not null; `source` varchar(40) not null (registration|online_booking|payment|ekyc|guest_portal|front_desk); `ip_address` inet; `user_agent` text; `withdrawn_at` timestamptz; `created_at` timestamptz default now(). Reproduce the patch's indexes (read the tail of `0010_consent_records.sql` for exact index defs).
- `payment_retry_capabilities`: `id` bigint identity PK; `booking_id` bigint not null; `payment_id` bigint nullable; `token_hash` varchar(80) not null; `expires_at` timestamptz not null; `consumed_at` timestamptz; `replacement_payment_id` bigint; `created_at` timestamptz default now(). Indexes per tail of `0012_payment_retry_capabilities.sql`.

reference-data.sql changes (match upstream text verbatim):
- [x] Add `unpaid_hold_release_hours`, `require_two_factor_roles`, `require_two_factor_grace_days`, `hotel_business_number` rows to the `system_settings` INSERT … ON CONFLICT block (values/descriptions/is_public per patches 0009/0013/0016).
- [x] Patch 0015 semantics: `totp_issuer_name`/`passkey_relying_party_name` seed `value=''` and the new descriptions ("Empty uses hotel_name."). Mirror both the INSERT values and the metadata-sync UPDATE clause if the file has one for these keys.
- [x] Check `GuestsEntity` for `firstName`/`lastName` fields against current upstream baseline (`git -C "$BE/.." show origin/master:hotel-app-be/database/postgres/migrations/0001_v1_baseline.sql | sed -n '1555,1575p'`); add if missing.
- [x] `./mvnw -q compile` clean; `./mvnw verify` green (SeederIT covers re-run idempotency — extend it if it asserts the settings row count).
- [x] Commit `feat(data): schema sync to upstream patch catalog 0016 (consent records, payment retry capabilities, client timezone, new settings)`.

### Task 3: Guest `nick_name` rename + guest payload deltas

Upstream sources (read the diffs, not just the files):
- `git diff b7bce0a8..origin/master -- src/handlers/guests.rs src/services/guests.rs src/repositories/guests.rs src/models/guests.rs src/routes/guests.rs src/modules/guest_booking/`
- Commits: `83ede5b6` (rename), `ca3f2af5` (identifier field), `f8674b93` (first/last name payloads), `82982b32` (legal name on folios), `e6456f0c` (linked_guests columns), `1d8bb780`+`f3b4ee7d` (409 `guest_name_taken` for duplicate anonymous nicknames — replaces the old suffixing behavior).

**Files:** `guests/GuestsController.java`, `bookings/BookingsController.java`, `portal/PortalController.java`, `engagement/EngagementController.java`, `admin/AdminController.java`, `insights/InsightsController.java`, `gaps/*Controller.java`, `core/entity/GuestsEntity.java` (done in Task 2), DTOs under each package, `ReferenceDataSeeder.java`/sample-data.sql if they insert guests.

Steps:
- [x] Rename every **guests-table** `full_name`/`fullName` reference to `nick_name`/`nickName` — JSON field names too (upstream renamed the serialized field; verify per-model serde names in the diff). Do NOT touch `users.full_name` or `ekyc_verifications.full_name`.
- [x] Expose `first_name`/`last_name` on guest payloads exactly where upstream added them (guest detail, booking guest blocks, folios after check-in uses legal names per `82982b32`).
- [ ] Duplicate-nickname rule: anonymous booking hold with an already-taken nick → `409 {"error":…,"code":"guest_name_taken"}` — copy the exact body/code from upstream handler (grep `guest_name_taken` upstream). Remove any suffixing behavior if the port mirrored it. **(deferred to Task 5 — the anonymous-booking hold endpoint is part of the missing public-booking surface; guest-CRUD duplicate names already return 400 per upstream)**
- [x] `linked_guests` selection fix (`e6456f0c`): select every non-defaulted column — apply to the corresponding Spring query.
- [x] Tests: extend/create `GuestsIT` — payload shape incl. `nickName`/`firstName`/`lastName`; 409 on duplicate anonymous name; booking summary still renders guest name.
- [x] `./mvnw verify` green → commit `feat(guests): mirror nick_name rename, legal-name payloads, guest_name_taken 409`.

### Task 4: Guest portal "me" hub + portal auth flows

~35 endpoints (all in `portal/` package; split controllers if one file grows past ~600 lines — e.g. `PortalMeController`, `PortalPaymentsController`, `PortalSupportController`):

```
GET  /api/guest-portal/me, /me/availability, /me/benefits, /me/booking-options,
     /me/bookings, /me/credits, /me/ekyc, /me/membership, /me/notification-preferences,
     /me/promotions, /me/transactions, /me/vouchers, /me/support/conversations,
     /me/support/conversations/{id}, /me/support/socket, /booking, /payment-config
PATCH /api/guest-portal/me/profile
PUT  /api/guest-portal/me/notification-preferences
POST /api/guest-portal/me/booking-quote, /me/bookings, /me/bookings/{id}/cancel,
     /me/booking-voucher-options, /me/ekyc/documents, /me/ekyc/submit,
     /me/payments/bank-transfer, /me/payments/paypal/create-order,
     /me/payments/paypal/capture, /me/payments/{id}/receipt,
     /me/promotions/{id}/claim, /me/support/conversations,
     /me/support/conversations/{id}/messages, /me/support/conversations/{id}/reopen,
     /booking/payments/bank-transfer, /booking/payments/paypal/create-order,
     /booking/payments/paypal/capture, /booking/payments/{id}/receipt,
     /auto-checkin, /pre-checkin, /claim-account, /logout
```

Upstream sources:
- `BE/src/routes/guest_portal.rs` (registration + gates), `BE/src/handlers/guest_portal.rs` (or wherever handlers live — find via route registrations), plus portal sections of `src/modules/guest_booking/`, `src/modules/ekyc/portal.rs`, `src/modules/loyalty/` (hub.rs — member-facing views), `src/modules/support/` (guest conversations), `src/modules/promotions/` (claim), `src/services/payments.rs` portal paths.
- Key commits: `f9aa99a6` (profile + devices #162), `2ff8f664` (manage credentials #161), `d48ab2f9` (pre-check-in), `9a496614` (verify by name + booking number — check whether the existing `/guest-portal/verify` in the port needs the same change), `3f34576a` (hash portal tokens), `d880110d` (tokens out of URLs), `235fb117` (payment-config requires booking/guest session), `1cf73f5c` (tourism tax on foreign bookings).

Steps:
- [x] Read the whole `git diff b7bce0a8..origin/master -- src/routes/guest_portal.rs` first; tabulate which endpoints are new vs modified.
- [x] Port the guest-token/session validation changes first (hashed portal tokens — `GuestPortalSessionsEntity` may need a `token_hash` column; check patch/entity diffs). → `PortalAuth`: SHA-256 session hashes, `sha256:`-prefixed booking tokens with legacy plaintext read, `x-booking-access-token` header-first resolution.
- [x] Implement endpoint groups in order: me-core (profile/bookings/credits/transactions/membership/benefits/vouchers) → me-payments + booking payments → me-ekyc → me-support → auth flows (claim-account/logout/auto-checkin/pre-checkin/payment-config). Each group: copy request/response DTO field names byte-identical from upstream models. **(4a landed me-core + all payments + auth flows + auto/pre-check-in; **4d landed** — `ekyc/EkycPortal*` (status/upload/submit, GuestPortal channel: stored-path-only image refs, EKYC consent recorded), `EkycPortalContractTest` 12 cases;  me-support/prefs/promos/vouchers → Task 4e, availability socket → Task 4f; **4c landed** — `guestbooking/` package: public offers/quote/reservations + me booking-options/quote/voucher-options/bookings + admin online-inventory, `FunnelContractTest` 23 cases; **4e landed** — `communications/PreferencesController` (prefs GET/PUT), `promotions/GuestPromotions{,Tx,Controller}` + `loyalty/GuestLoyalty` (guest promo list incl. July Deluxe loyalty offer, idempotent claim, voucher list), `support/GuestSupport{,Tx,Controller}` + `SupportHub` (conversation list/detail/create/message/reopen, guest-request idempotency keys, SLA mutation on reply/reopen), `GuestSupportContractTest` 9 + `GuestPromotionsContractTest` 5; stale `GET /api/guest-portal/me/summary` stub removed (renamed `/me` upstream); support socket → Task 4f, staff support → Task 9, admin promotions → Task 8)**
- [x] Permission/session semantics: all `me/*` behind the guest bearer scheme (existing `GuestTokenService`-equivalent in `core/security`); `payment-config` now requires a booking or guest session (401s otherwise, exact upstream body). → `PortalAuth.requireGuestSession*` + `require*BookingToken*`; `payment-config` accepts either.
- [ ] Tests: `PortalMeIT` — verify→me→profile patch→booking quote→cancel→notification prefs round-trip; payment-config gate; pre-check-in by name+booking number; claim-account flow. **(`PortalContractTest` unit coverage landed — token hashing/legacy match, plausibility, header-vs-path resolution, eKYC normalisation, receipt signatures; IT still owed — needs Docker/Postgres)**
- [x] `./mvnw verify` green → commit `feat(guest-portal): me hub, payments, ekyc, support, pre-check-in, claim-account`. **(`mvnw test` 58/58 green; `verify` ITs need Docker; **4f landed** — `portal/PortalSocketHandshake` + `PortalWebSocketConfig` (raw WS upgrades, token via `Sec-WebSocket-Protocol`, marker echo, session auth + `guest:{id}` read budget, `{error,…}` envelope rejections incl. `Retry-After`), `guestbooking/AvailabilitySocketHandler` (`hotel-guest-availability`), `support/SupportSocketHandler` (`hotel-guest-support`, per-guest event filter, `guest_id` never serialized), `spring-boot-starter-websocket`, `PortalSocketContractTest` 4 cases, parity checker counts `addHandler` registrations — 44 missing, both WS routes resolved)**

### Task 5: Public booking flows + payment recovery + hold release

Endpoints:
```
GET  /api/booking/offers, /api/booking/recover-payment/{token}
POST /api/booking/quote, /api/booking/reservations,
     /api/booking/recover-payment/{token}/bank-transfer,
     /api/booking/recover-payment/{token}/payments/{id}/receipt,
     /api/booking/recover-payment/{token}/paypal/create-order,
     /api/booking/recover-payment/{token}/paypal/capture,
     /api/bookings/{id}/release
```

Upstream sources:
- `BE/src/modules/guest_booking/**` (quote/offers/reservations — availability.rs, service.rs, handlers.rs)
- `BE/src/routes/payment_retry.rs`, `BE/src/services/payment_retry.rs` (recover-payment capability flow over `payment_retry_capabilities` — token hash, expiry, single-consumption, duplicate-resolves-to-replacement)
- `BE/src/services/unpaid_hold_scheduler.rs` (@Scheduled equivalent; window from `unpaid_hold_release_hours`, 0 disables, front-desk bookings never auto-released)
- Commits: `0af2fe4f` (anonymous checkout + stale hold release), `7df96103` (fast booking with only a guest name), `03355785` (anonymous receipt upload), `d930b057` (email payment retry #141), `57744fc7` (anonymous Complete-payment deep link #131), `ab946136` (bank details required).

Steps:
- [x] Port `payment_retry_capabilities` lifecycle: issue on rejected payment (email link), GET recovery page data by token hash, consume-once on replacement payment, PayPal order re-capture after consumption. `sha256:`-prefixed hex hash, same scheme as booking-access tokens. **(landed — `paymentrecovery/PaymentRecovery` + `PaymentRecoveryController`: view/bank-transfer/paypal create+capture/receipt; consume inside the claim tx in `PortalPaymentTx`; restore on PayPal order failure; duplicate resolves to the existing payment; `PaymentRecoveryContractTest` 9 cases)**
- [x] Port booking quote/offers/reservations (public, rate-limited like upstream) incl. anonymous checkout semantics and `guest_name_taken` interplay (Task 3). **(landed in Task 4c — `guestbooking/` package)**
- [x] `@Scheduled` unpaid-hold-release worker mirroring `unpaid_hold_scheduler.rs` (reads setting each tick; logs audit rows like upstream). **(landed — `bookings/UnpaidHoldScheduler` 15-min fixedDelay + `BookingRelease.releaseStaleUnpaidHolds`: website/online sources only, 200/sweep cap, per-booking re-check under current state)**
- [x] `POST /api/bookings/{id}/release` staff endpoint — port gate + side effects from `src/routes/bookings.rs` + handlers diff. **(landed — `bookings:update` gate, `BookingRelease` + `BookingReleaseTx.performRelease`: pending_payment only, collected-money refused, reason 4..500, void+releaseRoom+voidPayments+restoreCredits+recompute+history+modification+audit in one tx)**
- [ ] Receipt upload for anonymous bookers reuses the bank-transfer receipt pipeline (Task 10 payments delta if shared).
- [ ] Tests: `PublicBookingIT` — quote→reserve→recover-payment happy path; capability single-use + expiry; hold release flips stale unpaid booking.
- [ ] `./mvnw verify` green → commit `feat(booking): public quote/offers/reservations, payment recovery capabilities, unpaid hold release`.

### Task 6: Communications module — admin surface + unsubscribe + feed

Endpoints:
```
GET    /api/admin/communications/{audience,campaigns,campaigns/{id},campaigns/{id}/deliveries,deliveries,guests/{guest_id}/consent,suppressions,templates}
POST   /api/admin/communications/{campaigns,campaigns/{id}/cancel,campaigns/{id}/preview,campaigns/{id}/schedule,campaigns/{id}/test-send,guests/{guest_id}/consent,suppressions,templates,templates/{id}/deactivate}
PUT    /api/admin/communications/{campaigns/{id},templates/{id}}
DELETE /api/admin/communications/suppressions/{email}
GET|POST /api/communications/unsubscribe/{token}
```

Upstream: `BE/src/modules/communications/{routes,handlers,service,repository,models,validation,tokens}.rs` — diff vs baseline to catch the paged delivery feed (`1642ca9b`), transactional-bypass (`8a28d8e2`), unsubscribe metering (`03057bd3`), unsubscribe token signed with guest id (`dd50d694`), consent ledger integration (`fc2137a8` → `modules/consent`).

**Files:** extend `engagement/` (existing `/api/communications/*` mappings already live in `EngagementController`) or create `communications/` package — match however the port currently splits admin vs public comms routes.

Steps:
- [x] Port campaigns CRUD + schedule/cancel/preview/test-send + per-campaign deliveries; templates CRUD + deactivate; suppressions list/add/remove; audience query; paged deliveries feed; guest consent GET/POST — consent goes to `notification_subscriptions` + `notification_consent_events` (upstream's consent ledger), not `consent_records`. Landed in new `communications/` package: `CommsModels`, `CommsValidation`, `CommunicationsAdmin`, `CommunicationsController`, `SmtpTransport` (`SMTP_*` envs → JavaMailSender), `UnsubscribeTokens` (HMAC-SHA256 over base64url guest id, JWT_SECRET key).
- [x] Public unsubscribe GET (page data) + POST (action) — `UnsubscribeTokens` HMAC verify, invalid → 404 "Invalid unsubscribe link", SENSITIVE IP limiter; global unsubscribe adds `email_suppressions` row (reason `unsubscribe`, source `unsubscribe_link`) in a second tx like upstream.
- [x] RBAC gates match handlers: read → `communications:read`; compose → `communications:compose`; test-send/schedule/cancel → `communications:send`; manage → `communications:manage` (suppressions, deactivate, staff consent POST).
- [x] Tests: `CommunicationsContractTest` — 17 cases mirroring upstream's (tiers/subscription gate, campaign/template/suppression/email validation, template render+escape, token round-trip + tamper, SMTP env config). DB-level IT deferred (no Testcontainers coverage for comms yet).
- [x] `./mvnw test` green (137) → commit `feat(communications): admin campaigns/templates/suppressions, consent, unsubscribe` + push.

### Task 7: Communications workers — schedulers + email triggers

Upstream: `BE/src/modules/communications/{worker,scheduler,transport,email_layout}.rs`; booking-side triggers in `src/services/` diffs (`a105bbd7` checkout receipt outbox, `0f1ce5a2` pre-arrival reminders, `7c286689` confirmation emails on confirm/payment, `4c10aaa4` branded guest mail + spam notice, `7e082f99` transactional consent gate, `dd50d694`).

Steps:
- [ ] Port the durable outbox pattern (`email_deliveries` rows → worker → transport) if not already ported; `@Scheduled` worker + scheduler beans behind the same env switches as upstream (`SMTP_*`).
- [ ] Wire triggers: booking confirm/payment → confirmation email; checkout → receipt email; pre-arrival window → reminder; transactional kinds bypass topic subscriptions but still honor suppression list; kind CHECK expanded in Task 2 (verify no entity-level constraint blocks the new kinds).
- [ ] Email layout/branding per `email_layout.rs` (hotel name, spam notice).
- [ ] Tests: `EmailTriggersIT` — confirm booking writes a `booking_confirmation` delivery row; unsubscribe token in row validates; suppression blocks campaign but not transactional.
- [ ] `./mvnw verify` green → commit `feat(communications): outbox worker, schedulers, transactional email triggers`.

### Task 8: Promotions + vouchers

Endpoints:
```
GET  /api/admin/promotions, /api/admin/promotions/{id}, /api/admin/vouchers,
     /api/promotions/{id}           (GET /api/promotions already mapped — verify shape unchanged)
POST /api/admin/promotions, /api/admin/promotions/{id}/{publish,pause,archive},
     /api/admin/vouchers, /api/admin/vouchers/{id}/revoke
PUT  /api/admin/promotions/{id}
```
(portal claim + booking-voucher-options landed in Task 4)

Upstream: `git diff b7bce0a8..origin/master -- src/modules/promotions/` — routes/handlers/service/repository/models/validation.

Steps:
- [ ] Port admin lifecycle (draft→publish→pause→archive state machine exactly as upstream `service.rs`), voucher create/list/revoke (`VouchersEntity`, `VoucherRedemptionsEntity`, `VoucherRedemptionAllocationsEntity` already exist — verify columns vs baseline diff).
- [ ] New `promotions:*`/`vouchers:*` permissions → reference-data.sql rows + role grants (diff upstream `seed.sql` permissions block).
- [ ] Public `GET /api/promotions{,/{id}}` shape check against upstream models (fields may have grown).
- [ ] Tests: `PromotionsVouchersIT` — lifecycle transitions incl. illegal ones (exact error bodies), voucher revoke, redemption accounting on a booking.
- [ ] `./mvnw verify` green → commit `feat(promotions,vouchers): admin lifecycle, vouchers, public surface`.

### Task 9: Support conversations — staff side

Endpoints:
```
GET  /api/support/agents, /api/support/conversations, /api/support/conversations/{id}
POST /api/support/conversations/{id}/messages, /api/support/conversations/{id}/actions
```
(guest-side support endpoints landed in Task 4)

Upstream: `git diff b7bce0a8..origin/master -- src/modules/support/` (handlers, service, repository, hub.rs, models; idempotency tables `SupportActionIdempotencyKeys`/`SupportGuestRequestIdempotencyKeys` entities exist).

Steps:
- [ ] Port conversation list/detail with the same filters/pagination; agent list; staff message post; action endpoint (assign/resolve/reopen per upstream action enum) honoring the idempotency-key semantics.
- [ ] Permissions → reference-data.sql if new `support:*` rows exist upstream.
- [ ] Tests: `SupportIT` — create-via-guest → staff reply → action → reopen path; idempotency replay returns same result.
- [ ] `./mvnw verify` green → commit `feat(support): staff conversation surface`.

### Task 10: Auth hardening

Upstream sources: `git diff b7bce0a8..origin/master -- src/routes/auth.rs src/handlers/auth.rs src/services/auth.rs src/services/google_identity.rs src/routes/two_factor.rs src/routes/passkey.rs src/modules/consent/` plus commits `d24b577e` (lookup #135), `1c0b6aa3`+`86817bf8` (Turnstile), `7478e7b1`+`b11ab13d`+`0726f36a`+`49f53df1` (One Tap + consent notice + race), `0f87ae87`+`52fea640` (2FA enforcement + routing), `0f14c2d7`+`6c23756f`+`64a77489` (passkey re-auth + shared session write-through), `efac4599` (authenticator hotel name).

Steps:
- [ ] `POST /api/auth/login/lookup` — existence check before password (exact response shape + rate limiter from upstream).
- [ ] Turnstile: verify `cf-turnstile-response` (or upstream field name) on login/register behind `TURNSTILE_*` env vars; fail-open/closed semantics copied from upstream handler.
- [ ] Google One Tap: extend the Google endpoint to create accounts with consent-by-notice; fix the create-race (resolve instead of reject) per `49f53df1`; persist consent rows (`modules/consent` → `consent_records`).
- [ ] 2FA enrollment enforcement: on login, roles in `require_two_factor_roles` with no enrolled factor get the enrollment-pending response upstream returns (copy the exact status/body — likely a distinct code the FE routes on); honor `require_two_factor_grace_days`.
- [ ] Passkey registration now requires recent re-auth (upstream `reauthenticated_at`/session marker — port the check + the session minting through the shared write-through used elsewhere).
- [ ] `refresh_tokens.client_timezone` captured from the login request (upstream header/field name — grep the diff).
- [ ] Authenticator prompts carry `hotel_name` when issuer/RP settings are blank (Task 2 seeding made them blank).
- [ ] Tests: extend `AuthFlowIT` — lookup 200/404, Turnstile bypass-when-unset, 2FA-required response for seeded privileged role, client_timezone persisted.
- [ ] `./mvnw verify` green → commit `feat(auth): login lookup, turnstile, one-tap, 2fa enforcement, passkey re-auth`.

### Task 11: Remaining deltas sweep

For each, port the upstream diff and prove with a test:

- [ ] `POST /api/data-transfer/import` + full-import users/guests FK-cycle ordering fix (`37ab462c`, `c7343b29`) — `BE/src/{routes,handlers,services,repositories}/data_transfer.rs`.
- [ ] Payments: bank details required with general defaults (`ab946136`), legacy references editable + owner-named conflict (`668b3936`), PayPal SDK checkout + stale-attempt expiry (`b99f038f`, `e905c2b5`) — `BE/src/{routes,handlers,services,repositories}/payments.rs`, `services/paypal_client.rs`.
- [ ] `hotel_business_number` readable pre-auth via public settings surface (is_public) — verify the existing settings GET honors `is_public`; used by `/legal` pages.
- [ ] Any residual diffs: run `git diff b7bce0a8..origin/master --stat -- src/ | grep -v test` and tick off every file not covered by Tasks 3–10 (e.g. `rate_limiter.rs` zero-limit guard `aff1237b`, `routes/mod.rs` changes).
- [ ] `./mvnw verify` green → commit `feat: sweep remaining upstream deltas (data-transfer import, payments hardening, limiter guard)`.

### Task 12: Reconcile stale/renamed mappings

For each row in the stale-mappings table (top of this doc): grep the upstream route registration at `origin/master` to confirm the canonical `(METHOD, path)`, then fix the Spring mapping — and the handler if upstream also changed request/response shape (`git diff b7bce0a8..origin/master -- <handler file>`). Rows already owned by another task (guest-portal `/me`, passkey `register/start`, comms admin move, promotions admin move, data-transfer export/preview) are fixed inside that task — this task owns only the rest:

- [ ] `GET /api/rooms/{id}` → `/api/rooms/{id}/detailed`; `PUT` → `PATCH /api/rooms/{id}`; `PUT`→`PATCH` `/api/room-types/{id}` and `/api/rate-plans/{id}`.
- [ ] `GET /api/night-audit/runs{,/{id}}` → `/api/night-audit{,/{id}}`.
- [ ] Loyalty member surface: drop/rename `GET /api/loyalty/programs` and `POST /api/loyalty/rewards/redeem` to upstream's `/api/loyalty/{enroll,me,me/activity,rewards,rewards/{id}/redeem}` set — compare with what Spring already maps and unify.
- [ ] `DELETE /api/admin/loyalty/rewards/{id}` — verify absence in `modules/loyalty/routes.rs`, then remove.
- [ ] `POST /api/bookings/{id}/checkout` — find upstream's real checkout flow (likely payments/folio path); remove the phantom or repoint it.
- [ ] Re-run `tools/check_openapi_parity.py` — spring-only count must drop to just infra routes (`/health`, `/ws/status`, `/uploads/**` — those stay per the spec).
- [ ] `./mvnw verify` green → commit `fix(api): reconcile renamed endpoints to upstream route surface`.

### Task 13: Frontend re-sync (verbatim)

Steps:
- [ ] `rsync -a --delete --exclude node_modules --exclude dist "$FE/" frontend/` — then **restore** `frontend/src/features/bookings/components/QuickBookingModal.tsx` (port-local file; first grep upstream `hotel-web-fe` for an equivalent — if upstream now ships one, drop the local copy and fix `features/bookings/index.ts` accordingly).
- [ ] Top-level sweep: `guest.html`, i18n/legal/portal files, `bun.lock`, `package.json` (new fontsource deps), `vite.config.ts` (guestHtmlFallback plugin) all come along verbatim.
- [ ] `cd frontend && bun install && bun run typecheck && bun run build` — must pass; this is the FE gate.
- [ ] If `frontend/Dockerfile` or nginx conf exists, confirm it serves `guest.html` too (multi-page build) — mirror `deploy/` nginx changes from upstream.
- [ ] Commit `chore(frontend): re-sync verbatim hotel-web-fe @ <upstream sha>`.

### Task 14: Final parity + docs

- [ ] `python3 tools/check_parity.py --strict` → `0 missing of 361` and `python3 tools/check_openapi_parity.py` → `0 missing, 0 unexpected` (infra routes whitelisted).
- [ ] `./mvnw verify` fully green — no skipped tests.
- [ ] Update `README.md` (361 endpoints, new feature areas, parity notes for intentionally-stubbed ceremonies) and the design doc's deviation list if anything new is stubbed.
- [ ] Commit `docs: mirror complete to upstream @<sha> — 361 endpoints at parity`.
