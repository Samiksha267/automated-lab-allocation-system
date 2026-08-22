# Deployment Guide

**Status: Phase 3 (Authentication + RBAC) — local Docker Compose deployment, including authentication, verified; production deployment remains planned.**

## Local Docker Compose Deployment — verified

```
Browser
  ↓
frontend container (nginx:1.27-alpine serving the Vite production build, port 5173→80)
  ↓ HTTPS not yet — plain HTTP for local dev
backend container (eclipse-temurin:21-jre-alpine running the Spring Boot jar, port 8080)
  ↓
postgres container (postgres:16-alpine, port 5432, named volume `postgres-data`)
```

Commands actually run this phase:
```
cp .env.example .env
docker compose build
docker compose up
```

See [13-DEVELOPER-SETUP.md](13-DEVELOPER-SETUP.md) for the exact verified outcome of each command, including the one known limitation (Testcontainers/`docker-java` cannot reach this machine's Docker daemon, even though the `docker` CLI and `docker compose` — which is what actually matters for this section — work correctly).

## Container Responsibilities

| Service | Image basis | Responsibility |
|---|---|---|
| `postgres` | `postgres:16-alpine` | Single source of truth for all persisted data; named volume `postgres-data` survives container recreation |
| `backend` | multi-stage: `maven:3.9-eclipse-temurin-21` (build) → `eclipse-temurin:21-jre-alpine` (runtime), non-root user | Spring Boot API; runs Flyway migrations on startup; exposes `/actuator/health` |
| `frontend` | multi-stage: `node:20-alpine` (build) → `nginx:1.27-alpine` (runtime) | Serves the Vite production build as static files; nginx config includes SPA fallback (`try_files ... /index.html`) so React Router client-side routes don't 404 on refresh |

## Ports (local Compose)

| Service | Host port | Container port |
|---|---|---|
| postgres | 5432 | 5432 |
| backend | 8080 | 8080 |
| frontend | 5173 | 80 |

## Environment Variables

See `.env.example` at the repository root — copied to `.env` for local use (never committed). Compose passes `POSTGRES_*` to the `postgres` service, `DB_*`/`SERVER_PORT`/`SPRING_PROFILES_ACTIVE`/`CORS_ALLOWED_ORIGINS`/`JWT_SECRET`/`JWT_EXPIRATION_MINUTES`/`DEMO_*_PASSWORD` to `backend` (see docs/13-DEVELOPER-SETUP.md for what each auth variable does), and `VITE_API_BASE_URL` as a **build arg** to `frontend` (Vite bakes `VITE_*` variables into the static bundle at build time — it cannot be changed at container-run time without rebuilding the image, which is why it is a build arg, not a runtime environment variable, in `docker-compose.yml`). `JWT_SECRET` uses Compose's `${VAR:?error message}` syntax so `docker compose up` fails fast with a clear message if `.env` is missing entirely, rather than silently starting with an empty secret.

## Health Checks

- `postgres`: `pg_isready` (Compose `healthcheck`, 5s interval).
- `backend`: `wget -qO- http://localhost:8080/actuator/health` (Compose `healthcheck`, 10s interval, 30s start period to allow JVM startup + Flyway migration).
- `backend` service has `depends_on: postgres: condition: service_healthy` — Compose waits for Postgres's healthcheck to pass, not just for the container to start, before starting the backend (this is the specific gap the phase brief warned about: plain `depends_on` alone does not guarantee readiness).
- `frontend` depends on `backend`'s healthcheck similarly.

## Persistent Database Volume

`postgres-data` (Docker named volume) is mounted at `/var/lib/postgresql/data` — survives `docker compose down` (without `-v`); only removed with an explicit `docker compose down -v` or `docker volume rm`.

## Planned Production Deployment

Unchanged from the Phase 1 plan — still explicitly **planned, not implemented**:
- **Frontend:** the same static build/nginx image, deployed to whatever host is chosen.
- **Backend:** the same Docker image, deployed to whatever host/orchestrator is chosen (unresolved — see [18-FUTURE-IMPROVEMENTS.md](18-FUTURE-IMPROVEMENTS.md)).
- **PostgreSQL:** managed instance or containerized, with backups — specifics deferred.
- **CORS:** `CORS_ALLOWED_ORIGINS` must be set to the real production frontend origin, never `*`.
- **Migrations:** Flyway runs automatically on backend startup; a failed migration must fail the deployment loudly.
- **TLS:** local Compose is plain HTTP; production would need TLS termination (reverse proxy or platform-managed) - not designed yet.

## Verification Checklist

- [x] Backend compiles (`./mvnw package`) — **verified 2026-08-21**
- [x] Backend unit tests pass (`./mvnw test`) — **verified 2026-08-21**
- [ ] Backend Testcontainers integration test (`./mvnw verify`) — blocked by this machine's Docker/`docker-java` incompatibility (see [13-DEVELOPER-SETUP.md](13-DEVELOPER-SETUP.md)); expected to pass in a compatible environment (e.g. Linux CI, Phase 27)
- [x] Frontend tests pass (`npm test`) — **verified 2026-08-21**
- [x] Frontend production build succeeds (`npm run build`) — **verified 2026-08-21**
- [x] `docker compose build` — **verified 2026-08-21**: both `lab_allocation-backend` and `lab_allocation-frontend` images built successfully
- [x] `docker compose up -d` — **verified 2026-08-21**: all three containers reached `Healthy`/`Up`; `curl http://localhost:8080/actuator/health` returned `{"status":"UP",...,"components":{"db":{"status":"UP",...}}}`; `curl http://localhost:5173/` returned `200`; `flyway_schema_history` inside the running Postgres container confirmed migration `1 baseline` applied successfully; the frontend's built JS bundle was confirmed to have `http://localhost:8080/api` baked in from the `VITE_API_BASE_URL` build arg
- [x] Flyway `V2__create_app_user.sql` applies cleanly — **verified 2026-08-22**: `flyway_schema_history` shows version `2 create app user` succeeded, alongside `1 baseline`
- [x] Dockerized authentication end-to-end — **verified 2026-08-22**: login for all three seeded demo roles (LAB_ASSISTANT/CR/STUDENT) against the Dockerized backend, `GET /api/auth/me` returning the correct role for each; negative cases (no token, invalid token, wrong password, unknown email) all returned the correct `401` shape; CORS preflight for `http://localhost:5173` confirmed correctly scoped (not a wildcard)
- [ ] CI pipeline running all of the above automatically — Phase 27
