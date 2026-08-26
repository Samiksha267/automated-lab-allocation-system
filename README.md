# Automated Constraint-Based Lab Allocation and Scheduling System

[![CI](https://github.com/Samiksha267/automated-lab-allocation-system/actions/workflows/ci.yml/badge.svg)](https://github.com/Samiksha267/automated-lab-allocation-system/actions/workflows/ci.yml)

A constraint-based lab scheduling engine for a college's multi-program academic structure — not a lab-booking CRUD app. See [docs/01-PROJECT-OVERVIEW.md](docs/01-PROJECT-OVERVIEW.md) for the full problem statement and motivation.

## Current Status

**Phase 27 of 30 complete: CI/CD.** Authentication/RBAC, the full scheduling/constraint engine, extra-lab booking with concurrency protection, audit logging, timetable versioning, PDF import, Lab Assistant/CR/Student frontends, analytics, a production-ready Docker Compose deployment, and a GitHub Actions pipeline verifying every push/PR are all implemented — see [docs/12-DEPLOYMENT-GUIDE.md](docs/12-DEPLOYMENT-GUIDE.md) for how to run it and [docs/11-TESTING-STRATEGY.md](docs/11-TESTING-STRATEGY.md) for how CI verifies it.

## CI

Every push and pull request against `main` runs [`.github/workflows/ci.yml`](.github/workflows/ci.yml): backend compile → unit tests → Testcontainers-backed integration tests, frontend `npm ci` → lint → test → build (in parallel with the backend), then a Docker job that builds both production images and boots the real Compose stack (`prod` profile) as a smoke test. See docs/11-TESTING-STRATEGY.md's "Phase 27 — CI Verification" section for the full breakdown.

**Recommended branch protection** (not configured through code — a repository setting, applied by a maintainer with admin access): require a pull request before merging to `main`, and require the `Backend`, `Frontend`, and `Docker` CI checks to pass before merge is allowed.

## Architecture

```
React (Vite + TypeScript)
      ↓ REST/JSON
Spring Boot API (Java 21)
      ↓
PostgreSQL (Flyway-migrated)
```

Modular monolith — no microservices, no Node.js runtime hop between the frontend and backend. Full rationale in [docs/03-SYSTEM-ARCHITECTURE.md](docs/03-SYSTEM-ARCHITECTURE.md) and [docs/15-DESIGN-DECISIONS.md](docs/15-DESIGN-DECISIONS.md).

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, React Router, TanStack Query |
| Backend | Java 21, Spring Boot 4.1.1, Spring Data JPA, Bean Validation |
| Database | PostgreSQL 16, Flyway |
| Testing | JUnit 5, AssertJ, Testcontainers (backend); Vitest, React Testing Library (frontend) |
| DevOps | Docker, Docker Compose |

## Repository Structure

```
Lab_allocation/
├── backend/            Spring Boot API (Maven, Java 21)
├── frontend/            React + TypeScript SPA (Vite)
├── docs/                 Full project documentation (architecture, requirements, constraints, ADRs, ...)
├── docker-compose.yml
├── .env.example
└── README.md
```

## Getting Started

### Prerequisites

- Java 21 (Eclipse Temurin recommended)
- Node.js 20+ (developed against v24; Docker's frontend build stage pins Node 20)
- Docker + Docker Compose
- PostgreSQL 16 (via Docker, or native)

Full verified setup steps: [docs/13-DEVELOPER-SETUP.md](docs/13-DEVELOPER-SETUP.md).

### Local Development (without Docker)

```bash
cp .env.example .env

# Backend (from backend/)
./mvnw spring-boot:run

# Frontend (from frontend/)
npm install
npm run dev
```

### Docker Development

```bash
cp .env.example .env
docker compose build
docker compose up
```

Full container responsibilities, ports, and health-check behavior: [docs/12-DEPLOYMENT-GUIDE.md](docs/12-DEPLOYMENT-GUIDE.md).

## Testing

```bash
# Backend unit tests
cd backend && ./mvnw test

# Backend + Testcontainers integration tests (requires a working Docker daemon)
cd backend && ./mvnw verify

# Frontend tests
cd frontend && npm test
```

See [docs/11-TESTING-STRATEGY.md](docs/11-TESTING-STRATEGY.md) for the full testing strategy and [docs/13-DEVELOPER-SETUP.md](docs/13-DEVELOPER-SETUP.md) for known environment limitations affecting Testcontainers on some machines.

## Documentation

Full documentation lives in [docs/](docs/), including requirements, database design, the scheduling engine design, hard constraints, RBAC, API contracts, ADRs, and more. Start with [docs/01-PROJECT-OVERVIEW.md](docs/01-PROJECT-OVERVIEW.md).

Presenting this project? [docs/17-DEMO-SCENARIOS.md](docs/17-DEMO-SCENARIOS.md) has ready-to-run 3/10/15-minute scripts and 12 live-verified proof scenarios.
