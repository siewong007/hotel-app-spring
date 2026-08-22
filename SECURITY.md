# Security Policy

## Supported Versions

Hotel app (Spring Boot) is an academic and portfolio-oriented project. Security fixes are currently considered for the active `main` branch.

| Version | Supported |
| --- | --- |
| `main` | Yes |
| Older commits or forks | No formal support |

## Reporting a Vulnerability

Please do not disclose suspected vulnerabilities publicly before they can be reviewed. If a private GitHub security advisory is available, use it. Otherwise, contact the repository maintainer through a private channel and include:

- A clear description of the issue.
- Steps to reproduce or proof-of-concept details.
- Affected component: API, frontend, database schema/seeding, or documentation.
- Potential impact and any known mitigations.
- Your contact information for follow-up.

## Security Scope

Relevant reports include, but are not limited to:

- Authentication or authorization bypass (JWT validation, session-bound checks, guest-portal token handling).
- RBAC permission errors, including `<resource>:manage` implication bugs.
- SQL injection or unsafe query construction.
- Exposure of secrets, tokens, logs, or uploaded identity documents.
- Unsafe file upload or path traversal behavior.
- Cross-site scripting or unsafe frontend rendering.

## Out of Scope

- Issues requiring unrealistic physical access without a practical exploit path.
- Denial-of-service reports without a clear security impact.
- Missing production hardening that is already documented as a limitation (in-memory rate limiting/caching, single-instance design).
- Reports against dependencies without a demonstrated impact on this project.

## Maintainer Response

The maintainer will make a best-effort review, confirm whether the issue is valid, and prioritize a fix based on severity and project scope. This project does not currently offer a bug bounty or guaranteed response time.

## Security Notes for Contributors

- Never commit real `.env` files, credentials, tokens, private keys, or local database files.
- Keep SQL parameterized; dynamic SET clauses may only use fixed column allow-lists.
- Use the existing security core (`JwtService`, `SessionBoundAuthFilter`, `RbacService`, `PermissionGate`) rather than re-implementing checks.
- Return generic client-facing errors via `core.error.ApiError` while logging useful internal details.
- JWT secrets must be at least 32 characters and are validated at startup; compose refuses to start with blank credentials.
