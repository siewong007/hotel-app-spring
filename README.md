# Hotel App — Spring Boot

A Spring Boot re-implementation of the `hotel-app` Rust backend, **feature-identical and interface-identical**: all 361 REST endpoints from the original API are reproduced with the same paths under `/api`, request/response JSON shapes, status codes, error envelope (`{"error": "..."}`), session-bound JWT auth, RBAC permission gates (including `<resource>:manage` implication), in-memory rate limits and security headers. The React administrative + guest frontend is bundled verbatim in [`frontend/`](frontend) (multi-page build: `index.html` staff app + `guest.html` guest portal/check-in).

See [`docs/superpowers/specs/2026-08-22-spring-boot-port-design.md`](docs/superpowers/specs/2026-08-22-spring-boot-port-design.md) for the design contract and [`docs/api-parity-inventory.txt`](docs/api-parity-inventory.txt) for the endpoint inventory (all routes verified by `tools/check_parity.py`).

## Quick start (Docker)

```bash
cp .env.example .env   # set POSTGRES_PASSWORD + JWT_SECRET (>=32 chars)
docker compose up -d
curl http://localhost:3030/health          # {"status":"ok"}
open http://localhost/                     # frontend via nginx -> api
```

Default seeded accounts (placeholder bcrypt passwords; set real ones with your own flow):
`admin` / `superadmin` — see `core/bootstrap/ReferenceDataSeeder`.

## Local development

```bash
source env.sh                 # pins JDK 21 (Homebrew openjdk@21)
./mvnw spring-boot:run        # API on :3030; reads DATABASE_URL + JWT_SECRET
cd frontend && bun install && bun run start   # Vite :3000 proxies /api to :3030
```

## Verification gates

```bash
. ./env.sh && ./mvnw verify        # unit + Testcontainers integration tests
python3 tools/check_parity.py --strict   # endpoint parity vs inventory (exit 1 on gaps)
```

## Layout

| Path | Purpose |
| --- | --- |
| `src/main/java/com/hotelapp/core/` | config, security (JWT/session/RBAC/rate-limits), error envelope, seeder |
| `src/main/java/com/hotelapp/<domain>/` | controllers/services per domain (auth, bookings, billing, admin, ops, insights, engagement, portal, collab, gaps) |
| `src/main/java/com/hotelapp/core/entity/` | JPA entities generated from the v1 baseline SQL (`tools/generate_entities.py`) |
| `src/main/resources/db/` | reference/sample seed SQL applied idempotently at startup |
| `frontend/` | verbatim React frontend (bun/vite) |
| `deploy docs` | `docker-compose.yml`, `Dockerfile.api`, `.env.example` |

## Parity notes & deviations

- Schema is Hibernate-generated from entities whose table/column names, types, defaults, unique constraints and generated columns mirror the original baseline SQL; data remains dump-compatible.
- Auth ceremonies are complete: login lookup, Turnstile, Google One-Tap, TOTP + recovery codes, 2FA enforcement, and full WebAuthn registration/login via CBOR attestation parsing + ES256 assertion verification.
- The booking lifecycle runs the upstream `update_booking` semantics on `PATCH/PUT /api/bookings/{id}`: status transitions (check-in, checkout, void), balance guard, deposit reconciliation against the payments ledger, company-ledger auto-post, invoice + receipt mail, loyalty award/reversal, housekeeping task and night-audit backfill.
- PayPal order create/capture respond behind the same `PAYPAL_ENABLED` flag and surface `503 ServiceUnavailable` when unconfigured; the CSP allow-lists `*.paypal.com`/`*.paypalobjects.com`/`*.venmo.com` like upstream.
- `/health` and `/ws/status` remain at the root (not under `/api`), matching the original router. WebSocket upgrade endpoints (`/api/guest-portal/me/availability`, `/api/guest-portal/me/support/socket`) are registered via Spring's WebSocket handlers rather than MVC mappings.

MIT License — see upstream repository.
