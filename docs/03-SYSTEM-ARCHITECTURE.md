# System Architecture

## 1. Architecture Overview

Modular monolith. One Spring Boot application, one PostgreSQL database, one React SPA. No Node.js server sits between the browser and the API — Node/Vite is a build-time tool only. No microservices; the scheduling engine is a set of packages inside the single backend deployable, not a separate service.

```mermaid
flowchart TD
    User(["User: Lab Assistant / CR / Student"]) --> React["React SPA (Vite + TS + Tailwind)"]
    React -->|"HTTPS + JWT"| API["Spring Boot REST API"]
    API --> AppSvc["Application Services\n(auth, academic, lab, subject, allocation, importer, audit)"]
    AppSvc --> SchedEngine["Scheduling Engine"]
    SchedEngine --> ConstraintEngine["Constraint Engine\n(HC-01..HC-12)"]
    SchedEngine --> ScoringEngine["Scoring / Optimization Engine"]
    SchedEngine --> ConflictEngine["Conflict Resolution Engine"]
    AppSvc --> DB[(PostgreSQL)]
    SchedEngine --> DB
```

### Why React
Component-based UI maps naturally onto three structurally different dashboards (Lab Assistant / CR / Student) that share primitives (timetable grid, lab card) but differ in permitted actions. TypeScript gives compile-time safety across the many DTO shapes coming from a strongly-typed backend. Mature ecosystem (TanStack Query for server state, React Hook Form + Zod for validated forms) avoids reinventing data-fetching/caching.

### Why Spring Boot / Java
The core value of this project is a typed, testable, transactional domain — constraint validation, scoring, backtracking search — not template rendering. Spring gives us: a strong static type system for the domain model, first-class transaction management (critical for FCFS + concurrency correctness), Spring Security for layered RBAC, and a mature testing stack (JUnit 5, Mockito, Spring Boot Test, Testcontainers) that lets the scheduling engine be tested in isolation from the web/persistence layers.

### Why PostgreSQL
The domain is inherently relational: labs, faculty, batches, divisions, and allocations reference each other with strict integrity requirements (a session must reference a real lab, a real faculty, a real batch). PostgreSQL gives us foreign key integrity, ACID transactions, configurable isolation levels, and either unique constraints or advisory/row locks — all of which the concurrency-safe booking design (Phase 16) depends on directly, not incidentally.

### Why a modular monolith (not microservices)
At this scale (one college, ~15 labs, a few hundred allocations/week) a distributed system buys nothing but operational complexity: no independent scaling need, no independent deployment need, no team boundary that maps to service boundaries. A modular monolith gives the same internal separation of concerns (via Java package boundaries: `constraint`, `scoring`, `conflict`, `scheduler` are independent, testable modules) with one build, one deployment, one transaction boundary — which the FCFS/concurrency requirement actually needs (a single DB transaction spanning constraint check + insert is far simpler within one process than across service calls). See [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md) for the full ADR.

