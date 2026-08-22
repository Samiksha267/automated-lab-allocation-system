# Automated Constraint-Based Lab Allocation and Scheduling System

A constraint-based lab scheduling engine for a college's multi-program academic structure — not a lab-booking CRUD app. See [docs/01-PROJECT-OVERVIEW.md](docs/01-PROJECT-OVERVIEW.md) for the full problem statement and motivation.

## Current Status

**Phase 2 of 30 complete: Project Foundation.** Repository scaffolding, backend/frontend projects, Docker setup, and baseline testing are in place and verified. **No domain logic, authentication, or scheduling engine exists yet** — do not expect labs, allocations, or dashboards to work. See the phase-by-phase build plan referenced in project docs for what's next (Phase 3: Authentication + RBAC).

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
