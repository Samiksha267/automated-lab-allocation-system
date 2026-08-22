# Design Decisions

ADR-style log of significant, hard-to-reverse decisions. Each entry: Context, Decision, Alternatives, Reasons, Trade-offs, Consequences. Numbering follows the fixed set requested for this project (ADR-001..ADR-010) plus additional entries appended afterward as further decisions arise. Earlier entries are not rewritten, only superseded (with a note) if a later phase changes course.

---

## ADR-001: Modular Monolith, Not Microservices

**Context:** The scheduling engine has several logically distinct concerns (constraint checking, scoring, conflict resolution, backtracking search) that *could* be split into services; the college operates at a scale (~15 labs, one institution) with no independent-scaling requirement.

**Decision:** Single Spring Boot deployable containing all modules (`constraint`, `scoring`, `conflict`, `scheduler`, `allocation`, etc.) as Java packages, not separate services.

**Alternatives:** Node.js gateway + Java scheduling service; full microservices (auth service, scheduling service, import service, etc.); Python-based scheduling service called from Java.

**Reasons:** FCFS + concurrency correctness (Phase 16) is far simpler within one database transaction than across a distributed call — splitting the scheduler out would force either distributed transactions or a much weaker consistency guarantee for no functional benefit. Package-level boundaries already give the separation of concerns that matters for testability (NFR-08).

**Trade-offs:** If the system ever serves many institutions, this would need revisiting (see [18-FUTURE-IMPROVEMENTS.md](18-FUTURE-IMPROVEMENTS.md)).

**Consequences:** One build, one deployment pipeline, one transaction boundary; internal package boundaries (`auth/ user/ academic/ faculty/ lab/ subject/ schedule/ allocation/ constraint/ scoring/ conflict/ importer/ audit/ security/ common/`) are the enforced separation mechanism instead of network boundaries.

---

## ADR-002: Java 21 + Spring Boot as the Sole Backend

**Context:** The project's core value is a typed, testable, transactional scheduling domain, not template rendering or a thin CRUD layer.

**Decision:** Java 21 + Spring Boot for 100% of server-side logic — no secondary backend language/runtime.

**Alternatives:** Python (Django/FastAPI) backend, possibly pairing naturally with a Python constraint solver; Node.js/Express backend.

**Reasons:** Strong static typing suits a domain this relationally strict (labs, faculty, batches, terms, allocations all cross-reference each other with invariants that benefit from compile-time checking). Spring's transaction management is what makes the FCFS/concurrency guarantee (ADR-010) tractable within a single `@Transactional` boundary. Spring Security gives layered RBAC (coarse role checks + fine-grained ownership checks) without hand-rolling auth. The JUnit 5/Mockito/Testcontainers ecosystem lets the scheduling engine (`constraint`/`scoring`/`conflict` packages) be unit-tested in true isolation from the web and persistence layers (NFR-08). It also directly demonstrates enterprise Java/Spring skills relevant to how this project will be discussed in interviews.

**Trade-offs:** Java is more verbose than Python for quick scripting-style code; accepted since the domain logic here benefits more from type safety than from scripting brevity.

**Consequences:** All business logic — including the constraint/scoring/scheduling engine — lives in Java, tested with JUnit 5 + Mockito (unit) and Spring Boot Test + Testcontainers (integration), per [11-TESTING-STRATEGY.md](11-TESTING-STRATEGY.md).

---

## ADR-003: PostgreSQL as the Sole Datastore

**Context:** Allocation data is deeply relational (lab ↔ faculty ↔ batch ↔ division ↔ subject), and correctness depends on foreign key integrity and transactional guarantees; the hardest correctness requirement (FCFS double-booking prevention) depends directly on database-level guarantees.

**Decision:** PostgreSQL for everything, including raw import data (`TimetableImportEntry` stores original + corrected values as normal nullable columns, not a schemaless blob).

**Alternatives:** MongoDB (schema flexibility, particularly appealing for the PDF-import "raw extracted data" stage).

**Reasons:** The scheduling correctness guarantees (no double-booking) are only as strong as the database's transactional/locking model. A document store would push conflict-prevention logic entirely into the application layer, reintroducing the exact check-then-insert race this project explicitly must avoid (PART 34/64). Relational integrity also makes "which subjects require Cloudera and which labs have it" a straightforward join instead of application-level cross-referencing.

**Trade-offs:** Raw PDF extraction data is slightly less flexible to store than in a schemaless format; mitigated by keeping `TimetableImportEntry` wide/nullable rather than strict.

**Consequences:** All migrations are Flyway-managed SQL against Postgres; the FCFS mechanism (ADR-010) will use a Postgres-native transactional/locking feature, not an application-level lock.

---

## ADR-004: React + TypeScript Frontend

**Context:** Three structurally different dashboards (Lab Assistant, CR, Student) share primitives (timetable grid, lab card, allocation explanation panel) but differ sharply in permitted actions and data scope.

**Decision:** React + TypeScript + Vite, talking directly to the Spring Boot REST API.

**Alternatives:** Server-rendered Java views (Thymeleaf); Vue/Angular; plain JavaScript.

**Reasons:** Component architecture maps naturally onto shared-but-differently-permissioned dashboards. TypeScript gives compile-time safety across the many DTO shapes coming from a strongly-typed backend, catching contract drift (e.g. a renamed field in an allocation-search response) at build time rather than at runtime in front of a user. The ecosystem (TanStack Query for server-state caching/invalidation, React Hook Form + Zod for validated forms) avoids reinventing data-fetching and form-validation infrastructure that this project doesn't need to build custom.

**Trade-offs:** A separate frontend build/deploy step versus server-rendered pages; accepted since the role-differentiated, data-heavy dashboards benefit more from a real SPA than from server-rendered templates.

**Consequences:** Vite/Node is a **build-time only** dependency — see ADR that follows on why no Node runtime sits between the browser and Spring Boot.

---

## ADR-005: `target_type = BATCH | DIVISION` as an Explicit Column, Not Implicit Null