### Why not MongoDB
The domain is uniformly relational — every scheduling decision depends on joining labs, faculty, batches, divisions, subjects, and time windows with strict integrity requirements (a session must reference a *real* lab that *really* has the required software). A document store would either duplicate that relational data across documents (denormalization that itself becomes an integrity risk — two copies of "does this lab have Cloudera" can drift) or push every cross-reference check into application code. More fundamentally, the core correctness requirement — no two concurrent requests can double-book the same lab/time — depends on transactional/locking guarantees that a relational database provides natively (see ADR-003, ADR-010) and that a document store would require reinventing at the application layer, reintroducing exactly the check-then-insert race condition this project is designed to eliminate (PART 34/64). There is no offsetting benefit here: the data has no genuinely schemaless part (even PDF-import raw extraction is stored as ordinary nullable columns — ADR-007 — not because Postgres can't hold semi-structured data, but because keeping it in the same relational store keeps import correction trivially joinable against the same lab/faculty/subject tables).

### Why not React → Node → Java
Inserting a Node.js layer between the React frontend and the Spring Boot backend would mean: a second server runtime to deploy, monitor, and secure; a duplicated slice of API/domain logic (either Node re-validates what Spring Boot already validates, or it becomes a dumb proxy that adds latency and a new failure point for zero behavior); and a second place authentication/authorization would need to be enforced correctly, doubling the RBAC risk surface (PART 42 requires authorization to always be enforced server-side — every additional server in the chain is another place that requirement could be gotten wrong). React talking directly to the Spring Boot REST API removes all of this for no lost capability — Vite/Node is used only as a *build-time* tool for the frontend bundle, never as a request-time server.

### Logical Architecture

```
React (Vite build, served as static assets)
      ↓ HTTPS + JWT
REST API (Spring Boot @RestController layer)
      ↓
Spring Boot modules:
  ├── auth / security     — JWT issuance, Spring Security filter chain
  ├── academic            — Program/Stream/Year/Division/Batch, CR assignment
  ├── lab                 — Lab/Software/Equipment inventory
  ├── faculty             — Faculty + availability
  ├── subject             — Subject + requirements
  ├── importer            — PDF extraction/parsing/normalization/mapping
  ├── allocation/schedule — application services orchestrating the engine
  ├── constraint          — HC-01..HC-12 (hard, gating)
  ├── scoring             — soft-factor ranking (§07-ALLOCATION-SCORING.md)
  ├── conflict            — alternative-suggestion service
  └── audit               — append-only audit log
      ↓
PostgreSQL (single database, Flyway-migrated)
```

### Deployment Architecture (planned — see [12-DEPLOYMENT-GUIDE.md](12-DEPLOYMENT-GUIDE.md) for full detail, unverified until Phase 2/26)

```
Browser
   ↓
React static frontend (served via nginx or equivalent static host)
   ↓ HTTPS
Spring Boot API (single container, embedded Tomcat)
   ↓
PostgreSQL (single container/managed instance, Flyway migrations run on backend startup)
```

Docker Compose packages all three (Postgres, backend, frontend) for local development, demo, and CI, giving one reproducible `docker compose up` command instead of per-developer manual installs — it is a developer/ops convenience here, not a scaling mechanism (there is no requirement to scale backend/frontend independently at this project's size).

## 2. Role Matrix

See [02-REQUIREMENTS.md](02-REQUIREMENTS.md#role-summary) for the capability matrix. Full endpoint-level authorization rules are documented in [09-AUTHORIZATION-RBAC.md](09-AUTHORIZATION-RBAC.md) (Phase 3).

## 3. Core Entities

Finalized in [04-DATABASE-DESIGN.md](04-DATABASE-DESIGN.md) (Phase 1, this same pass) — that document is now the single source of truth for the ER diagram and per-entity rationale, so it is not duplicated here (a duplicate diagram is a duplicate to keep in sync, and the project's own working rules call for correcting rather than accumulating inconsistency). Key entities: `app_user`, `cr_assignment`, `program`→`stream`→`academic_year`→`division`→`batch`, `subject` (+ software/equipment/lab-type requirements), `faculty` (+ availability, + `subject_faculty_assignment` scoped to division/batch/term), `lab` (+ `lab_type`, `lab_software`, `lab_equipment`, `lab_unavailability`), `academic_term`→`schedule_version`→`allocation`, `timetable_import`→`timetable_import_entry`, `audit_log`.

Key modeling decision: `ALLOCATION.target_type` is an explicit `DIVISION | BATCH` enum, enforced by both application validation and a database CHECK constraint (see [04-DATABASE-DESIGN.md §7](04-DATABASE-DESIGN.md)) — not a null-as-hack.

## 4. Scheduling Concepts

- **SchedulingRequest** — the input: subject, target (division or batch), date, time window, requester.
- **SchedulingContext** — everything the constraint/scoring engine needs to evaluate a request: existing allocations in the relevant window, faculty availability, lab inventory, subject requirements.
- **CandidateAllocation** — one (lab, faculty, time) combination under consideration.
- **ConstraintResult / ConstraintViolation** — pass/fail + reason from a single `SchedulingConstraint`.
- **ScoreBreakdown** — per-scorer contributions plus total, for a valid candidate.
- **AllocationDecision** — the final selected candidate plus full explanation (constraints passed, score breakdown, rejected alternatives with reasons).

Full detail in [05-SCHEDULING-ENGINE.md](05-SCHEDULING-ENGINE.md). **Roadmap correction (Phase 8):** this Phase-1-era paragraph originally tagged the whole scheduling-engine document "(Phase 8)" without distinguishing the persisted schema from the algorithm — a label never revisited across Phases 4–7, which is exactly the inconsistency Phase 8 corrects. The finalized numbering (§16 below) is: Phase 8 builds the `Allocation`/`ScheduleVersion` persistence layer and these domain objects' *shapes*; Phase 9 is the Constraint Engine that evaluates them; Phase 10 onward is candidate generation, scoring, explainability, alternatives, and backtracking, in that order.

## 5. Allocation Lifecycle — Two Separate State Machines, Deliberately Not One

**Design question resolved (phase brief §16):** do REGULAR and EXTRA allocations need identical lifecycles, sharing states like `DRAFT`/`PENDING_REVIEW`/`CONFLICT`/`REJECTED`? **No.** Modeling them as one shared state machine was the initial (Phase 0) draft and, on review, created exactly the kind of contradiction the Phase 1 consistency check is meant to catch: `CONFLICT` and `REJECTED` are properties of *unreviewed, not-yet-real* data (a PDF-extracted row that might be wrong), never of an `Allocation` row itself — an `Allocation` is only ever created once it is already known to be valid (either Lab-Assistant-approved from a `TimetableImportEntry`, or hard-constraint-validated in the same transaction as an EXTRA booking). Splitting the lifecycle into two purpose-built state machines removes four states that never actually applied to `Allocation` and answers every question the brief poses directly:

| Question | Answer |
|---|---|
| Does EXTRA need Lab Assistant approval? | **No.** EXTRA passes the same hard constraints (HC-01..HC-12) inside one atomic transaction and is created directly as `APPROVED`, then immediately `PUBLISHED` (see below) — approval-by-review is a REGULAR/import-only concept. |
| Can a CR's valid EXTRA booking immediately become active? | **Yes** — that's the entire point of FCFS extra-lab booking; waiting for a human or for the next scheduled publish would defeat it. |
| Can a published allocation be cancelled? | **Yes**, for `EXTRA` allocations only, by the owning CR (or by Lab Assistant for any). `REGULAR` allocations are cancelled only by the Lab Assistant (e.g. correcting a mistake before the next re-publish), never by a CR. |
| Can a rejected allocation be edited? | N/A to `Allocation` — rejection happens at the `TimetableImportEntry` stage (correctable, re-validated) before an `Allocation` row ever exists. |
| Does "conflict" exist as a persistent status? | **No** — it is always a transient validation outcome (`TimetableImportEntry.validation_status = CONFLICT`, or an API error response for a rejected EXTRA request), never written as an `Allocation.status` value. |

### `Allocation.status` (both types — deliberately small)

```mermaid
stateDiagram-v2
    [*] --> APPROVED: constraints satisfied at creation (import approval, or EXTRA transaction commit)
    APPROVED --> PUBLISHED: included in / cascades from a published ScheduleVersion
    PUBLISHED --> CANCELLED: EXTRA — owning CR (or Lab Assistant); REGULAR — Lab Assistant only
    APPROVED --> CANCELLED: cancelled before its version publishes
    PUBLISHED --> [*]
    CANCELLED --> [*]
```

- **REGULAR**: created as `APPROVED`, attached to the term's current `DRAFT` `ScheduleVersion`; becomes `PUBLISHED` only when the Lab Assistant explicitly publishes that version (cascades to every `APPROVED` allocation under it in the same transaction).
- **EXTRA**: only reaches `APPROVED`+`PUBLISHED` after the full pipeline completes, in order: (1) authorization — CR's `divisionId` resolved from their own `cr_assignment`, never trusted from the request (HC-11); (2) latest-state hard-constraint validation — HC-01..HC-12 re-checked against the database as it stands *right now*, not against a stale client-side candidate list; (3) transactional concurrency protection — the check-and-insert happens inside one transaction with the locking/exclusion mechanism from ADR-010, so a second concurrent request for the same resource cannot slip through between check and commit; (4) only once the commit actually succeeds is the row written as `APPROVED` **and immediately `PUBLISHED`** in that same transaction, attached to the term's *currently published* `ScheduleVersion` (resolves ASSUMPTIONS A-11 — extra labs do not wait for the next official version cut; they overlay the live published version as soon as they're validly booked). **At no point does an EXTRA allocation skip authorization, hard-constraint validation, or commit-time revalidation** — "immediate" describes how fast a *valid, committed* booking becomes visible, not a shortcut around the checks themselves.
- Only `APPROVED`/`PUBLISHED` rows occupy a lab/faculty/batch for future conflict checks (matches the partial indexes in [04-DATABASE-DESIGN.md §7](04-DATABASE-DESIGN.md)).
- `CANCELLED` is terminal, never deletes the row, and always records `cancelledBy`/`cancelledAt`/`cancellationReason`.

### `TimetableImportEntry.validation_status` (import-only — separate lifecycle)

```mermaid
stateDiagram-v2
    [*] --> PENDING: extracted from PDF
    PENDING --> VALID: passes hard-constraint validation
    PENDING --> CONFLICT: fails hard-constraint validation
    CONFLICT --> PENDING: Lab Assistant corrects mapped fields, re-validation triggered
    VALID --> [*]: import approved → materializes as a new Allocation (APPROVED, REGULAR)
```

`REJECTED` at the whole-import level (`timetable_import.status`) means the Lab Assistant discarded the entire batch — individual entries are never force-approved while `CONFLICT`.

## 6. Allocation Request Flow

```mermaid
flowchart TD
    Req[Scheduling Request] --> Authz[Authorization: role + ownership check]
    Authz --> CandGen[Candidate Generation]
    CandGen --> HC[Hard Constraint Validation HC-01..HC-12]
    HC -->|invalid| Alt[Conflict + Alternative Suggestions]
    HC -->|valid candidates| Score[Scoring Engine]
    Score --> Rank[Ranked Candidates]
    Rank --> Select[Selection - auto or CR choice]
    Select --> Tx[Atomic DB Transaction: revalidate + insert]
    Tx -->|success| Audit[Audit Log]
    Tx -->|constraint violated concurrently| Alt
```

## 7. PDF Import Flow

```mermaid
flowchart TD
    PDF[PDF Upload] --> Extract[Extraction]
    Extract --> Parse[Parsing]
    Parse --> Normalize[Normalization]
    Normalize --> Map[Entity Mapping]
    Map --> Validate[Validation + Conflict Detection]
    Validate --> Review[Lab Assistant Review]
    Review --> Correct[Correction if required]
    Correct --> Review
    Review --> Approve[Approval]
    Approve --> Publish[Publication]
```

## 8. Backend Module Boundaries (Java packages)

```
auth/ user/ academic/ faculty/ lab/ subject/
schedule/ allocation/ constraint/ scoring/ conflict/
importer/ audit/ security/ common/
```

`constraint`, `scoring`, `conflict`, and the scheduler within `schedule` depend only on the domain objects in §4 (or their own package), not on JPA entities or HTTP DTOs directly — application services in `allocation`/`schedule` translate between persistence, domain, and API layers. This keeps the engine unit-testable without a database (NFR-08).

## 9. Consistency Check (Phase 1 sign-off)

- Role matrix (§2) matches FR-01..FR-29 in [02-REQUIREMENTS.md](02-REQUIREMENTS.md). ✅
- State machine (§5) matches the allocation lifecycle referenced by FR-15, FR-24, FR-25. ✅
- Entity diagram (§3) covers every entity named in the original spec's PART 39, with the documented decision *not* to blindly create a table for every name until relationships are modeled (deferred to Phase 4+ per-domain migrations). ✅
- No UI work has started; this document and its siblings are the only Phase 1 output. ✅

## 10. Phase 2 — Implemented Foundation (actual, verified state)

Everything in §§1-9 above was the Phase 1 *plan*. This section records what was actually built and verified in Phase 2 (Project Foundation) — see [12-DEPLOYMENT-GUIDE.md](12-DEPLOYMENT-GUIDE.md) and [13-DEVELOPER-SETUP.md](13-DEVELOPER-SETUP.md) for full verified command output.

### Actual repository structure

```
Lab_allocation/
├── backend/                      Spring Boot 4.1.1, Java 21, Maven (wrapper committed)
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd / .mvn/
│   ├── Dockerfile                multi-stage: maven:3.9-eclipse-temurin-21 -> eclipse-temurin:21-jre-alpine
│   └── src/main/java/com/college/laballocation/
│       ├── LabAllocationBackendApplication.java
│       ├── common/                ApiErrorResponse, ApiException, ResourceNotFoundException, GlobalExceptionHandler
│       └── config/                CorsConfig
│   └── src/main/resources/
│       ├── application.yml, application-dev.yml, application-test.yml
│       └── db/migration/V1__baseline.sql
├── frontend/                      React 19 + TypeScript + Vite 8
│   ├── Dockerfile                 multi-stage: node:20-alpine -> nginx:1.27-alpine
│   ├── nginx.conf                 SPA fallback routing for React Router
│   └── src/{api,components,features,hooks,layouts,pages,routes,types}/
├── docs/
├── .github/workflows/             placeholder only - CI pipeline itself is Phase 27
├── docker-compose.yml             postgres + backend + frontend, healthchecks, named volume
├── .env.example
└── README.md
```

### Verified runtime versions (this development machine, 2026-08-21)

| Tool | Planned (Phase 1, ASSUMPTIONS A-04) | Actual, verified |
|---|---|---|
| Java | 21 | **21.0.12** (Eclipse Temurin, installed via winget mid-phase — machine had no JDK beforehand) |
| Maven | 3.9+ | **3.9.16** (resolved automatically by the committed Maven Wrapper — no system-wide Maven needed) |
| Node.js | 20 LTS (ASSUMPTIONS A-04) | **v24.14.0** — newer than planned; used as-is since it built/tested/ran the Vite/React toolchain without issue. ASSUMPTIONS A-04 is superseded by this actual verified version for local dev; Docker's frontend build stage still pins `node:20-alpine` for a reproducible container build regardless of the host machine's Node version. |
| npm | bundled | **11.9.0** |
| Spring Boot | 3.x (original Phase 0 assumption) | **4.1.1** — Spring Initializr's supported version range had moved past 3.x entirely by the time this phase ran (`start.spring.io` rejected 3.3.x as below its minimum supported range); superseding ASSUMPTIONS A-04's "Spring Boot 3.x" framing was unavoidable, not a discretionary choice. Package names changed accordingly (e.g. `spring-boot-starter-webmvc` instead of `spring-boot-starter-web`, `org.springframework.boot.resttestclient.TestRestTemplate` instead of `org.springframework.boot.test.web.client.TestRestTemplate`) - noted here so the difference from any Spring Boot 3.x tutorial/reference material is understood, not mistaken for an error. |
| Docker | recent stable | Docker Desktop 4.86.0 / Engine 29.7.2, confirmed working via CLI and Docker Compose |

### Configuration flow (actual)

`docker-compose.yml` / local shell environment variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT`, `SPRING_PROFILES_ACTIVE`, `CORS_ALLOWED_ORIGINS`) → `application.yml` placeholders (`${DB_HOST:localhost}` etc., sensible localhost defaults for bare `./mvnw spring-boot:run` without Docker) → Spring Environment → `DataSource`/`CorsConfig` beans. No credential ever has a non-placeholder default beyond a clearly-labeled local dev value (`change_me`), and `.env` is git-ignored (`.env.example` is the committed template).

### Health flow (actual)

`GET /actuator/health` (Spring Boot Actuator, `management.endpoints.web.exposure.include: health,info` only — the rest of the actuator surface is not exposed) → aggregates the built-in DB health indicator (via `spring-boot-starter-data-jpa`'s auto-configured `DataSourceHealthIndicator`) → `{"status":"UP",...}` once the datasource is reachable. Verified end-to-end in `LabAllocationBackendApplicationIT` against a real Testcontainers-launched PostgreSQL instance (not a mock).

### Deviations from the Phase 1 plan, and why

- **Spring Boot 4.1.1 instead of "3.x"** — forced by Spring Initializr's supported version window at the time of implementation (see table above); the modular-monolith / package-boundary architecture (§1, §8) is unaffected, only some artifact/class names differ from Boot 3.x conventions.
- **Testcontainers-backed integration test separated into Failsafe (`mvn verify`), not Surefire (`mvn test`)** — see ADR-014 in [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md); this keeps the default build fast and Docker-independent, which turned out to matter directly in this development environment (see Known Limitations in [13-DEVELOPER-SETUP.md](13-DEVELOPER-SETUP.md)).
- Everything else (module boundaries, entity design, constraint/scoring/conflict separation, API base path, CORS approach) matches the Phase 1 plan unchanged.

## 11. Phase 3 — Authentication + RBAC (implemented)

### Authentication flow

```mermaid
sequenceDiagram
    participant Browser
    participant AuthController
    participant AuthService
    participant UserRepository
    participant JwtService
    participant DB as PostgreSQL

    Browser->>AuthController: POST /api/auth/login {email, password}
    AuthController->>AuthService: login(request)
    AuthService->>UserRepository: findByEmail(normalized email)
    UserRepository->>DB: SELECT * FROM app_user WHERE email = ?
    DB-->>UserRepository: row (or none)
    AuthService->>AuthService: passwordEncoder.matches(raw, hash) + isActive()
    AuthService->>JwtService: generateToken(userId, role)
    JwtService-->>AuthService: signed JWT (HS256)
    AuthService-->>AuthController: LoginResponse
    AuthController-->>Browser: 200 {accessToken, tokenType, expiresIn, user}
```

### Authenticated request flow

```mermaid
sequenceDiagram
    participant Browser
    participant Filter as JwtAuthenticationFilter
    participant JwtService
    participant UserRepository
    participant DB as PostgreSQL
    participant Controller

    Browser->>Filter: any /api/** request, Authorization: Bearer <token>
    Filter->>JwtService: parseAndValidate(token)
    JwtService-->>Filter: claims (or empty if invalid/expired)
    Filter->>UserRepository: findById(userId).filter(isActive)
    UserRepository->>DB: SELECT * FROM app_user WHERE id = ?
    DB-->>UserRepository: row (or none / inactive)
    Filter->>Filter: SecurityContext ← Authentication(userId, ROLE_<current role>)
    Filter->>Controller: continue filter chain
    Controller-->>Browser: 200 (or 401/403 via RestAuthenticationEntryPoint/RestAccessDeniedHandler if unauthenticated/unauthorized)
```

The user is **re-fetched from the database on every request** (not cached from the token) specifically so a deactivation takes effect immediately (docs/09-AUTHORIZATION-RBAC.md).

### New backend packages (Phase 3)

```
com.college.laballocation.user/       AppUser (entity), UserRole (enum), UserRepository, DevUserSeeder (@Profile("dev"))
com.college.laballocation.auth/       AuthController, AuthService, LoginRequest/LoginResponse/UserSummary (DTOs), InvalidCredentialsException
com.college.laballocation.security/   SecurityConfig, JwtService, JwtAuthenticationFilter, RestAuthenticationEntryPoint, RestAccessDeniedHandler
```

### New frontend modules (Phase 3)

```
frontend/src/api/tokenStorage.ts      localStorage-backed JWT storage (trade-off documented in-code and in ADR-015)
frontend/src/api/auth.ts              login(), fetchCurrentUser()
frontend/src/api/client.ts            updated: attaches Authorization header, routes 401s to a registered handler
frontend/src/features/auth/           AuthContext/useAuth, LoginPage, ProtectedRoute, RequireRole
```

### Verified end-to-end (Dockerized, 2026-08-21/22)

Login for all three demo roles, `GET /api/auth/me` returning the correct role for each, `401` for no/invalid token, `401 INVALID_CREDENTIALS` for wrong password/unknown email/deactivated account, CORS preflight correctly scoped to `http://localhost:5173`, Flyway `V2__create_app_user.sql` applied, and the three demo users present in `app_user` inside the running Postgres container. Full command-by-command detail in docs/13-DEVELOPER-SETUP.md.

## 12. Phase 4 — Academic Domain (implemented)

### Academic-domain request flow (Program/Stream/AcademicYear/AcademicTerm/Division/Batch/Subject/Faculty)

```mermaid
flowchart TD
    Controller["Controller (e.g. DivisionController)"] --> Preauth["@PreAuthorize hasRole LAB_ASSISTANT (write only - GET is open to any authenticated role)"]
    Preauth --> Service["Academic Service (e.g. DivisionService)"]
    Service --> Validate["Relationship Validation\n(parent exists, cross-table relationships e.g. batch-belongs-to-division)"]
    Validate --> Repo["Spring Data Repository"]
    Repo --> DB[(PostgreSQL)]
    Validate -->|invalid| Err["ApiException -> standard JSON error\n(e.g. INVALID_ACADEMIC_RELATIONSHIP)"]
```

Every create/update path resolves its parent entity through the corresponding service's `getEntity(id)` (throws a specific `*_NOT_FOUND` code if missing) before touching the repository — foreign keys catch a nonexistent id, but not a *wrong* one (e.g. a real batch that belongs to a different division), which is why explicit relationship validation exists as its own step, not folded into persistence.

### CR ownership resolution flow

```mermaid
flowchart TD
    JWT["JWT (sub = userId)"] --> Filter[JwtAuthenticationFilter]
    Filter --> Principal["SecurityContext: userId + ROLE_CR"]
    Principal --> Endpoint["GET /api/cr-assignments/me\n(or any future CR-scoped write)"]
    Endpoint --> Ownership[CrOwnershipService]
    Ownership --> Lookup["CrAssignmentRepository.findByUserIdOrderByCreatedAtDesc(userId)"]
    Lookup --> Filter2["filter: status=ACTIVE AND term.status=ACTIVE"]
    Filter2 -->|found| Resolved["Resolved: the CR's real division"]
    Filter2 -->|none| NotFound["404 CR_ASSIGNMENT_NOT_FOUND"]
    Resolved --> Compare{"divisionId in request\nmatches resolved division?"}
    Compare -->|yes| Allowed["Operation proceeds"]
    Compare -->|no| Forbidden["403 FORBIDDEN_DIVISION_ACCESS"]
```

The client-supplied `divisionId` (where one exists in a future request body) is never trusted as authorization — only ever cross-checked against the value resolved purely from the authenticated `userId`. See docs/09-AUTHORIZATION-RBAC.md for the full role-vs-ownership distinction and the worked CR-A/Division-B example.

### New backend packages (Phase 4)

```
com.college.laballocation.academic/   Program, Stream, AcademicYear, AcademicTerm, Division, Batch,
                                       CrAssignment, their repositories/services/controllers/DTOs,
                                       CrOwnershipService, DevAcademicSeeder (@Profile("dev"))
com.college.laballocation.subject/    Subject + repository/service/controller/DTOs
com.college.laballocation.faculty/    Faculty, SubjectFacultyAssignment, FacultyAssignmentResolutionService,
                                       their repositories/services/controllers/DTOs
```

### Deviations from the Phase 1 plan, and why

- **`division.strength`/`batch.strength` are `NOT NULL`, not nullable** as an earlier draft had `division.strength` — both target types (BATCH/DIVISION) need a real capacity number for HC-07, so leaving one nullable would just defer a null-check to the constraint engine later. Corrected now rather than migrated later.
- **API paths are flat resource nouns** (`/api/programs`, `/api/divisions`, ...), not nested under `/api/academic/...` as the Phase 1 sketch in docs/10-API-DOCUMENTATION.md loosely suggested — simpler routing, consistent with the rest of the API surface, and updated in that document to match.
- Everything else (entity relationships, no-`SubjectOffering` decision, CR assignment history model, capacity source of truth) matches the Phase 1 plan unchanged.

## 13. Phase 5 — Laboratory Domain (implemented)

### Lab management flow

```mermaid
flowchart TD
    Controller["LabController / LabCapabilityController / LabUnavailabilityController"] --> Preauth["@PreAuthorize hasRole LAB_ASSISTANT (write only)"]
    Preauth --> Service["LabService / LabCapabilityService / LabUnavailabilityService"]
    Service --> Validate["Validation: capacity>0, lab type exists, no duplicate code/association, interval end>start"]
    Validate --> Repo["LabRepository / LabSoftwareRepository / LabEquipmentRepository / LabUnavailabilityRepository"]
    Repo --> DB[(PostgreSQL)]
```

### Static capability filtering (not scheduling)

```mermaid
flowchart LR
    Query["GET /api/labs?minCapacity=68&software=CLOUDERA"] --> Spec["LabSpecifications (JPA Specification, composable AND)"]
    Spec --> Active[active filter]
    Spec --> Cap["minCapacity filter"]
    Spec --> Soft["hasAllSoftware - one EXISTS subquery per requested code, ANDed"]
    Spec --> Equip["hasAllEquipment - same pattern"]
    Active --> DB[(PostgreSQL)]
    Cap --> DB
    Soft --> DB
    Equip --> DB
```

**How the future scheduling engine will consume, but not own, this data:** Phase 9+'s constraint engine (HC-06/07/08/09/10) will *read* `lab`, `lab_type`, `lab_software`, `lab_equipment`, and `lab_unavailability` as inputs to candidate generation and hard-constraint validation — but it will never write to them. Lab master data ownership stays entirely with the Lab Assistant via this phase's API; the scheduling engine is a consumer, not a co-owner, of the laboratory domain (mirroring how it will later read, not own, the academic domain from Phase 4).

### New backend package (Phase 5)

```
com.college.laballocation.lab/   LabType, Lab, Software, Equipment, LabSoftware, LabEquipment, LabUnavailability,
                                  their repositories/services/controllers/DTOs, LabSpecifications (static capability
                                  filtering), DevLabSeeder (@Profile("dev"))
```

### Verified end-to-end (Dockerized, 2026-08-22)

15 seeded labs across wings B/C/D (5/5/5), capacities 30–80, 3 of 15 with Cloudera (2 satisfying capacity ≥ 68, 1 smaller); RBAC (LAB_ASSISTANT create allowed, CR/STUDENT 403, unauthenticated 401); invalid capacity and duplicate-code rejected cleanly; Cloudera filter, capacity filter, and their AND-combination all verified against real seeded data; unavailability `end <= start` rejected; all DB constraints (capacity CHECK, interval CHECK, association uniqueness) confirmed directly in the schema; seed idempotency confirmed by restarting the backend container and re-checking row counts. Full detail in docs/13-DEVELOPER-SETUP.md.

### Deviations from the Phase 1 plan

- **`lab_unavailability` uses full `TIMESTAMPTZ` granularity**, not the date-level granularity this document's Phase 1 draft originally sketched as "sufficient" — superseded per the Phase 5 brief's explicit instruction to use proper temporal types with clear interval semantics (docs/04-DATABASE-DESIGN.md).
- Everything else (configurable `LabType`/`Software`/`Equipment` instead of enums, explicit `LabSoftware`/`LabEquipment` association entities, flat location model) matches the Phase 1 plan.

## 14. Phase 6 — Subject Requirements (implemented)

### Requirement management flow

```mermaid
flowchart TD
    Controller[SubjectRequirementController] --> Preauth["@PreAuthorize hasRole LAB_ASSISTANT (write only)"]
    Preauth --> Service[SubjectRequirementService]
    Service --> Validate["Validation: subject/software/equipment/labType exist and are active, quantity>0, required xor preferred lab type"]
    Validate --> Repo["SubjectSoftwareRequirementRepository / SubjectEquipmentRequirementRepository / Subject entity"]
    Repo --> DB[(PostgreSQL)]
```

### The two-sided model, staged deliberately (the project's core "not-CRUD" story)

```mermaid
flowchart LR
    subgraph Subject Side - Phase 6
        SR[Subject Requirements] --> BDA["BDA requires: Cloudera<br/>prefers: Data Engineering"]
    end
    subgraph Lab Side - Phase 5
        LC[Lab Capabilities] --> Labs["B-201, B-301, C-202 have Cloudera<br/>C-304 does not"]
    end
    BDA -.->|"read by, not yet compared"| CE["Constraint Engine (Phase 9 - NOT YET IMPLEMENTED)"]
    Labs -.->|"read by, not yet compared"| CE
    CE -.-> Result["'Can lab X host BDA' - not answerable until Phase 9"]
```

Phase 6 never writes to `lab_software`/`lab_equipment`/`lab.lab_type_id` (Phase 5's tables); Phase 5 never writes to `subject_software_requirement`/`subject_equipment_requirement`/`subject.required_lab_type_id` (Phase 6's columns). Each phase's manual Docker verification deliberately queried *both* sides independently (`GET /api/subjects/{id}/requirements` and `GET /api/labs?software=CLOUDERA` as two separate, uncombined facts) specifically to keep this separation honest, not just documented.

**Update (Phase 9):** the diagram above reflects this section's original Phase 6 state. `RequiredSoftwareConstraint`/`RequiredEquipmentConstraint`/`RequiredLabTypeConstraint` now exist and do the comparison this diagram calls "not yet compared" — see §17 below and docs/06-CONSTRAINTS.md HC-08/09/10.

### New backend additions (Phase 6)

```
com.college.laballocation.subject/   + SubjectSoftwareRequirement, SubjectEquipmentRequirement (entities),
                                        their repositories, SubjectRequirementService, SubjectRequirementController,
                                        SubjectRequirementDtos, DevSubjectRequirementSeeder (@Profile("dev"))
                                      Subject (Phase 4 entity) + required/preferred lab-type fields and
                                        setLabTypeRequirement() validation
```

### Verified end-to-end (Dockerized, 2026-08-22)

BDA's requirements (`Cloudera`, preferred `DATA_ENGINEERING`, no required type) retrieved via the real endpoint and cross-referenced against Phase 5's independently-verified Cloudera-capable lab list; CNS confirmed to have zero requirements of any kind (empty lists, both lab-type fields null) with no error; RBAC (LAB_ASSISTANT allowed, CR/STUDENT 403, unauthenticated read 401); duplicate software requirement rejected as `409 SOFTWARE_REQUIREMENT_ALREADY_EXISTS`; equipment `requiredQuantity=0` rejected, `=5` accepted; a newly-deactivated software correctly rejected as a new requirement (`INACTIVE_SOFTWARE`); both/neither lab-type-preference validation proven at the unit level; all DB constraints (`chk_ser_quantity_positive`, `chk_subject_lab_type_pref`, uniqueness) confirmed directly in the schema; seed idempotency confirmed by restarting the backend container (BDA→Cloudera remained exactly one row); Phase 5's lab/software/equipment counts confirmed unchanged. Full detail in docs/13-DEVELOPER-SETUP.md.

### Deviations from the Phase 1 plan

None of substance — the required-vs-preferred lab-type distinction, subject-level (not per-term) requirement scope, and ALL-required software/equipment semantics all match what Phase 1's design already anticipated.

## 15. Phase 7 — Faculty Availability (implemented)

### Availability management + evaluation flow

```mermaid
flowchart TD
    Controller[FacultyAvailabilityController] --> Preauth["@PreAuthorize hasRole LAB_ASSISTANT (read AND write)"]
    Preauth --> Service[FacultyAvailabilityService]
    Service --> Validate["Validation: faculty active, term not CLOSED, start<end, no overlap with active rows"]
    Validate --> Repo[FacultyAvailabilityRepository]
    Repo --> DB[(PostgreSQL)]
    Service -->|isAvailable / check| Eval["Evaluation: merge adjacent active rows in memory, test containment via TimeIntervalUtils"]
```

### Faculty Availability vs. Faculty Conflict — two layers, never confused (PART 41 of the phase brief)

```mermaid
flowchart LR
    subgraph Layer 1 - Availability - Phase 7 implemented
        FA["Faculty X available Mon 09:00-12:00"]
    end
    subgraph Layer 2 - Conflict - Phase 9+ not yet implemented
        FC["Faculty X already booked A1 09:00-11:00 -> A2 09:00-11:00 request fails HC-02, not HC-03"]
    end
    FA -->|"HC-03 - is the slot within declared availability at all?"| Decision["Both layers must independently pass for a session to be schedulable"]
    FC -->|"HC-02 - is the faculty already occupied by a real booking?"| Decision
```

A faculty can be generally *available* (HC-03) at a time they are already *booked* (HC-02) — availability is a static weekly boundary the faculty declares in advance; conflict depends on real allocation data. Phase 7 implemented the availability layer's data and evaluation logic; Phase 9 (§17 below) implemented both `FacultyAvailabilityConstraint` and `FacultyConflictConstraint` as real, independently-tested `SchedulingConstraint` classes, verified with a dedicated test proving they can disagree for the identical candidate (available but conflicted).

### New backend additions (Phase 7)

```
com.college.laballocation.common/    + TimeIntervalUtils (isValid/overlaps/contains, half-open [start,end)
                                        semantics reused by faculty availability now, future constraints later)
com.college.laballocation.faculty/   + FacultyAvailability (entity), FacultyAvailabilityRepository,
                                        FacultyAvailabilityService, FacultyAvailabilityController,
                                        FacultyAvailabilityDtos, DevFacultyAvailabilitySeeder (@Profile("dev"))
```

### Access model — deliberately narrower than Phase 5/6 (PART 22 of the phase brief)

Unlike Labs/Subjects (Phase 5/6), where `GET` is open to any authenticated role, `/api/faculty/{id}/availability*` restricts **both** read and write to `LAB_ASSISTANT`. Raw faculty-availability management data has no legitimate CR/STUDENT consumer yet — the future constraint engine (Phase 9+) will call `FacultyAvailabilityService` internally, not through this REST surface, so there is no student- or CR-facing use case this phase needs to support. See docs/09-AUTHORIZATION-RBAC.md's Permission Matrix (updated this phase) and ADR-034.

### Verified end-to-end (Dockerized, 2026-08-22)

All 8 of the phase brief's required scenarios (A-H: LAB_ASSISTANT creates valid availability, CR/STUDENT mutation both 403, unauthenticated read 401, overlapping create rejected `409 FACULTY_AVAILABILITY_OVERLAP`, adjacent create allowed, BDA-Monday-09:00-11:00 check returns available, BDA-Monday-13:00-14:00 check returns unavailable) executed with real `curl` requests against the running containers; PATCH and DELETE (soft-deactivate) verified directly; a restart-and-recount idempotency check confirmed the seeded+test-created row count (7) was unchanged across a backend container restart; Phase 4-6 regression endpoints (`/api/auth/me`, `/api/cr-assignments/me`, Cloudera lab filter, BDA subject requirements) re-verified working with no regression.

### Deviations from the Phase 1 plan

**`academic_term_id` made mandatory**, not nullable — the Phase 1 draft's "applies every term" design (docs/ASSUMPTIONS.md A-15) is superseded per the Phase 7 brief's explicit recommendation; see ADR-031. Read access restricted to `LAB_ASSISTANT` only (see above) is also a deliberate narrowing versus the open-read pattern of Phase 5/6, not an oversight — documented, not silent.

## 16. Phase 8 — Scheduling Domain & Allocation Persistence Foundation (implemented)

### Roadmap correction — finalized Phase 8–16 sequence

The single stale "(Phase 8)" tag on §4 above (pointing at the whole Scheduling Engine document, unrevised since Phase 1) is superseded by this explicit sequence, confirmed against the project's original phase brief:

| Phase | Scope |
|---|---|
| **8** | **Scheduling Domain & Allocation Persistence Foundation** (this phase) — `Allocation`/`ScheduleVersion` tables, the pure `SchedulingRequest`/`SchedulingContext`/`CandidateAllocation`/`ConstraintResult`/`ConstraintViolation` object shapes, and the repository/query infrastructure Phase 9 needs. No constraint evaluation. |
| 9 | Constraint Engine — the actual `SchedulingConstraint` classes (HC-01..HC-12) that read this phase's persisted data and evaluate a candidate. |
| 10 | Candidate Generation — querying/filtering labs into a candidate list for a request. |
| 11 | Scoring Engine — ranking valid candidates (docs/07-ALLOCATION-SCORING.md). |
| 12 | Explainable Allocation — `AllocationDecision`, the final selected-candidate-plus-explanation result type (deliberately deferred past Phase 8, see §17.7 below). |
| 13 | Conflict Detection + Alternatives — ranked fallback suggestions when a request fails. |
| 14 | Automatic Scheduling / Backtracking — most-constrained-first multi-session generation. |
| 15 | Extra Lab Scheduling — the CR-facing FCFS booking flow that finally creates real `Allocation` rows in production. |
| 16 | FCFS / Concurrency — finalizes ADR-010's provisional concurrency mechanism. |

This is a documentation correction, not a change to any business requirement — every FR/HC this project has already documented (docs/02-REQUIREMENTS.md, docs/06-CONSTRAINTS.md) is unaffected; only the phase *numbering* for not-yet-built algorithm work is being made internally consistent (11-TESTING-STRATEGY.md's "Algorithm Test Coverage" section header is corrected to match, see that document).

### Persisted state vs. transient algorithm objects

```mermaid
flowchart TD
    subgraph Persisted - Phase 8 implemented
        SV[ScheduleVersion] --> AL[Allocation]
    end
    subgraph Transient domain objects - Phase 8 shapes only, Phase 9+ populates/evaluates them
        SR[SchedulingRequest] --> SC[SchedulingContext]
        SC --> CA[CandidateAllocation]
    end
    AL -.->|"read by SchedulingContextFactory"| SC
    CA -.->|"evaluated by"| CE["Phase 9 Constraint Engine - NOT YET IMPLEMENTED"]
    CE -.-> CR[ConstraintResult / ConstraintViolation]
```

`ScheduleVersion`/`Allocation` are real, migrated JPA entities (`V10__create_schedule_version_and_allocation.sql`). `SchedulingRequest`, `SchedulingContext`, `CandidateAllocation`, `ConstraintResult`, `ConstraintViolation` are plain Java records with no JPA annotations at all (NFR-08) — see docs/05-SCHEDULING-ENGINE.md for the full per-object contract Phase 9 will implement against.

### New backend additions (Phase 8)

```
com.college.laballocation.scheduling/  + AllocationType, TargetType, AllocationStatus, ScheduleVersionStatus (enums)
                                          ScheduleVersion (entity), ScheduleVersionRepository, ScheduleVersionService
                                          Allocation (entity, static forBatch/forDivision factories), AllocationRepository,
                                          AllocationQueryService (read-only, no creation methods)
                                          SchedulingTimeMapper, InstantRange (LocalDate/LocalTime <-> Instant bridge)
                                          SchedulingRequest, SchedulingContext, SchedulingRefs, CandidateAllocation,
                                          ConstraintResult, ConstraintViolation, HardConstraintId (pure domain objects)
                                          SchedulingContextFactory (assembles a context from existing Phase 4/5 services)
                                          DevScheduleVersionSeeder (@Profile("dev") - seeds ScheduleVersion only, no Allocation rows)
```

### No production Allocation-creation API (deliberate, PART 31 of the phase brief)

Neither `POST /api/allocations` nor any `AllocationController` exists. A raw `Allocation` row must never bypass the constraint engine that doesn't exist until Phase 9 — verified live: `POST /api/allocations` and `GET /api/allocations` both return `404` against the running stack, confirming no such surface was accidentally exposed. `Allocation` rows in this phase are only ever created via direct repository calls from tests and the dev seeder — never through a REST boundary.

### Verified end-to-end (Dockerized, 2026-08-22)

Flyway migrated to v10 cleanly; `DevScheduleVersionSeeder` created one `PUBLISHED` `ScheduleVersion` (term "Semester 5 (2026-27)", version 1) with zero `Allocation` rows, confirmed via direct `psql` query. Five DB-level guarantees proven with real, transactional `psql` inserts against the live container: (1) a valid DIVISION-targeted allocation insert succeeds; (2) a BATCH-targeted insert with a null `batch_id` is rejected by `chk_allocation_target_invariant`; (3) `end_time <= start_time` is rejected by `chk_allocation_interval`; (4) a duplicate `(academic_term_id, version_number)` is rejected by `uq_schedule_version_term_number`; (5) a second `PUBLISHED` version for the same term is rejected by `uq_schedule_version_one_published_per_term`. A restart-and-recount idempotency check confirmed the seeded `schedule_version` row count (1) was unchanged across a backend container restart. Phase 3-7 regression endpoints (`/api/auth/me`, `/api/cr-assignments/me`, Cloudera lab filter, BDA subject requirements, faculty availability) all re-verified working with no regression.

### Deviations from the Phase 1 plan

None of substance to the schema itself — `Allocation`/`ScheduleVersion`'s fields, invariants, and lifecycle match what docs/04-DATABASE-DESIGN.md §7 and ADR-005/ADR-009 already specified. The one real addition beyond the Phase 1 draft is the college-timezone bridge (`SchedulingTimeMapper`, `app.college.time-zone`) — the Phase 1 plan never anticipated the `LocalDate`/`LocalTime` (Allocation) vs. `TIMESTAMPTZ`/`Instant` (LabUnavailability, Phase 5) type boundary that HC-06 needed to cross; resolving it in Phase 8 (rather than leaving every future constraint to solve it independently) is a deliberate, documented addition — see ADR-037.

## 17. Phase 9 — Constraint Engine (implemented)

### Pipeline

```mermaid
flowchart TD
    Req[SchedulingRequest] --> Ctx[SchedulingContext<br/>via SchedulingContextFactory]
    Ctx --> Cand[CandidateAllocation<br/>via CandidateAllocationFactory]
    Cand --> Engine[ConstraintEngine.evaluate]
    Engine --> HC12[HC-12 Academic Relationship]
    Engine --> HC07[HC-07 Capacity]
    Engine --> HC08[HC-08 Required Software]
    Engine --> HC09[HC-09 Required Equipment]
    Engine --> HC10[HC-10 Required Lab Type]
    Engine --> HC03[HC-03 Faculty Availability]
    Engine --> HC06[HC-06 Lab Availability]
    Engine --> HC01[HC-01 Lab Conflict]
    Engine --> HC02[HC-02 Faculty Conflict]
    Engine --> HC04[HC-04 Batch Conflict]
    Engine --> HC05[HC-05 Division-Wide Conflict]
    Engine --> HC11[HC-11 CR Authorization]
    HC12 & HC07 & HC08 & HC09 & HC10 & HC03 & HC06 & HC01 & HC02 & HC04 & HC05 & HC11 --> Eval[ConstraintEvaluation<br/>valid + all results + all violations]
```

All twelve constraints run for every candidate (no fail-fast) - see docs/06-CONSTRAINTS.md for each HC's class, logic, and error code, and docs/05-SCHEDULING-ENGINE.md for the engine's own architecture notes (context reuse, applicability, deterministic ordering).

### New backend additions (Phase 9)

```
com.college.laballocation.scheduling/     + ConstraintOutcome (enum: PASS/FAIL/NOT_APPLICABLE)
                                             ConstraintResult, CandidateAllocation, SchedulingRequest, SchedulingRefs
                                               (all extended - see docs/05-SCHEDULING-ENGINE.md)
                                             SchedulingActor (userId + UserRole, nullable on SchedulingRequest)
                                             CandidateAllocationFactory (builds one candidate's LabRef snapshot)
com.college.laballocation.scheduling.constraint/   SchedulingConstraint (interface)
                                                    ConstraintEngine, ConstraintEvaluation
                                                    LabConflictConstraint, FacultyConflictConstraint,
                                                      FacultyAvailabilityConstraint, BatchConflictConstraint,
                                                      DivisionWideConflictConstraint, LabAvailabilityConstraint,
                                                      CapacityConstraint, RequiredSoftwareConstraint,
                                                      RequiredEquipmentConstraint, RequiredLabTypeConstraint,
                                                      CrAuthorizationConstraint, AcademicRelationshipConstraint
com.college.laballocation.common/         TimeIntervalUtils gained an Instant overload of overlaps(...)
```

No new migration - Phase 9 is entirely code against the Phase 8 schema, confirmed live: Flyway remained at schema version 10 after this phase's work.

### No production Allocation-creation API (still, PART 31/65 of the phase brief)

Still true after Phase 9: `POST /api/allocations` and `GET /api/allocations` both return `404`. A temporary, `@Profile("dev")`-only `ApplicationRunner` (`DevConstraintEngineVerificationRunner`) was used to exercise the real, Spring-assembled `ConstraintEngine` against real dev-seeded data over the live Docker/Postgres stack for manual verification (Testcontainers remains environment-blocked here) - it created and cleaned up its own temporary rows (a test `Allocation`, a `LabUnavailability`, a `SubjectEquipmentRequirement`, and a temporary required-lab-type flip on BDA, all reverted/deleted within the same run) and was deleted from the codebase once verification was recorded, per the phase brief's explicit instruction not to leave a diagnostic harness behind.

### A real bug found and fixed: exception-across-transaction-boundary in HC-11

The first `CrAuthorizationConstraint` called `CrOwnershipService.requireOwnsDivision(...)` and caught its thrown `ApiException` subtypes to build a `FAIL` `ConstraintResult` - consistent-looking with "represent expected failure as a result, not an exception" until manual Docker verification actually exercised the unauthorized-CR scenario and the whole request failed with `UnexpectedRollbackException`. Root cause: `requireOwnsDivision` is itself `@Transactional`: Spring's transaction interceptor marks the *shared* surrounding transaction rollback-only the moment the exception crosses that method's boundary - before this constraint's own catch block ever runs. Catching the exception in Java code did not undo the transactional marker. Fix: switched to `CrOwnershipService.getCurrentAssignment(userId)` (`Optional`-returning, never throws) and compared the division directly - no exception crosses any `@Transactional` boundary for this expected-failure path at all now. See docs/14-INTERVIEW-PREPARATION.md and docs/06-CONSTRAINTS.md HC-11 for the full account, and docs/15-DESIGN-DECISIONS.md for the resulting ADR.

### Verified end-to-end (Dockerized, 2026-08-23)

All sixteen of the phase brief's required manual scenarios executed against the real Spring-assembled `ConstraintEngine` over live Docker/Postgres using the real seeded demo data (BDA/CNS, Faculty BDA/CNS, labs B-301/C-202/C-304/B-201/C-101, Cloudera): valid A1/A2 simultaneous batches (zero violations); same-lab, same-faculty, same-batch, and division-wide conflicts each correctly rejected; faculty-unavailable and lab-temporarily-unavailable each correctly rejected; BDA/Cloudera pass vs. BDA/no-Cloudera fail; equipment-quantity pass/fail; required-lab-type pass/fail plus the mandatory preferred-only-never-fails-HC-10 check (isolated at the per-constraint result level, since the chosen demo lab also lacked Cloudera for an unrelated reason); invalid academic relationship rejected; unauthorized CR context rejected (`CR_ASSIGNMENT_NOT_FOUND`); and a multi-failure candidate returned two simultaneous violations together (software + faculty availability), proving the engine does not fail fast. All sixteen scenarios' actual results matched their expected validity. Confirmed via `psql` after the run that every temporary row the verification harness created was cleaned up (zero leftover allocations, unavailability rows, or equipment requirements). Regression re-verified: `/api/auth/me`, `/api/programs`, `/api/labs`, `/api/subjects/{id}/requirements`, `/api/faculty/{id}/availability` all still 200; `/api/allocations` still 404 both before and after.

### Deviations from the Phase 1 plan

None to the constraint *rules* themselves - every HC-01..HC-12 implementation matches docs/06-CONSTRAINTS.md's Phase 1-through-6 specification exactly. Two real, deliberate additions beyond the original plan, both because manual Docker verification surfaced a genuine need: `ConstraintOutcome`'s three-way PASS/FAIL/NOT_APPLICABLE split (Phase 1 never anticipated a constraint being inapplicable rather than satisfied) and the `CrOwnershipService.getCurrentAssignment`-over-`requireOwnsDivision` fix described above (an implementation detail invisible to the constraint *specification*, but a real architectural lesson about exceptions and Spring transaction boundaries).
