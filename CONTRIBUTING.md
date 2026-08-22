# Contributing to Hotel App (Spring Boot)

Thank you for your interest in contributing to the Spring Boot port of the Hotel Management System! This document provides guidelines and instructions for contributing.

## Table of Contents
1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Workflow](#development-workflow)
4. [Project Structure](#project-structure)
5. [Coding Standards](#coding-standards)
6. [Testing Guidelines](#testing-guidelines)
7. [Pull Request Process](#pull-request-process)
8. [Commit Conventions](#commit-conventions)

## Code of Conduct

By participating in this project, you agree to abide by the [Code of Conduct](CODE_OF_CONDUCT.md). Please report unacceptable behavior.

## Getting Started

### Initial Setup

See [README.md](README.md) for installation and environment configuration. The stack is Java 21, Spring Boot 3.5.x, Maven, PostgreSQL 17, and bun for the bundled frontend.

### Development Commands

```bash
source env.sh                       # pin JDK 21 (Homebrew openjdk@21)
./mvnw spring-boot:run              # backend API on :3030

cd frontend && bun install && bun run start   # frontend on :3000 (proxies /api)

. ./env.sh && ./mvnw verify         # unit + Testcontainers integration tests
python3 tools/check_parity.py --strict   # endpoint parity vs inventory (exit 1 on gaps)

docker compose up -d                # full stack: postgres + api + nginx frontend
```

## Development Workflow

### Branch Strategy

- `main` — stable code
- `feature/<name>` — new features
- `fix/<name>` — bug fixes
- `refactor/<name>` — code restructuring
- `docs/<name>` — documentation changes

### Parity Rule (most important)

This project is a **port** of the Rust/Axum backend at the upstream `hotel-app` repository. The behavioral source of truth for any handler is the corresponding Rust route/service file. When changing an endpoint:

1. Check [`docs/api-parity-inventory.txt`](docs/api-parity-inventory.txt) — every line must keep a Spring mapping.
2. Keep paths under `/api`, response JSON field names, status codes, and the error envelope (`{"error": "..."}`) identical to the Rust implementation.
3. Run `tools/check_parity.py --strict` before opening a PR.

## Project Structure

```
hotel-app-spring/
├── src/main/java/com/hotelapp/
│   ├── core/            # config, security (JWT/RBAC/rate limits), errors, seeder, entities
│   ├── auth/            # login/refresh/logout/access snapshot + 2FA/TOTP
│   ├── bookings/        # booking lifecycle, complimentary, credits
│   ├── billing/         # payments, invoices, ledgers
│   ├── rooms|rates|     # inventory, pricing, channels
│   ├── guests|companies/
│   ├── operations/      # housekeeping + maintenance
│   ├── admin/           # users, RBAC administration
│   ├── ops/             # settings, audit logs, night audit, data transfer
│   ├── insights/        # analytics, reports, search
│   ├── engagement/      # loyalty, promotions, communications
│   ├── portal/          # guest portal + eKYC
│   ├── collab/          # teams, support, webhooks, passkeys
│   └── gaps/            # remaining inventory endpoints
├── src/main/resources/db/   # idempotent reference/sample seed SQL
├── tools/                   # schema generator, seed extractor, parity checker
└── frontend/                # verbatim React frontend (bun/vite)
```

## Coding Standards

- Java 21, no Lombok; plain records/getters.
- Controllers stay thin: gate (`PermissionGate`/`PermissionGateHelper`) → service/JDBC work → DTO/map response.
- All SQL parameterized with `?` placeholders; column names in dynamic SET clauses must come from fixed allow-lists.
- Money is `BigDecimal` over `NUMERIC`.
- Business dates come from DB session time zone (`system_settings.timezone`), never JVM-local math.
- Errors are thrown as `core.error.ApiError`; never hand-write JSON error bodies.
- No comments unless a rule demands explanation.

## Testing Guidelines

- Unit tests live next to the class under test (`src/test/java/...`).
- Integration tests are named `*IT.java`, run in the failsafe phase, and use Testcontainers PostgreSQL (see `AuthFlowIT`, `RbacServiceIT`, `SeederIT`).
- New endpoints need at least one integration assertion on status code and response shape.
- Gate: `. ./env.sh && ./mvnw verify` must be green before claiming done.

## Pull Request Process

1. Fork/branch from `main`.
2. Ensure `./mvnw verify`, `python3 tools/check_parity.py --strict`, and (for frontend changes) `bun run typecheck && bun run lint && bun run test` all pass.
3. Update documentation if behavior or endpoints change.
4. Open a PR describing the change and which Rust source files define the ported behavior.

## Commit Conventions

Conventional Commits style:

```text
feat(auth): add recovery-code login path
fix(billing): deposit refund rounding
docs: expand parity notes
chore(deps): bump testcontainers
```
