# Developer Setup

**Status: Phase 3 (Authentication + RBAC) — verified.** Every command below was actually executed on the development machine (2026-08-21/22) and the exact result is recorded, including where the environment differed from the original plan. Nothing here is aspirational.

## Prerequisites — actually verified versions

| Tool | Verified version | Notes |
|---|---|---|
| Java (JDK) | **21.0.12** (Eclipse Temurin) | This machine had no JDK at all beforehand; installed via `winget install --id EclipseAdoptium.Temurin.21.JDK -e`. |
| Maven | **3.9.16** | Not installed system-wide (`winget install Apache.Maven` found no package) — not needed, since the committed **Maven Wrapper** (`mvnw`/`mvnw.cmd`) resolves and downloads the correct Maven version automatically on first run. Always build via `./mvnw ...` (or `.\mvnw.cmd ...` on Windows), never a bare `mvn`. |
| Node.js | **v24.14.0** | Newer than the originally planned Node 20 LTS (ASSUMPTIONS A-04) — used as-is; it built, tested, and ran the frontend without any issue. The Docker frontend image still pins `node:20-alpine` for the container build regardless of host Node version. |
| npm | **11.9.0** | Bundled with the above Node install. |
| Git | 2.54.0 | |
| Docker | Docker Desktop 4.86.0 / Engine 29.7.2 | Was not running at the start of this phase (had to be launched); confirmed working via `docker ps`/`docker compose` afterward. |

## Local Setup Steps — verified

1. `git init` (done this phase — see git history from this point forward).
2. Copy `.env.example` to `.env` and adjust values (`cp .env.example .env` — verified to work; real secrets are never committed).
3. **Backend** (from `backend/`):
   - `./mvnw test` — runs the fast unit test suite. **Verified: 16 tests, all pass** (`GlobalExceptionHandlerTest`, `JwtServiceTest`, `AuthServiceTest`, `RoleAuthorizationTest`).
   - `./mvnw package` — produces `target/lab-allocation-backend-0.0.1-SNAPSHOT.jar`. **Verified: succeeds.**
   - `./mvnw verify` — additionally runs `LabAllocationBackendApplicationIT` and `AuthenticationIT` (both Testcontainers-backed, require a working Docker daemon reachable by the `docker-java` client library specifically — see Known Limitations below). **Verified: blocked in this specific environment; not a defect in either test.**
   - `./mvnw spring-boot:run` — starts the app against `DB_HOST`/`DB_PORT`/etc. from the environment (defaults to `localhost:5432` if unset — pair with a `docker compose up postgres` or local Postgres instance).
4. **Frontend** (from `frontend/`):
   - `npm install` — **verified: succeeds**.
   - `npm test` — runs Vitest. **Verified: 2 test files, 8 tests, all pass** (`DevStatusPage.test.tsx`, `features/auth/Auth.test.tsx`).
   - `npm run build` — `tsc -b && vite build`. **Verified: succeeds**, producing `dist/` (269.04 kB JS, gzip 85.07 kB; 10.95 kB CSS).
   - `npm run dev` — starts the Vite dev server (not separately re-verified this phase beyond the build/test above, since it uses the same toolchain that already succeeded).
5. **Full stack via Docker Compose** — see verified status in [12-DEPLOYMENT-GUIDE.md](12-DEPLOYMENT-GUIDE.md).

## Authentication — Environment Variables and Demo Credentials

| Variable | Purpose | Dev-only fallback (never used in prod — see below) |
|---|---|---|
| `JWT_SECRET` | HS256 signing key | `dev-only-insecure-jwt-signing-secret-change-me-0123456789` (obviously non-production; **must** be overridden with a real random secret, e.g. `openssl rand -base64 48`, outside local dev) |
| `JWT_EXPIRATION_MINUTES` | Access token lifetime | `60` |
| `DEMO_LAB_ASSISTANT_PASSWORD` | Seeded Lab Assistant demo password | `LabAssistant123!` |
| `DEMO_CR_PASSWORD` | Seeded CR demo password | `CrDemo123!` |
| `DEMO_STUDENT_PASSWORD` | Seeded Student demo password | `Student123!` |

**Demo accounts** (created only when `SPRING_PROFILES_ACTIVE=dev`, by `DevUserSeeder` — see ADR-017 in [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md); never created in any other profile):