**Context:** The single most important domain rule in this project: two different batches of the same division can have simultaneous sessions (different labs, different faculty), but a division-wide session (e.g. a guest lecture requiring the whole division) must block every batch in that division.

**Decision:** `Allocation.target_type` is an explicit `DIVISION | BATCH` enum column, always populated, enforced by both application validation and a database CHECK constraint. `batch_id` is set only when `target_type = BATCH`.

**Alternatives:** Infer "division-wide" from `batch_id IS NULL` without a dedicated column; represent every division-level session as N separate per-batch rows.

**Reasons:** The original spec explicitly warns against "null batch ID hacks." An explicit, named column makes the two conflict types independently testable (HC-04 vs HC-05 in [06-CONSTRAINTS.md](06-CONSTRAINTS.md)) and self-documenting in every query that touches `Allocation`. The "N separate per-batch rows" alternative was rejected because it would require inserting/deleting a row per batch whenever the division's batch count changes, coupling scheduling data to academic-structure changes.

**Trade-offs:** One extra column, one CHECK constraint, and one extra hard constraint (HC-05) class versus the "just use null" shortcut — accepted, since the shortcut is the exact anti-pattern the spec calls out.

**Consequences:** The conflict interaction matrix in [06-CONSTRAINTS.md](06-CONSTRAINTS.md) is the canonical reference for how `BATCH`/`DIVISION` combinations interact; both HC-04 and HC-05 must be implemented and independently tested for the "different batches, same division, simultaneous" case (VALID) and the "division-wide vs any batch" case (always INVALID) to both hold.

---

## ADR-006: Faculty as a Pure Domain Entity, No Login Identity

**Context:** The project's confirmed role set is exactly three login roles (`LAB_ASSISTANT`, `CR`, `STUDENT`); faculty are referenced constantly by scheduling but do not authenticate.

**Decision:** `Faculty` has no corresponding `app_user` row, no password, no JWT identity. Faculty availability, assignments, and conflict-checking are all maintained/viewed by the Lab Assistant on faculty's behalf.

**Alternatives:** Give faculty a fourth login role with a personal dashboard (view their own schedule, submit their own availability).

**Reasons:** The confirmed requirement explicitly scopes login to three roles. Adding faculty login now would mean building a fourth auth/authorization path, a fourth dashboard, and self-service availability editing (which raises its own conflict-detection questions — e.g., can a faculty member unilaterally shrink their availability out from under an already-scheduled session?) for a capability that was not asked for.

**Trade-offs:** Faculty cannot self-service their own availability; the Lab Assistant is a manual intermediary. Acceptable at current scale, and reversible — adding faculty login later is an additive migration (new `app_user` row type + linking column on `faculty`), not a redesign, since `faculty` is already a clean, independent entity.

**Consequences:** Documented explicitly as a future improvement in [18-FUTURE-IMPROVEMENTS.md](18-FUTURE-IMPROVEMENTS.md); no code path in the initial system assumes faculty ever authenticate.

---

## ADR-007: Human-Reviewed PDF Import — Never Auto-Publish Extraction

**Context:** PDF extraction (OCR/table-parsing) is not reliably accurate; the official timetable is high-stakes shared data that students and CRs depend on.

**Decision:** `TimetableImport`/`TimetableImportEntry` are separate tables from `Allocation`. Extraction populates import tables only; nothing becomes a real, conflict-checked `Allocation` until Lab Assistant approval, and nothing is visible to students until the containing `ScheduleVersion` is subsequently published.

**Alternatives:** Parse and publish directly; parse, auto-detect conflicts, and only pause for review when a conflict is found (skipping review for "clean" imports).

**Reasons:** The spec explicitly forbids publishing directly after extraction. Requiring review even for apparently-clean imports protects against silent misreads that don't manifest as a scheduling *conflict* (e.g., a lab code OCR'd as the wrong-but-still-valid lab) — a conflict-only review gate would miss exactly these.

**Trade-offs:** An extra manual step for every import, even ones that turn out to need no correction — accepted as the entire point of the requirement.

**Consequences:** Two-stage pipeline: import review (Lab Assistant corrects extracted data) → approval (creates real `APPROVED` allocations against a `DRAFT` `ScheduleVersion`) → publication (Lab Assistant explicitly publishes, making them visible to students). See the state machines in [03-SYSTEM-ARCHITECTURE.md §5](03-SYSTEM-ARCHITECTURE.md).

---

## ADR-008: Custom Scheduling Engine, Not a General-Purpose Solver (e.g. OR-Tools)

**Context:** The scheduling problem is a constraint satisfaction + optimization problem, which general solvers (e.g. Google OR-Tools CP-SAT) are built for; problem size is modest (~15 labs, tens of sessions per generation run).

**Decision:** Hand-written constraint engine + scoring engine + most-constrained-first backtracking search in Java (see [05-SCHEDULING-ENGINE.md](05-SCHEDULING-ENGINE.md)), not a CP-SAT-based solver.

**Alternatives:** Google OR-Tools CP-SAT via a JNI/subprocess bridge or a separate Python microservice.

**Reasons:** At this problem size, a hand-rolled backtracking search with good pruning is both fast enough and dramatically easier to make *explainable* (every rejection and every score traces to a specific, named constraint or scorer) than treating the objective as an opaque solver call. Explainability is a first-class requirement here (PART 28), and a generic solver doesn't produce "Lab C-302 rejected: Cloudera unavailable" without equivalent hand-written explanation logic layered on top anyway — which removes most of the solver's advantage. It also avoids adding a second language runtime or a JNI bridge, keeping the stack to what was specified (PART 1).

**Trade-offs:** No proof of global optimality; the search returns a valid, reasonably good schedule within a configured `maxDepth`/`maxAttempts`/`timeout` budget, or reports partial failure, rather than guaranteeing the theoretically optimal schedule. Worst-case complexity is exponential, not polynomial — documented plainly in [05-SCHEDULING-ENGINE.md](05-SCHEDULING-ENGINE.md), never understated.

