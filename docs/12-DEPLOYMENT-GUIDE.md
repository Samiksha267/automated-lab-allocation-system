# Deployment Guide

**Status: Phase 26 (Deployment) — containerized production-readiness formalized and verified.** This
extends, not replaces, the Docker Compose deployment verified since Phase 2 - the working images/Compose
stack were preserved as-is; Phase 26 added a production Spring profile, a JWT-secret startup guard, and this
document's full rewrite.

## Continuous Integration

Every push and pull request against `main` is verified automatically by GitHub Actions
(`.github/workflows/ci.yml`): backend compile → unit tests → Testcontainers-backed integration tests →
frontend install/lint/test/build → Docker image build + a `prod`-profile Compose smoke test. Full details:
docs/11-TESTING-STRATEGY.md's "Phase 27 — CI Verification" section. This section stays deployment-focused;
it does not duplicate the workflow's own step-by-step logic.

## Prerequisites

Only these are required on the deployment host:

- Docker
- Docker Compose (bundled with modern Docker Desktop/Docker Engine)
- Git (to obtain the repository)

**Java, Maven, Node, and npm are not required on the deployment host** - both the backend and frontend
Docker images build entirely from source inside their own multi-stage builds (a Maven/JDK stage and a
Node stage, each discarded after producing the final runtime image).

## Architecture

```text
Browser
   ↓  HTTP (plain, not TLS-terminated here — see "HTTPS" below)
frontend container (nginx:1.27-alpine serving the Vite production build, port 5173→80)
   ↓  cross-origin REST/JSON, browser → backend directly (no reverse proxy hop)
backend container (eclipse-temurin:21-jre-alpine running the Spring Boot jar, port 8080)
   ↓
postgres container (postgres:16-alpine, port 5432, named volume `postgres-data`)
```

**Why cross-origin rather than an nginx `/api` reverse proxy:** the frontend already talks to the backend
via a single, explicit, build-time-baked base URL (`VITE_API_BASE_URL` → `frontend/src/api/client.ts`), and
the backend already enforces a real, non-wildcard CORS allow-list (`CORS_ALLOWED_ORIGINS`) scoped to exactly
the frontend's origin. Introducing an nginx reverse proxy in front of the backend would add a second HTTP
hop and a second place to keep multipart/timeout limits in sync, for no correctness benefit this project's
actual architecture needs - PDF uploads go directly from the browser to the backend container and never
pass through nginx at all, so nginx's `client_max_body_size` is not a factor here (unlike a same-origin
reverse-proxy deployment, where it would need to be raised above the backend's 10MB limit).

## Container Responsibilities

| Service | Image basis | Responsibility |
|---|---|---|
| `postgres` | `postgres:16-alpine` | Single source of truth for all persisted data; named volume `postgres-data` survives container recreation |
| `backend` | multi-stage: `maven:3.9-eclipse-temurin-21` (build) → `eclipse-temurin:21-jre-alpine` (runtime), **non-root user** (`addgroup -S app && adduser -S app -G app`, `USER app`) | Spring Boot API; runs Flyway migrations on startup; exposes `/actuator/health` |
| `frontend` | multi-stage: `node:20-alpine` (build) → `nginx:1.27-alpine` (runtime) | Serves the Vite production build as static files; SPA fallback so client-side routes never 404 on refresh |

## Environment Variables

Copy `.env.example` to `.env` and set real values before the first `docker compose up`. **Never commit `.env`** (already in `.gitignore`; `.env.example` is intentionally tracked).