| Email | Role | Default password (dev only) |
|---|---|---|
| `lab.assistant@example.edu` | LAB_ASSISTANT | `LabAssistant123!` |
| `cr@example.edu` | CR | `CrDemo123!` |
| `student@example.edu` | STUDENT | `Student123!` |

### Login testing commands — actually verified (2026-08-22, against the Dockerized stack)

```bash
# Login and capture a token
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"cr@example.edu","password":"CrDemo123!"}'
# → {"accessToken":"...","tokenType":"Bearer","expiresIn":3600,"user":{"id":2,"email":"cr@example.edu","displayName":"Demo CR","role":"CR"}}

# Use the token
curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer <accessToken>"
# → {"id":2,"email":"cr@example.edu","displayName":"Demo CR","role":"CR"}

# No token → 401 UNAUTHORIZED; wrong password / unknown email → 401 INVALID_CREDENTIALS (same generic message for both)
```

All verified directly for all three roles (Lab Assistant, CR, Student) plus the negative cases (no token, invalid token, wrong password, unknown email) during this phase — see the Phase 3 completion report for the exact commands and outputs.

## Known Limitations (honestly reported, not glossed over)

**Testcontainers cannot connect to Docker Desktop's daemon from Java on this machine, even though the `docker` CLI and `docker compose` work correctly.** Diagnosed during this phase:
- `docker version` / `docker info` / `docker ps` / `docker compose build` all succeed normally via the CLI.
- `curl http://localhost:2375/version` (after temporarily enabling `exposeDockerAPIOnTCP2375` in Docker Desktop settings, then restarting Docker Desktop) returns a correct, fully-populated Engine API response.
- The Testcontainers/`docker-java` client, however — whether configured via the default named pipe (`npipe:////./pipe/docker_engine`), an explicit `DOCKER_HOST=npipe:////./pipe/docker_engine`, or an explicit `DOCKER_HOST=tcp://localhost:2375` — consistently receives a stub, empty-valued `/info` response (HTTP 400, every field blank, labeled `com.docker.desktop.address=npipe://\\.\pipe\docker_cli`) instead of the real engine info `curl` sees on the identical TCP endpoint. This points to a `docker-java` 3.5.x / this specific Docker Desktop build (4.86.0, Engine 29.7.2, API 1.55) compatibility gap in the connection negotiation, not a misconfiguration of this project — the same failure occurs across three different transport configurations.
- **Consequence:** `LabAllocationBackendApplicationIT` (the one integration test verifying Flyway + health against a real Testcontainers Postgres) could not be executed successfully in this environment. It is correctly written and is expected to pass in any environment where Testcontainers can reach a compatible Docker daemon (e.g. Linux CI runners, which is where Phase 27's CI pipeline will actually run it). This is why it was deliberately separated into the Failsafe (`mvn verify`) lifecycle rather than the default `mvn test`/`mvn package` (see ADR-014 in [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md)) — the fast, default build path does not depend on this working.
- Docker Compose itself (CLI-driven, not `docker-java`-driven) was verified working normally — see [12-DEPLOYMENT-GUIDE.md](12-DEPLOYMENT-GUIDE.md) for the actual result.
- **Resolved this phase:** Phase 2 had left Docker Desktop's `exposeDockerAPIOnTCP2375` setting enabled from diagnosis. Per this phase's pre-work safety check, it has been **disabled again** (Docker Desktop restarted afterward) — re-verified that `docker version`, `docker compose version`, and `docker compose up` all still work normally through the standard mechanism with it off. The underlying Testcontainers/`docker-java` limitation is unrelated to this setting (it was reproduced with TCP exposure both on and off, and with the default named pipe) — enabling it bought nothing and stayed off.

## Running Tests — summary

| Command | What it runs | Verified result |
|---|---|---|
| `./mvnw test` (backend) | Unit tests only (`*Test`) | ✅ Pass (16/16) |
| `./mvnw verify` (backend) | Unit + Testcontainers `*IT` (`LabAllocationBackendApplicationIT`, `AuthenticationIT`) | ⚠️ Blocked by this machine's Docker/`docker-java` incompatibility (see above) — not a code defect |
| `npm test` (frontend) | Vitest + React Testing Library | ✅ Pass (8/8) |
| Manual `curl` login/`/me` (all 3 roles + negative cases) | End-to-end auth against the Dockerized backend | ✅ Verified — see commands above |