**Consequences:** If the problem size grows by orders of magnitude, or true global optimality becomes a real requirement, OR-Tools becomes worth the added complexity — tracked as a concrete comparison-benchmark item in [18-FUTURE-IMPROVEMENTS.md](18-FUTURE-IMPROVEMENTS.md), not asserted as a foregone future step.

---

## ADR-009: Schedule Versioning Instead of In-Place Timetable Updates

**Context:** An official timetable revision must never silently overwrite history; students must only ever see the currently authoritative version.

**Decision:** `AcademicTerm` → `ScheduleVersion` → `Allocation`. A new official timetable revision creates a new `ScheduleVersion` with a recorded reason; the previous `PUBLISHED` version is marked `SUPERSEDED`, never deleted or overwritten. `EXTRA` allocations attach to (and can continue to be added to) the currently published version directly, rather than waiting for the next version cut (see ADR-005/A-11 in [ASSUMPTIONS.md](ASSUMPTIONS.md)).

**Alternatives:** Update `allocation` rows in place for a new timetable revision, keeping a separate audit-log-only trail of what changed.

**Reasons:** The spec requires that an old official timetable never be silently overwritten and that students see only the currently published version. Versioning at the `ScheduleVersion` level (rather than relying solely on `AuditLog` reconstruction) gives the Lab Assistant a direct, queryable way to compare v1 vs v2 of a term's timetable, not just a derived diff.

**Trade-offs:** Slightly more complex queries (must always filter by "current published version for this term") versus in-place update simplicity — accepted, since losing timetable history was explicitly disallowed.

**Consequences:** Every `Allocation` always has a non-null `schedule_version_id` (see [04-DATABASE-DESIGN.md §7](04-DATABASE-DESIGN.md)); publishing a version is a single transaction that flips its status and cascades `APPROVED → PUBLISHED` on its allocations.

---

## ADR-010: FCFS Concurrency Strategy — Provisional, Finalized in Phase 16

**Context:** Two CRs can submit overlapping requests for the same lab/time within milliseconds of each other. A "check availability, then insert" pattern without database-level protection has a race window regardless of how carefully the application code is written.

**Decision (provisional):** the constraint-revalidation-plus-insert must happen inside a single database transaction using either row-level locking (`SELECT ... FOR UPDATE` on the relevant lab/faculty/batch rows, or a serializing query) or a PostgreSQL exclusion constraint over `(lab_id, date, time-range)` using the `btree_gist` extension — final mechanism to be chosen and implemented in Phase 16, backed by the concurrent-request integration test in [11-TESTING-STRATEGY.md](11-TESTING-STRATEGY.md) as the acceptance gate, not asserted as working before that test passes.

**Alternatives:** Application-level (in-JVM) synchronized lock per lab — rejected outright, since it does not survive multiple application instances and is exactly the kind of check-then-insert-without-DB-protection pattern the spec forbids; optimistic locking with retry — viable but adds client-visible retry complexity for what is fundamentally a range-overlap problem, not a simple row-version conflict; plain `SERIALIZABLE` isolation for the whole transaction — simplest to reason about, but may need combining with an explicit lock/exclusion constraint if serialization-failure retry rates prove too high under load.

**Reasons for the exclusion-constraint direction specifically:** allocations conflict based on **overlapping time ranges**, not identical slot IDs — a naive `UNIQUE(lab_id, start_time)` constraint would miss a request for `10:00–12:00` against an existing `09:00–11:00` row. PostgreSQL's `EXCLUDE` constraint with a range type and the `&&` (overlaps) operator can enforce "no two active rows for the same lab with overlapping time ranges" *at the database level*, which is a materially stronger guarantee than any amount of correct-looking application code, and is worth analyzing seriously before committing to a locking-only approach.

**Trade-offs:** to be documented with the actual chosen mechanism's measured behavior once Phase 16 implements and tests it (retry behavior under contention, whether `btree_gist` is available/acceptable in the deployment target, etc.) — not guessed at now.

**Consequences:** This entry will be rewritten (not just appended to) once Phase 16 completes, replacing "provisional" with the actual mechanism, the actual test result, and any trade-offs discovered during implementation.

---

## ADR-011: Audit Log Is Append-Only, Never Mutated

**Context:** PART 40 requires actor/role/action/resourceType/resourceId/timestamp/metadata to be reconstructable after the fact for every consequential action.

**Decision:** `AuditLog` rows are inserted, never updated or deleted, by application code.

**Alternatives:** Allow corrections/redactions of audit entries.

**Reasons:** A mutable log defeats the purpose of an audit trail — any legitimate correction should itself be a *new* audit entry referencing the old one, not an edit to history.

**Trade-offs:** None significant — standard practice for audit trails.

**Consequences:** No `PATCH`/`DELETE` endpoint or repository method exists for `audit_log` anywhere in the application; `metadata` is JSONB specifically because audit payload shape varies per action type and is only ever displayed, never joined against relationally (the one deliberately flexible column in an otherwise strictly normalized schema).

---

## ADR-012: API Base Path — `/api` Without a Version Segment