| Variable | Service | Purpose | Required |
|---|---|---|---|
| `POSTGRES_DB` | postgres | Database name | No (`lab_allocation` default) |
| `POSTGRES_USER` | postgres | Database role | No (`lab_user` default) |
| `POSTGRES_PASSWORD` | postgres, backend | Database password | **Yes for any real deployment** (`change_me` is an obvious placeholder) |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | backend | JDBC connection - `DB_HOST` must be the Compose service name (`postgres`), never `localhost`, from inside the backend container | Yes (Compose sets these for you) |
| `SERVER_PORT` | backend | HTTP port the Spring Boot app listens on | No (`8080` default) |
| `SPRING_PROFILES_ACTIVE` | backend | `dev` (demo seed data, verbose logging) or `prod` (no seed data, `ProductionJwtSecretGuard` active, quieter logging) | Yes - defaults to `dev` in this repo's Compose file, matching its role as a runnable college-project demo |
| `CORS_ALLOWED_ORIGINS` | backend | Exact frontend origin(s) allowed to call the API - never `*` | Yes for any deployment whose frontend origin differs from `http://localhost:5173` |
| `JWT_SECRET` | backend | HS256 signing key. Compose fails fast (`${JWT_SECRET:?...}`) if unset at all; **when `SPRING_PROFILES_ACTIVE=prod`, `ProductionJwtSecretGuard` additionally rejects the known dev placeholder and any secret under 32 bytes at application startup** | Yes |
| `JWT_EXPIRATION_MINUTES` | backend | Access token lifetime | No (`60` default) |
| `COLLEGE_TIME_ZONE` | backend | IANA zone bridging `LocalDate`/`LocalTime` to `Instant` (see `SchedulingTimeMapper`) | No (`Asia/Kolkata` default) |
| `DEMO_LAB_ASSISTANT_PASSWORD` / `DEMO_CR_PASSWORD` / `DEMO_STUDENT_PASSWORD` | backend | Passwords for the three seeded demo accounts - **consumed only when `SPRING_PROFILES_ACTIVE=dev`** (`DevUserSeeder` is `@Profile("dev")`); structurally inert under `prod` | No |
| `VITE_API_BASE_URL` | frontend (build arg, not runtime env) | Backend base URL the browser bundle calls | Yes - Vite bakes `VITE_*` values into the static bundle at **build time**; changing it requires rebuilding the frontend image, not just restarting the container |

**Never** put `JWT_SECRET`, database passwords, or any other real secret in a `VITE_*` variable - everything
prefixed `VITE_` ships to every browser that loads the page.

## Secrets Strategy

- `.env` is local-only, gitignored, never committed.
- `.env.example` is committed and contains only placeholder values with comments explaining each one.
- Compose passes values through as container environment variables - nothing is baked into the backend
  image at build time (the frontend's `VITE_API_BASE_URL` is the sole intentional exception, since Vite
  requires it at build time by design).
- `JWT_SECRET` has two independent layers of protection against an accidentally-weak production secret:
  1. `docker-compose.yml`'s `${JWT_SECRET:?JWT_SECRET must be set in .env - see .env.example}` - fails
     `docker compose up` immediately if the variable is unset at all.
  2. `ProductionJwtSecretGuard` (`@Profile("prod")`, backend) - fails Spring context startup if the
     variable *is* set but is still the documented dev placeholder or shorter than HS256's 32-byte
     minimum. This layer exists because layer 1 only protects the Docker Compose path; a `prod`-profile
     backend started outside Compose (e.g. `java -jar app.jar -Dspring.profiles.active=prod`) would
     otherwise silently fall back to `application.yml`'s dev-only default with no warning at all.
- Generate a real secret with: `openssl rand -base64 48`

## Production vs. Demo Mode

This repository ships **one** Compose file, defaulting `SPRING_PROFILES_ACTIVE=dev` - appropriate for its
actual purpose (a runnable college-project demonstration with known seeded accounts for Lab
Assistant/CR/Student). To run a production-*like* configuration instead (no demo seed data, the JWT secret
guard active, quieter logging):

```bash
# In .env:
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=<a real secret from `openssl rand -base64 48`>
POSTGRES_PASSWORD=<a real password>
CORS_ALLOWED_ORIGINS=<the real frontend origin, e.g. https://lab-allocation.your-college.example>
```

Under `prod`, `DevAcademicSeeder`/`DevLabSeeder`/`DevUserSeeder`/etc. (all `@Profile("dev")`) do not run at
all - the database starts genuinely empty (post-migration) with no demo accounts, no known passwords, and no
benchmark/test data. **Never deploy with `SPRING_PROFILES_ACTIVE=dev` on a network reachable by anyone other
than the people who should know the demo credentials** - `dev` mode's whole point is convenient, known-password
demonstration access, not access control.

**A real bug found and fixed this phase (Phase 27):** `docker-compose.yml`'s `backend.environment` block had
`SPRING_PROFILES_ACTIVE: dev` as a *literal* value, not `${SPRING_PROFILES_ACTIVE:-dev}` - so setting the
variable in `.env` as instructed above had silently had no effect at all; Compose never read it. Fixed to
interpolate from the environment (defaulting to `dev`, preserving the existing local/demo workflow
unchanged). Verified live: booted an isolated stack with `SPRING_PROFILES_ACTIVE=prod` and a real CI-style
JWT secret - `ProductionJwtSecretGuard` allowed startup, `/actuator/health` returned `UP`, and a login attempt
with the known demo credentials correctly returned `401` (no seed data exists under `prod`), confirming the
fix actually works end-to-end, not just that the YAML parses differently.