**Context:** Phase 2 needed a routing convention decided once, up front, so every controller written from here on is consistent (per the phase brief's explicit request to record this decision).

**Decision:** All application endpoints are rooted at `/api` (e.g. `/api/labs`, `/api/allocations`), with no `/v1` version segment. `/actuator/health` remains unversioned and outside `/api`, matching Spring Boot Actuator convention.

**Alternatives:** `/api/v1/...` from day one.

**Reasons:** This is a single-consumer system (one first-party React frontend, built and deployed together with the backend) with no external API contract to preserve across versions - there is no scenario in the current scope where two API versions need to coexist. Introducing `/v1` now would be speculative versioning with no concrete second version ever planned, which the project's own working rules caution against ("don't design for hypothetical future requirements"). If a real breaking-change/versioning need arises later (e.g. a mobile client requiring a slower rollout), a version segment can be introduced at that point for the specific changed resources, rather than every endpoint carrying an unused `/v1` from the start.

**Trade-offs:** A future breaking change would need a deliberate versioning strategy introduced at that time rather than having one already in place. Accepted, since premature versioning has its own cost (habitually bumping a version that never actually changes) and this system's single-consumer nature makes that cost pay for nothing today.

**Consequences:** Every endpoint in [10-API-DOCUMENTATION.md](10-API-DOCUMENTATION.md) is written under `/api/...`; this stays the convention for all future phases unless a concrete versioning need arises, at which point this ADR is revisited (not silently overridden).

---

## ADR-013: Maven Wrapper (not a system-wide Maven install) as the Build Entry Point

**Context:** The backend build needs to be runnable identically by any developer or CI runner without depending on a pre-installed, version-matched Maven.

**Decision:** The committed Maven Wrapper (`mvnw` / `mvnw.cmd` / `.mvn/wrapper/`), generated by Spring Initializr, is the documented build entry point (`./mvnw test`, `./mvnw package`), not a system `mvn` command.

**Reasons:** The wrapper pins an exact Maven version (3.9.16, verified working in this project - see docs/13-DEVELOPER-SETUP.md) per project, so "works on my machine" version drift is not possible; it also means a machine only needs a JDK, not a separately installed and version-matched Maven, to build the project (verified directly in this phase: this environment had no system Maven at all, and the wrapper worked without one).

**Trade-offs:** None material - this is close to universal practice for Spring Boot projects generated via Spring Initializr.

---

## ADR-014: Testcontainers-Backed Integration Tests Run via Failsafe (`mvn verify`), Not Surefire (`mvn test`)

**Context:** The foundation integration test (`LabAllocationBackendApplicationIT`) needs a real PostgreSQL instance (via Testcontainers, per ADR-003's "design against real Postgres behavior" reasoning) and therefore needs a working Docker daemon to run at all.

**Decision:** Testcontainers-backed tests are named with an `*IT` suffix and run only via the `maven-failsafe-plugin` during `mvn verify`; the default `mvn test`/`mvn package` lifecycle (via Surefire) runs only fast, Docker-free `*Test` unit tests.

**Alternatives:** Run all tests, including Testcontainers-backed ones, through Surefire during `mvn test`.

**Reasons:** This is standard Maven convention specifically for this situation, and it was validated directly during this phase: this development environment's Docker Desktop installation has a client/API incompatibility that blocks Testcontainers from connecting (see docs/13-DEVELOPER-SETUP.md Known Limitations) even though the `docker` CLI itself and `docker compose` work correctly. Separating the two meant `mvn test`/`mvn package` (needed to produce the deployable jar for the Docker image, see backend/Dockerfile) succeed cleanly and quickly regardless of Docker/Testcontainers availability, while the Docker-dependent integration test remains correctly written and will run wherever Testcontainers can actually reach a Docker daemon (confirmed as a genuine, environment-specific limitation here, not a flaw in the test itself).

**Trade-offs:** A contributor must remember to run `mvn verify` (not just `mvn test`) to exercise the Testcontainers-backed test locally. Standard practice; documented in docs/13-DEVELOPER-SETUP.md.

---

## ADR-015: JWT Bearer Authentication, Stored in `localStorage`

**Context:** Phase 3 needed an authentication mechanism for three roles and a frontend token-storage strategy; both needed to be chosen deliberately, not defaulted into.

**Decision:** Stateless JWT (HS256), issued by the backend on login, sent as `Authorization: Bearer <token>`, stored in the browser's `localStorage` (not an HttpOnly cookie, not sessionStorage, not memory-only).

**Alternatives considered:**
- *Server-side sessions + cookie*: simpler token-expiry semantics (server can revoke instantly) but reintroduces session state into what is otherwise a stateless, horizontally-scalable API design, and needs CSRF protection back (cookies are ambient/auto-attached).
- *HttpOnly cookie carrying the JWT*: removes the XSS-read risk entirely, at the cost of needing CSRF protection re-enabled and cookie attribute configuration (`Secure`, `SameSite`, domain scoping).
- *In-memory-only token (no persistence)*: eliminates the XSS-at-rest risk (nothing durable for a script to read after the fact) but loses the session on every page refresh, which is poor UX for a college application used across a work session.

**Reasons:** `localStorage` was chosen for this phase specifically for its simplicity (no CSRF token plumbing, no cookie attribute configuration, trivial to attach to every request) appropriate for the project's current scope, while being explicit — in code (`frontend/src/api/tokenStorage.ts`) and here — about the trade-off it makes: an XSS vulnerability anywhere in the frontend's dependency chain could read the token. This is a conscious, bounded risk acceptance for a college project, not an oversight.

**Trade-offs / production hardening path:** An HttpOnly, `Secure`, `SameSite=Strict` cookie removes the XSS-read risk, at the cost of needing CSRF protection re-enabled (CSRF is currently disabled precisely because there is no cookie-based ambient credential — see docs/09-AUTHORIZATION-RBAC.md). If this system were ever deployed for real institutional use with real student/staff data, migrating to the cookie approach would be the first hardening step, tracked in docs/18-FUTURE-IMPROVEMENTS.md.

**Consequences:** `frontend/src/api/client.ts` reads the token from `tokenStorage` on every request; a `401` response (except from `/auth/login` itself) triggers a registered "unauthorized" callback that clears the token and auth state, preventing indefinite use of a stale/expired token without requiring every call site to handle this individually.

---

## ADR-016: No Refresh Tokens in Phase 3

**Context:** JWT access tokens have a fixed expiration (60 minutes by default); the phase brief explicitly asked whether refresh tokens were needed.

**Decision:** No refresh token mechanism. A token simply expires; the user logs in again.

**Alternatives:** A long-lived refresh token (stored more carefully, e.g. HttpOnly cookie) that silently mints new short-lived access tokens.

**Reasons:** Refresh tokens add real complexity (a second token type, its own storage/rotation/revocation strategy, an additional endpoint, additional attack surface if the refresh token itself leaks) that is not justified yet — this is a college-scheduling tool, not a consumer app where 60-minutes-then-relogin is a meaningful UX cost. The phase brief explicitly says to keep initial authentication architecture simple and only add refresh tokens "unless strongly justified" — no such justification exists yet.

**Trade-offs:** Users must re-authenticate every `JWT_EXPIRATION_MINUTES` (default 60). Acceptable for now; revisit if real usage shows this is genuinely disruptive (tracked in docs/18-FUTURE-IMPROVEMENTS.md), at which point this ADR would be superseded, not silently ignored.

---

## ADR-017: Controlled Dev-Only Seed Users, No Public Self-Registration

**Context:** Phase 3 needed some way for accounts to exist to log in with, but the system is explicitly not a public platform (PART 20 of the original spec forbids unrestricted `POST /api/users/register`).

**Decision:** No self-registration endpoint exists anywhere in the API. A `DevUserSeeder` (`@Profile("dev")`, `ApplicationRunner`) creates exactly one demo account per role, idempotently, reading passwords from environment variables with documented non-secret dev-only fallbacks.

**Alternatives:** A public registration endpoint gated by an invite code; a one-off manual SQL insert documented in a README; a Flyway data-seed migration (rejected specifically — migrations are schema history, not environment-conditional data, and a migration has no clean way to be "dev-profile only").

**Reasons:** Public registration is explicitly wrong for a college-controlled system where every account should originate from a real administrative action (Lab Assistant creating a CR, etc. — arriving in Phase 4+). Gating seeding behind `@Profile("dev")` guarantees it can never run with any other active profile, so production never receives predictable credentials, which a Flyway-migration-based seed could not guarantee as cleanly (migrations run in every environment unless separately profiled at the Flyway-locations level, which is more fragile than a Spring `@Profile` check).

**Trade-offs:** Every other environment (test, prod) starts with zero users; test code that needs a user creates one directly via the repository (see `AuthenticationIT`), which is appropriate for tests anyway.

**Consequences:** `docs/13-DEVELOPER-SETUP.md` documents the three demo accounts' emails and default passwords explicitly, since they are non-secret, dev-only, and overridable via environment variables (`DEMO_LAB_ASSISTANT_PASSWORD` etc.).

---

## ADR-018: No `SubjectOffering` Entity

**Context:** PART 10 of the Phase 4 brief explicitly asked whether `Subject` should carry program/stream/year/term context directly, or whether a separate `SubjectOffering` should represent the per-term instance of a subject being taught.

**Decision:** No `SubjectOffering`. `subject` is scoped to a single `academic_year` (pinning it to a stream/program); `subject_faculty_assignment` already carries the per-division/batch/term "who teaches this, when" context.

**Alternatives:** A `SubjectOffering` between `Subject` and `SubjectFacultyAssignment`, representing "BDA, offered in Semester 5 2026-27."

**Reasons:** Everything a `SubjectOffering` would hold (which term, which division/batch) is already on `subject_faculty_assignment`. Introducing a second layer to hold the *same* term-scoping information would duplicate it for no query or integrity benefit — there's no case in the current requirements where a subject is "offered" in a term with no faculty assignment at all yet needs to be queryable as a first-class offering. If that need materializes (e.g. publishing a "subjects available this term" list before faculty are assigned), `SubjectOffering` can be introduced then as an additive migration.

**Trade-offs:** `subject_faculty_assignment` carries slightly more responsibility (both "this subject is offered here" and "this is who teaches it") than a cleanly separated model would. Accepted — the phase brief's own instruction was "only introduce it if it materially improves the domain," and it doesn't yet.

---

## ADR-019: `subject_faculty_assignment` Uniqueness via Two Partial Indexes

**Context:** `batch_id` is nullable on `subject_faculty_assignment` (null = division-level assignment). A naive `UNIQUE(subject_id, division_id, batch_id, academic_term_id)` constraint would not actually prevent duplicate division-level rows, because PostgreSQL treats every `NULL` as distinct from every other `NULL` under a standard unique constraint.

**Decision:** Two partial unique indexes instead of one plain constraint — `uq_sfa_batch_scoped` (`WHERE batch_id IS NOT NULL`) and `uq_sfa_division_scoped` (`WHERE batch_id IS NULL`), both additionally filtered to `AND active` so a deactivated old assignment never blocks a new one from being created in its place.

**Alternatives:** A sentinel "no batch" value (e.g. `batch_id = 0`) instead of `NULL` — rejected, since it would require a fake row in `batch` or special-casing every query that joins on `batch_id`, exactly the kind of hack ADR-005 already rejected for the analogous `Allocation.target_type`/`batch_id` design.

**Reasons:** This is the same NULL-uniqueness pitfall documented for `Allocation` in ADR-005/docs/04-DATABASE-DESIGN.md, encountered again here and solved the same way — a general pattern worth naming explicitly since it will likely recur wherever a nullable "scope narrower than the parent" column needs uniqueness.

**Consequences:** Same pattern is reused for `cr_assignment`'s "one active assignment per division per term" / "one active assignment per user per term" rules (which don't have the nullable-column issue, but do need two independent partial-unique constraints for two independent invariants over the same table).

---

## ADR-020: Faculty Assignment Resolution Order — Batch-Exact, Then Division Fallback, Never Guessed

**Context:** PART 13 of the phase brief asks how the future scheduling engine will determine "which faculty teaches this session" when both a batch-specific and a division-level assignment could theoretically apply.

**Decision:** `FacultyAssignmentResolutionService` tries the exact batch-level assignment first; only if none exists does it fall back to the division-level (`batch_id IS NULL`) assignment. If neither exists, it throws a clear "not found" error rather than guessing or picking arbitrarily among candidates.

**Reasons:** This mirrors the intuitive real-world rule ("the specific assignment for this batch overrides the general one for the whole division") and, critically, ambiguity between *two* batch-level or *two* division-level assignments is structurally impossible by the time resolution runs — ADR-019's partial unique indexes guarantee at most one active row per scope. So resolution never needs to pick between "equally valid" options; it only ever needs to pick between "the more specific option" and "the more general option," which has one clearly correct answer.

**Trade-offs:** None significant — this is the natural consequence of the write-time uniqueness guarantees, not an independent design risk.

---

## ADR-021: Explicit `LabSoftware`/`LabEquipment` Association Entities, Not `@ManyToMany`

**Context:** A lab has many software packages and a software package is installed in many labs (and likewise for equipment) — the textbook case for a JPA `@ManyToMany` join table.

**Decision:** Explicit entities (`LabSoftware`, `LabEquipment`) with their own primary key, rather than an implicit `@ManyToMany` join table.

**Alternatives:** Plain `@ManyToMany` between `Lab` and `Software`/`Equipment`.

**Reasons:** The association needs real metadata beyond "this pair exists" — `lab_software.installed_version` (nullable, per-installation) and `lab_equipment.quantity` (required, "10 routers" is a genuinely different fact than "1 router"). A plain `@ManyToMany` join table cannot hold either without being converted to a real entity later — which would then require a migration to add a surrogate key and move existing join rows across. Building it as an explicit entity from day one avoids that migration entirely, at essentially zero extra cost now.

**Trade-offs:** Slightly more boilerplate (a real entity + repository per association instead of a `Set<Software>` on `Lab`) — accepted, since the metadata requirement is real, not speculative, per the phase brief's own examples.

---

## ADR-022: Lab Location as Flat `wing`/`floor`/`roomNumber`, Not a Campus Hierarchy

**Context:** The college currently has one building with three wings (B, C, D); a "proper" location model could be a full `Campus → Building → Wing → Floor → Room` hierarchy.

**Decision:** Three plain string columns on `Lab` — `wing`, `floor`, `room_number` — no separate location entities.

**Alternatives:** A normalized location hierarchy with its own tables (mirroring the `Program → Stream → AcademicYear` pattern from Phase 4).

**Reasons:** The academic hierarchy in Phase 4 was normalized because *program and stream have their own independent data* (duration, active status, further children) that genuinely benefits from being real rows. Wings, floors, and room numbers have no such independent data today — nothing else references "Wing C" as an entity, and there's exactly one campus, one building. A location hierarchy here would be structure for structure's sake, not because a concrete requirement needs it (the phase brief itself explicitly calls this a trade-off to document, not a default to reach for).

**Trade-offs:** If the institution later adds a second building or campus, `wing` alone would become ambiguous ("Wing C" of which building?) and this would need revisiting — genuinely deferred, not forgotten (tracked in docs/18-FUTURE-IMPROVEMENTS.md).

---

## ADR-023: `LabUnavailability` vs `Lab.active` — Two Deliberately Different Mechanisms

**Context:** A lab can be unusable for two structurally different reasons: permanently retired/repurposed, or temporarily closed for a dated reason (maintenance, an event).

**Decision:** `Lab.active` (boolean, permanent) and `LabUnavailability` (dated interval rows, temporary) are two separate mechanisms, never conflated.

**Reasons:** They have different query needs (a permanently inactive lab should never appear as a candidate anywhere, full stop; a temporarily unavailable lab is a normal candidate outside its unavailability window) and different lifecycles (a `LabUnavailability` row is created and later becomes irrelevant once its interval passes; `active` is toggled rarely and deliberately). Conflating them — e.g. using `active=false` for a week-long maintenance closure — would either require constant flipping of a flag that's supposed to represent a stable state, or would make "is this lab gone for good or just closed until Friday" unanswerable from the data alone.

**Trade-offs:** None significant — this is a standard state-vs-event distinction, applied consistently with how `Allocation` status vs. cancellation reason works elsewhere in this project's design.

---

## ADR-024: `LabUnavailability` Hard-Deleted on Removal — No Soft-Cancel Status

**Context:** Every other historically-significant entity in this project (Program, Division, Faculty, CrAssignment, ...) is deactivated, never hard-deleted. `LabUnavailability` removal (PART 30 of the phase brief: "cancel/remove future unavailability where appropriate") needed the same decision made deliberately, not by default.

**Decision:** `DELETE /api/labs/{labId}/unavailability/{id}` physically removes the row. No `active`/`status` column exists on `lab_unavailability`.

**Reasons:** The soft-deactivation principle elsewhere in this project exists because those entities are *referenced* by other data whose meaning would be corrupted by disappearing rows (an `Allocation` pointing at a deleted `Division` would be nonsensical). Nothing references `LabUnavailability` yet — no `Allocation` exists until Phase 9+ — so there is no historical evidence a hard delete would destroy. Adding a status column now for a "cancelled" state with zero current consumers would be exactly the kind of speculative complexity this project's own working rules warn against.

**Trade-offs:** If a future phase's allocation-explanation feature ever needs to say "this session was scheduled despite an unavailability window that was later cancelled," this decision would need revisiting — noted explicitly (docs/04-DATABASE-DESIGN.md), not silently painted over.

---

## ADR-025: Subject Requirements Scoped to `Subject`, Not `SubjectOffering`

**Context:** Phase 6 needed to decide where software/equipment/lab-type requirements attach. `Subject` already exists as a year-scoped catalog entity (ADR-018 rejected a separate `SubjectOffering`); requirements could instead have been attached per-term, per-division, or per-faculty-assignment.

**Decision:** Requirements (`SubjectSoftwareRequirement`, `SubjectEquipmentRequirement`, `required_lab_type_id`/`preferred_lab_type_id`) all hang directly off `Subject`.

**Alternatives:** Per-term requirements (a `SubjectOffering`-like scoping) — rejected for the same reason ADR-018 rejected `SubjectOffering` generally: nothing in the domain suggests BDA needs Cloudera in one term but not another; the requirement is a property of the subject's curriculum, not of a particular term's delivery.

**Reasons:** A subject's technical requirements are a curriculum fact (what BDA teaches), not a scheduling fact (when/who teaches it). Keeping requirements on `Subject` keeps them visible to every term's allocation run without duplication, and matches how `Subject` is already the stable anchor for `SubjectFacultyAssignment`.

**Trade-offs:** If a future curriculum revision genuinely changes a subject's tooling mid-life (e.g. BDA moves from Cloudera to a different platform), the change applies retroactively to all terms rather than being versioned — acceptable for now since no versioning concept exists elsewhere in the requirement/subject model either.

---

## ADR-026: No Boolean `required` Column on Requirement Rows

**Context:** A join-table design for requirements could have used a single table per capability type with a `required BOOLEAN` column (true = hard requirement, false = nice-to-have), rather than the current model where every `SubjectSoftwareRequirement`/`SubjectEquipmentRequirement` row is unconditionally a hard (ALL-semantics) requirement.

**Decision:** No boolean flag exists. Every row in `subject_software_requirement`/`subject_equipment_requirement` is a hard requirement by construction; "nice-to-have" software/equipment (the Phase 7's "Additional Environment Fit" scoring factor, docs/07-ALLOCATION-SCORING.md) is explicitly out of scope for Phase 6 and left unmodeled rather than half-modeled with an unused flag.

**Reasons:** The phase brief's demo scenario and hard-constraint model (HC-08/HC-09) only ever needed ALL-required semantics; adding a flag with no consumer would be exactly the kind of speculative column this project's working rules warn against ("no half-finished implementations"). If a future phase needs soft/preferred capability rows, it can add the column then, informed by real requirements rather than a guess made now.

**Trade-offs:** A future "preferred software" feature will need a migration to add the column (or a parallel table) rather than just flipping data — an acceptable cost for not carrying dead schema now.

---

## ADR-027: Nullable FK Columns for Lab-Type Requirement, Not a Join Table

**Context:** `required_lab_type_id`/`preferred_lab_type_id` needed a storage shape. A join-table (mirroring `SubjectSoftwareRequirement`) was one option; two nullable FK columns directly on `subject` was the other.

**Decision:** Two nullable `BIGINT` FK columns on `subject`, each referencing `lab_type`, with a `CHECK` constraint (`chk_subject_lab_type_pref`) enforcing they are never both non-null.

**Reasons:** Unlike software/equipment (where a subject can need zero, one, or many), lab-type requirement is genuinely single-valued per subject per role (required XOR preferred XOR neither) — the same reasoning already applied to `Lab.labType` itself (a lab has exactly one type, not a set). A join table would allow a nonsensical multi-row state (two "required" rows) that the application would then have to defensively guard against; a nullable FK column makes that state structurally unrepresentable.

**Trade-offs:** None significant — this mirrors the existing `Lab.labType` pattern exactly, so it introduces no new modeling idiom into the codebase.

---

## ADR-028: Required-vs-Preferred Lab Type Mutual Exclusivity Enforced at Both Layers

**Context:** "A subject cannot need a lab type both as a hard requirement and a soft preference at the same time" needed enforcement somewhere.

**Decision:** Enforced twice: `Subject.setLabTypeRequirement()` throws `ApiException("INVALID_LAB_TYPE_PREFERENCE", 400, ...)` before either field is set, and the database carries `CHECK (required_lab_type_id IS NULL OR preferred_lab_type_id IS NULL)` as defense-in-depth.

**Reasons:** Consistent with this project's standing pattern (docs/04-DATABASE-DESIGN.md) of pairing application-level validation with a database constraint wherever an invalid state would otherwise be silently persistable by a future code path that bypasses the service layer (a bulk import, a direct SQL fix, a future migration script).

**Trade-offs:** None — this is pure redundancy in the safe direction, at negligible cost (one `CHECK` constraint).

---

## ADR-029: Equipment Requirements Carry `requiredQuantity`, Software Requirements Do Not

**Context:** Software requirements are inherently a presence check (Cloudera is either installed or it isn't); equipment is physically countable (a subject might need 30 oscilloscopes for a batch of 60 working in pairs).

**Decision:** `SubjectEquipmentRequirement.requiredQuantity` (`INT NOT NULL DEFAULT 1`, `CHECK (required_quantity > 0)`) exists; `SubjectSoftwareRequirement` has no analogous column.

**Reasons:** Modeling a `requiredQuantity` on software would be meaningless (software isn't consumed per-seat in this system's model — a lab either has a license installed or not) and would invite confusion about what the number means. Keeping the two requirement tables structurally different, rather than forcing a shared shape, reflects that they represent genuinely different kinds of fact.

**Trade-offs:** The two requirement services/DTOs can't share a fully generic implementation — accepted, consistent with this project's general preference for explicit, slightly-duplicated code over a forced abstraction (project working rules: "three similar lines is better than a premature abstraction").

---

## ADR-030: Software/Equipment Version Matching Deferred — Identity/Code Only in Phase 6

**Context:** The phase brief raised (as a concern to document, not solve) whether a requirement should ever pin a specific software *version* (e.g. "Cloudera >= 6.3") rather than just its presence.

**Decision:** Phase 6 matches only by `Software`/`Equipment` identity (the FK), never by version. `LabSoftware.installedVersion` (Phase 5) exists and is stored, but no requirement-side field compares against it.

**Reasons:** No real scenario in this project's scope (BDA/Cloudera, the seeded demo data) needs version-level discrimination, and introducing a version-comparison concept (semantic versioning? exact string match? minimum-version?) would be speculative complexity with no consumer — the same reasoning as ADR-026's rejected boolean flag.

**Trade-offs:** If a future phase's constraint engine needs "lab has Cloudera >= 6.3," this will require a new column/comparison operator on the requirement row — a real, acknowledged gap, tracked here and in docs/06-CONSTRAINTS.md rather than silently absent.

---

## ADR-031: Faculty Availability Is Term-Scoped Mandatorily — Supersedes the Phase 1 Draft

**Context:** docs/04-DATABASE-DESIGN.md's Phase 1 draft modeled `faculty_availability.academic_term_id` as nullable, with a null value meaning "applies every term" (docs/ASSUMPTIONS.md A-15) — chosen at the time to avoid forcing term-by-term re-entry for the common case. Phase 7's brief explicitly asked this to be re-reviewed, and recommended making it mandatory instead.

**Decision:** `faculty_availability.academic_term_id` is `NOT NULL`. Every availability row belongs to exactly one term; there is no "applies forever" row.

**Alternatives:** The original nullable design was seriously considered (it was already written into the Phase 1 plan) — rejected because a faculty's real availability genuinely changes semester to semester (the phase brief's own example: Monday mornings free in one semester, Monday afternoons in the next), and a nullable "applies every term" row would silently misrepresent that the moment it happened, with no structural signal that the data had gone stale.

**Reasons:** Term-scoping matches how this project already treats every other term-relative fact (`SubjectFacultyAssignment`, `CrAssignment` — both already term-scoped, Phase 4) — faculty availability being the one term-relative concept modeled as term-agnostic would have been the inconsistent choice, not the mandatory one.

**Trade-offs:** A Lab Assistant must re-enter (or the future UI must offer to copy) availability for each new term, rather than it persisting automatically — a real data-entry cost, accepted because silently-stale "forever" availability is a worse failure mode for a hard constraint's source data than an explicit re-entry step. See A-32 in ASSUMPTIONS.md (A-15 marked superseded, not deleted, per this project's standing practice of recording changed decisions rather than erasing them).

---

## ADR-032: Faculty Availability Overlap — Application Validation Only, Reject Rather Than Merge

**Context:** Two related questions needed a decision: (1) how to prevent overlapping availability rows for the same faculty/term/day, and (2) what to do when a Lab Assistant's input actually overlaps an existing row.

**Decision (overlap prevention — Option A over Option B):** Application-level validation only (`FacultyAvailabilityService` queries existing active rows and checks for overlap before insert/update); no PostgreSQL exclusion constraint.
**Decision (overlap handling):** Reject the write with `FACULTY_AVAILABILITY_OVERLAP` (409). Never silently merge two overlapping inputs into one combined row.

**Alternatives considered for overlap prevention:** A PostgreSQL `EXCLUDE USING gist` constraint over a generated range type, mirroring how a genuinely recurring-weekly range might be modeled with a custom range type or a synthetic `(day_of_week, start_time, end_time)` composite range. Rejected: PostgreSQL has no built-in recurring-weekly range type (only `tsrange`/`tstzrange` for actual timestamps), so this would require either a custom range type or an application-computed helper column purely to feed the exclusion constraint — real, non-obvious complexity for data that is low-volume and administratively mutated (a handful of Lab Assistant edits per faculty per term, not a high-concurrency write path). The `chk_faculty_availability_interval` `CHECK (end_time > start_time)` constraint remains a genuine, non-bypassable database guarantee; only the *cross-row* overlap guarantee is application-only.

**Alternatives considered for overlap handling:** Silently merging `09:00-12:00` + `11:00-14:00` into a single `09:00-14:00` row. Rejected per the phase brief's explicit instruction — merging hides administrative intent (did the Lab Assistant mean to extend the window, or make a data-entry mistake?) and complicates the audit trail of what was actually entered, for a convenience that a clear rejection message achieves just as well (the Lab Assistant can delete/re-enter deliberately).

**Trade-offs:** Should this data ever become high-concurrency (unlikely for administratively-mutated availability), the application-only overlap check has the standard TOCTOU race-condition exposure of a read-then-write pattern without a database-level guarantee — acceptable at this project's actual scale and mutation pattern, noted here rather than silently assumed safe.

---

## ADR-033: Faculty Availability Uses Soft Deactivation (`active` flag), Not Physical Delete

**Context:** `LabUnavailability` (Phase 5, ADR-024) set a hard-delete precedent for a dated, one-off administrative record with no ongoing recurrence and no downstream consumer. `FacultyAvailability` needed its own explicit decision (PART 25 of the phase brief), not an automatic copy of that precedent.

**Decision:** `FacultyAvailability.active` (boolean, default `true`); `DELETE /api/faculty/{id}/availability/{availabilityId}` sets `active=false` rather than removing the row.

**Reasons:** A `FacultyAvailability` row represents an enduring weekly template (a faculty's standing Monday-morning slot), not a one-off dated event like a maintenance window — it is architecturally closer to `Faculty`, `Software`, or `Program` (all soft-deactivated, this project's dominant pattern) than to `LabUnavailability`. Overlap validation and availability evaluation both already need to filter on "active" rows regardless (to distinguish current from superseded windows), so the column exists as a natural query filter rather than purely as an audit mechanism — soft deactivation was effectively "free" given that need, unlike `LabUnavailability` where no such filtering need existed.

**Trade-offs:** None significant — matches the project's own more common pattern; the one place this diverges from `LabUnavailability` is deliberate and explained by the entities' different natures (recurring template vs. one-off event), not an inconsistency.

---

## ADR-034: Faculty Availability Read Access Restricted to LAB_ASSISTANT — Narrower Than Phase 5/6

**Context:** Every prior read-heavy domain in this project (Labs, Software/Equipment catalogs, Subject Requirements) opens `GET` to any authenticated role, reserving `LAB_ASSISTANT` for mutations only. The Phase 7 brief explicitly raised the question of whether that same open-read pattern makes sense for faculty availability, and recommended restricting it.

**Decision:** `/api/faculty/{facultyId}/availability*` (including the `/check` preview endpoint) requires `LAB_ASSISTANT` for **both** read and write. CR and STUDENT receive `403` on every method, not just mutations.

**Alternatives:** Following the established open-read convention (CR/STUDENT can `GET`) — rejected because, unlike Lab capacity/software (genuinely useful for a CR to see when planning an extra-lab request) or subject requirements (informative context), raw faculty-availability rows have no legitimate consumer outside the Lab Assistant and the future constraint engine (which will call `FacultyAvailabilityService` directly, never through this REST surface) — exposing it to CR/STUDENT would be surface area with no real use case behind it, at least for now.

**Reasons:** This project's default posture on new surface area is to open only what has a demonstrated need (the same reasoning behind not adding speculative columns, ADR-026/ADR-030) — read access is not free just because other domains happened to open it, each domain's access model is a decision, not an inherited default.

**Trade-offs:** If a future phase determines CRs genuinely need to see faculty availability (e.g. to explain why an extra-lab request failed), this endpoint would need to be reopened for CR reads specifically — a straightforward, backward-compatible change (loosening `@PreAuthorize`, not a breaking one) tracked here rather than pre-emptively built now on a guess.