## Build

```bash
cp .env.example .env
# edit .env with real values, especially if not running in dev/demo mode
docker compose build
```

For a fully reproducible verification (ignoring layer cache):
```bash
docker compose build --no-cache
```

## First Startup

```bash
docker compose up -d
docker compose ps
```

Expected: `postgres` → `Up (healthy)`, `backend` → `Up (healthy)`, `frontend` → `Up`. On a genuinely fresh
database, `backend` takes longer to become healthy than usual - Flyway must apply all migrations (`V1`
through the latest) before the JVM finishes starting; the health check's `start_period: 30s` plus its retry
budget accommodates this.

## Health Checks

| Service | Mechanism | Notes |
|---|---|---|
| `postgres` | `pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}` | Real readiness check, not just "process exists" |
| `backend` | `wget -qO- http://localhost:8080/actuator/health` | `/actuator/health` reuses this project's real, existing health endpoint (no redundant endpoint added). Only `health`/`info` are exposed (`management.endpoints.web.exposure.include`); `show-details: when-authorized` (base config) means an unauthenticated caller never sees database/config internals, only `{"status":"UP"}` or `{"status":"DOWN"}` |
| `frontend` | (relies on `backend`'s health via `depends_on`; a plain `GET /` returning `200` is sufficient to confirm nginx itself is serving) | No dedicated Compose healthcheck block was added - nginx serving static files has no meaningful "unhealthy but running" state the way a JVM with a DB dependency does |

## Startup Order

`backend` has `depends_on: postgres: condition: service_healthy` - Compose waits for Postgres's own
healthcheck to pass, not merely for the container process to start, before starting the backend. `frontend`
has the equivalent dependency on `backend`. This closes the specific gap plain `depends_on` (with no
condition) leaves open: a backend starting before Postgres is actually accepting connections.

## Frontend/API Routing

The browser loads the React SPA from the `frontend` container, then calls the backend directly at
`VITE_API_BASE_URL` (baked in at frontend build time) - a genuine cross-origin request, protected by the
backend's `CORS_ALLOWED_ORIGINS` allow-list (never a wildcard when credentials/auth headers are involved).
**SPA direct-route refresh**: `frontend/nginx.conf`'s `try_files $uri $uri/ /index.html` means a direct
browser request to e.g. `/lab-assistant/analytics` or `/student/timetable` is served `index.html` (letting
React Router resolve the route client-side) rather than an nginx `404` - verified this phase (see below).

## CORS

`CORS_ALLOWED_ORIGINS` must be set to the real frontend origin(s), comma-separated if more than one, and
**never `*`** - this project's authentication uses bearer tokens sent via an `Authorization` header (not
cookies), so a wildcard origin is less immediately dangerous than it would be with credentialed cookie auth,
but is still not the deployed configuration this project uses or recommends; the allow-list stays explicit.

## PDF Upload Limits

Spring's own multipart limits (`spring.servlet.multipart.max-file-size: 10MB`,
`max-request-size: 12MB`, `application.yml`) are the only limits in this request path - no reverse proxy sits
between the browser and the backend for API calls (see Architecture above), so there is no second,
independently-configured body-size limit to keep in sync. Verified this phase: a real PDF upload through the
Dockerized backend succeeded well under this limit.

## Database Persistence

`postgres-data` (a Docker named volume) is mounted at `/var/lib/postgresql/data` - it survives
`docker compose down`, `docker compose stop`/`start`, and container/image recreation. It is destroyed **only**
by an explicit `docker compose down -v` or `docker volume rm lab_allocation_postgres-data`.

> **Never run `docker compose down -v` against any environment whose PostgreSQL volume contains data that
> must be preserved.** There is no undo.

## Restart / Shutdown

| Command | Effect |
|---|---|
| `docker compose stop` | Stops containers, keeps them (and the volume) in place; `docker compose start` resumes without rebuilding |
| `docker compose down` | Removes containers and the default network; **volume is preserved** - data survives |
| `docker compose down -v` | Removes containers, network, **and the named volume - all database data is destroyed** |

Restarting an existing deployment (`docker compose down` then `docker compose up -d`, or just
`docker compose restart`) was verified this phase to preserve all data: Flyway correctly recognizes
already-applied migrations via `flyway_schema_history` and does not re-run or fail on them, and previously
created accounts/allocations/audit history remain queryable after restart.

## Logs

```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres
```

## Upgrade Procedure

```bash
git pull
docker compose build
pg_dump -h localhost -U "$POSTGRES_USER" "$POSTGRES_DB" > backup-$(date +%Y%m%d).sql   # see Backup below
docker compose up -d --build
docker compose ps   # confirm all services return to Up/healthy
```

Flyway applies any new migration versions automatically against the existing database on backend startup -
no manual migration step is required. **This is not a zero-downtime upgrade**: the backend container is
replaced, so there is a brief window (bounded by the health-check start period) where API requests fail
while the new container starts and Flyway/Hibernate validate the schema.

## Rollback

- **Application image rollback** is straightforward: `git checkout <previous-tag>` (or point Compose at a
  previously-built image tag), `docker compose up -d --build`.
- **Database migration rollback is not automatically supported.** Flyway, as used in this project, only
  ever moves a schema *forward* (no down-migrations exist for any of this project's migrations, matching
  its established convention). Reverting a schema change means either writing and applying a new,
  forward-only migration that undoes the effect, or restoring from a pre-migration backup. Do not attempt
  to "undo" a migration by editing or deleting its row from `flyway_schema_history` - this desynchronizes
  Flyway's own bookkeeping from the actual database state.

## Backup / Restore

Basic guidance only - this project does not implement automated backups (Phase 26's own deadline-aware scope
explicitly defers this).

**Backup:**
```bash
docker compose exec postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > backup.sql
```

**Restore** (into a running, empty database):
```bash
cat backup.sql | docker compose exec -T postgres psql -U "$POSTGRES_USER" "$POSTGRES_DB"
```

Store backups somewhere other than the same host's disk for anything that matters, and protect them with the
same care as the live database - a backup file contains the same sensitive data (password hashes, real
schedule data) as the database it was taken from.

## Security Notes

- Change `POSTGRES_PASSWORD` and generate a real `JWT_SECRET` before any deployment reachable by anyone
  beyond a local demo audience.
- Do not commit `.env`; only `.env.example` (placeholders) belongs in git.
- `SPRING_PROFILES_ACTIVE=dev`'s seeded demo accounts (`lab.assistant@example.edu` / `cr@example.edu` /
  `student@example.edu`, with the passwords in `.env.example`) are for local/demo use only - use `prod`
  profile for anything else (see "Production vs. Demo Mode" above).
- `postgres`'s port (`5432`) is published to the host in this repository's Compose file for local
  development convenience (`psql`/GUI client access during a demo or debugging session) - a genuine
  production deployment on a shared or internet-reachable host should remove this port mapping (or bind it
  to `127.0.0.1` only) so PostgreSQL is reachable only from the Docker network, not the public host interface.
- **HTTPS is not implemented in this repository.** Both containers serve plain HTTP. Any deployment
  reachable over the public internet must terminate TLS in front of this stack - a reverse proxy (nginx,
  Caddy, Traefik) or a cloud load balancer/managed certificate service - this project does not do so itself
  and does not claim to.
- Protect backup files with the same access control as the production database itself.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `postgres` never becomes healthy | Wrong/missing `POSTGRES_*` values, or a stale volume from a previous, incompatible config | Check `docker compose logs postgres`; if truly stuck and the data is disposable, `docker compose down -v` and retry (destroys data - see warning above) |
| Backend cannot connect to DB / crashes on startup with a connection error | `DB_HOST` isn't `postgres` (e.g. left as `localhost`), or Postgres isn't actually healthy yet | Confirm `DB_HOST=postgres` in the backend's environment; confirm `depends_on: postgres: condition: service_healthy` is intact in `docker-compose.yml` |
| Backend fails to start with `JWT_SECRET is not set` / `still the development placeholder` / `shorter than the minimum 32 bytes` | `SPRING_PROFILES_ACTIVE=prod` with a missing, default, or too-short `JWT_SECRET` | This is `ProductionJwtSecretGuard` working as designed - set a real secret (`openssl rand -base64 48`) |
| Frontend loads but every API call fails / browser console shows a CORS error | `CORS_ALLOWED_ORIGINS` doesn't match the frontend's actual origin | Set it to the exact scheme+host+port the browser loads the frontend from |
| Frontend can't reach the backend at all (network error, not CORS) | `VITE_API_BASE_URL` was baked in at build time pointing somewhere unreachable from the browser | Rebuild the frontend image with the correct `VITE_API_BASE_URL` build arg - a running-container env var change has no effect |
| PDF upload returns `413`/rejected | File exceeds the backend's `10MB` limit, or (only in a *different*, reverse-proxied deployment topology than this repository's default) a proxy in front of it has a smaller body-size limit | Confirm the file is under 10MB; if a reverse proxy was added in front of this stack, raise its body-size limit to match |
| Flyway migration failure at startup | A migration doesn't apply cleanly against the current schema state (e.g. a partially-applied prior attempt, or an incompatible manual schema change) | **The backend correctly refuses to start** rather than run against an unknown schema - this is intentional, not a bug to route around. Inspect `flyway_schema_history` and the actual failing migration; do not set `baseline-on-migrate`, `repair`, or delete history rows to force past it without understanding why it failed |
| `port is already allocated` on `docker compose up` | Another process (or a previous, still-running Compose stack) already holds `5432`/`8080`/`5173` on the host | Stop the conflicting process, or change the host-side port mapping in `docker-compose.yml` |

## Known Limitations

- **TLS/HTTPS is terminated externally, if at all** - not implemented in this repository's containers.
- **No automated backups** - `pg_dump`/`psql restore` documented above are manual.
- **Single-instance deployment** - one backend container, one Postgres instance; no clustering, replication, or horizontal scaling is configured (this project has never needed it at its actual scale).
- **No zero-downtime upgrade** - upgrading recreates the backend container, with a brief unavailability window bounded by its health-check start period.
- **No CI/CD pipeline yet** - Phase 27's scope, not Phase 26's.
- **Database extension requirement** (unchanged from prior phases): the `V11__enforce_allocation_concurrency.sql` migration requires `CREATE EXTENSION btree_gist`, which needs superuser or an explicit grant on a managed/cloud PostgreSQL instance where the application role isn't a superuser - see the note preserved below.

### Database Extension Requirement (Phase 16, preserved from the prior version of this document)

Since `V11__enforce_allocation_concurrency.sql`, the deployment PostgreSQL instance must support
`CREATE EXTENSION btree_gist` (used by three `EXCLUDE` constraints on `allocation` enforcing lab/faculty/batch
overlap protection at the database level - docs/04-DATABASE-DESIGN.md §7a, docs/03-SYSTEM-ARCHITECTURE.md
§24).

- **PostgreSQL version:** supported since PostgreSQL 9.x - no concern for `postgres:16-alpine` or any
  currently-supported version.
- **Privilege:** `CREATE EXTENSION` requires superuser or an explicit grant (`btree_gist` is not marked
  `trusted` in vanilla PostgreSQL). This repository's Compose-provisioned `POSTGRES_USER` is automatically a
  superuser (the official `postgres` image's own init behavior). **A managed/production PostgreSQL instance
  where the application's role is not a superuser will need `btree_gist` installed by a database
  administrator with sufficient privilege before migrations can run.**
- **Migration behavior:** `CREATE EXTENSION IF NOT EXISTS btree_gist` is idempotent; `V11` itself runs at
  most once per database (Flyway's own versioned-migration guarantee).

## Phase 26 Verification Record (2026-08-26)

Performed against an **isolated, disposable test stack** (a separate Compose project name/volume), not the
long-lived development stack whose PostgreSQL volume carries real historical multi-phase verification data -
deliberately, to avoid destroying that data with `down -v` while still genuinely testing a from-scratch
deployment.

- [x] `docker compose config` — parses cleanly, no errors
- [x] `docker build ./backend` — succeeds
- [x] `docker build ./frontend` — succeeds
- [x] Clean deployment (`down -v` → `build --no-cache` → `up -d`, isolated test stack): `postgres` → healthy;
  `backend` → healthy, Flyway applied all migrations (`V1` through the latest) against a genuinely empty
  schema, `GET /actuator/health` → `{"status":"UP"}`; `frontend` → serving, `GET /` → `200`
- [x] Login through the deployed stack succeeds for all three demo roles
- [x] Lab Assistant smoke test (dashboard, analytics, audit logs) — real data returned
- [x] CR smoke test (My Class, My Timetable, Available Labs) — real data returned
- [x] Student smoke test (timetable filters, lab location) — real data returned
- [x] Direct browser request to `/lab-assistant/analytics`, `/cr/my-extra-labs`, `/student/timetable` (no
  prior page load) — served `index.html` (`200`), not an nginx `404`
- [x] Real PDF upload through the deployed backend — succeeded
- [x] Restart without `-v` (existing-volume path, main development stack) — all data (accounts, allocations,
  audit history) survived; Flyway recognized already-applied migrations and did not re-run them
- [x] `ProductionJwtSecretGuard` — rejects a missing/default/short `JWT_SECRET` under the `prod` profile;
  accepts a real one
- [x] Backend test suite — 300 tests, 0 failures, 0 errors (296 Phase-24 baseline + 4 new
  `ProductionJwtSecretGuardTest` cases)
