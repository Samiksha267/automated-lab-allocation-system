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

---

## ADR-035: Phase 8 Roadmap Clarification — Scheduling Domain vs. Constraint Engine

**Context:** A single Phase-1-era reference in docs/03-SYSTEM-ARCHITECTURE.md tagged the entire Scheduling Engine document "(Phase 8)" without distinguishing persisted schema from algorithm, and was never revisited across Phases 4-7 even as every one of those phases' own documentation independently and consistently referred to "Phase 9" as the Constraint Engine. This produced a genuine, if narrow, roadmap inconsistency, surfaced during Phase 8's own pre-phase documentation review.

**Decision:** Phase 8 is formally **Scheduling Domain & Allocation Persistence Foundation** — it builds the `Allocation`/`ScheduleVersion` tables and the pure `SchedulingRequest`/`SchedulingContext`/`CandidateAllocation`/`ConstraintResult`/`ConstraintViolation` object *shapes*. Phase 9 remains the Constraint Engine. The full corrected sequence (Phase 8-16) is recorded in docs/03-SYSTEM-ARCHITECTURE.md §16.

**Reasons:** Phase 9's conflict constraints (HC-01/02/04/05/06) need real, queryable allocation data to evaluate against — a constraint engine cannot meaningfully exist before something has created the table it queries. This ordering is not a preference, it is a hard dependency: "whether a candidate is valid" (Phase 9) cannot be answered before "what a candidate/allocation *is*" (Phase 8) has a persisted shape to check against.

**Trade-offs:** None to the actual business requirements (docs/02-REQUIREMENTS.md) — every FR/HC this project has already specified is unaffected; this is purely a correction to a stale internal cross-reference, not a scope or requirement change.

---

## ADR-036: Allocation Represented as `LocalDate` + `LocalTime`, Not `TIMESTAMPTZ`

**Context:** `faculty_availability` (Phase 7) and `lab_unavailability` (Phase 5) took two different approaches to time representation — the former recurring-weekly `LocalTime`, the latter full-precision `TIMESTAMPTZ`/`Instant`. `Allocation` needed its own explicit decision, not an automatic inheritance of either.

**Decision:** `Allocation.allocationDate`/`startTime`/`endTime` are `LocalDate`/`LocalTime` (DB `DATE`/`TIME`), matching `faculty_availability`'s shape, not `lab_unavailability`'s.

**Reasons:** An allocation is inherently a single local-college-day session with no timezone ambiguity relevant to *scheduling* it (the Lab Assistant/CR always thinks and enters times in local wall-clock terms: "Monday 9 to 11") - overnight sessions are explicitly out of scope (PART 10 of the Phase 8 brief). `LabUnavailability`'s `TIMESTAMPTZ` precision exists for a different reason (a maintenance window that might span a specific real-world instant, potentially useful for cross-referencing external system timestamps) that doesn't apply to a teaching session. Using `LocalDate`/`LocalTime` also means `TimeIntervalUtils` (Phase 7) is directly reusable for `Allocation`-vs-`Allocation` overlap checks (HC-01/02/04/05) with zero conversion.

**Trade-offs:** Comparing an `Allocation` against `LabUnavailability` (HC-06) now requires an explicit bridge, since the two use different temporal types — see ADR-037. This was judged the better trade-off than forcing `Allocation` into `TIMESTAMPTZ` purely to match one single-use-case constraint's convenience, at the cost of every other constraint (HC-01/02/04/05, the overwhelming majority) needing a needless conversion instead.

---

## ADR-037: Configurable College Timezone + a Central `LocalDate`/`LocalTime` ↔ `Instant` Bridge

**Context:** ADR-036's choice creates a real type boundary: `Allocation` (`LocalDate`+`LocalTime`) vs. `LabUnavailability` (`TIMESTAMPTZ`/`Instant`) that HC-06 (Phase 9) must cross. Left unaddressed, this risks either being solved ad hoc inside `LabAvailabilityConstraint` alone, or worse, solved slightly differently by some future second consumer of the same conversion.

**Decision:** `SchedulingTimeMapper` (`com.college.laballocation.scheduling`), a single Spring component providing `toInstant(LocalDate, LocalTime)` and `toInstantRange(LocalDate, LocalTime, LocalTime)`, backed by a configurable `app.college.time-zone` property (env `COLLEGE_TIME_ZONE`, default `Asia/Kolkata`) resolved to a real `java.time.ZoneId`.

**Alternatives:** A hardcoded manual offset (e.g. "always add 5 hours 30 minutes") was explicitly rejected (PART 47 of the Phase 8 brief) — it would silently be wrong the day any DST rule ever applied to the deployment zone, and more subtly, it hides the timezone decision as an unlabeled magic number rather than a named, greppable configuration property. Using `ZoneId`/`Instant` lets the JDK's own, correct zone-rule database handle this, not hand-rolled arithmetic.

**Reasons:** Solving this once, centrally, and now (rather than leaving it to Phase 9) means `LabAvailabilityConstraint` will only ever need to call `SchedulingTimeMapper`, not invent the conversion itself — consistent with this project's general preference for shared utilities over near-duplicate implementations (the same reasoning behind introducing `TimeIntervalUtils` in Phase 7 ahead of having three constraints that would each need overlap logic).

**Trade-offs:** None significant — a college realistically operates in one timezone; if a future deployment genuinely spans multiple timezones, this design would need revisiting, but that is not a real requirement of this project today.

---

## ADR-038: `CandidateAllocation` Is a Transient Domain Object, Never a JPA Entity

**Context:** `Allocation` (persisted) and `CandidateAllocation` (the proposed, unpersisted possibility a future constraint engine evaluates) needed a clear, enforced boundary — PART 22 of the Phase 8 brief explicitly warns against conflating them.

**Decision:** `CandidateAllocation` is a plain Java `record` with no JPA annotations, holding a `SchedulingContext` and a candidate `labId`/`labCode` — never persisted, never given an `@Entity` mapping.

**Reasons:** Most candidates evaluated during a single scheduling run are rejected (docs/05-SCHEDULING-ENGINE.md's validation pipeline explicitly removes invalid candidates before scoring) — persisting every one, even transiently, would be pure waste and would blur the meaning of "this was actually booked" (an `Allocation` row) with "this was considered and discarded" (a `CandidateAllocation`), a distinction this project's NFR-08 (domain objects decoupled from JPA/HTTP) already requires generally and this decision makes concrete for scheduling specifically.

**Trade-offs:** None — this is the standard, expected shape for a CSP's candidate objects; the alternative (a `@Entity` "candidate" table) has no precedent anywhere else in this project's design and no real motivating use case.

---

## ADR-039: No Raw Allocation Creation API Before the Constraint Engine Exists

**Context:** `Allocation`/`ScheduleVersion` now have real repositories and services (Phase 8) — the question was whether to expose any create endpoint now, versus deferring until Phase 9/15/19 actually validate what gets created.

**Decision:** No controller exists for either entity. `POST /api/allocations` and `GET /api/allocations` are both confirmed `404` against the live stack. `Allocation` rows are only ever created via direct repository calls in tests and the dev seeder during this phase.

**Reasons:** An `Allocation` row is only ever supposed to be created once it is already known valid (docs/03-SYSTEM-ARCHITECTURE.md §5) — before Phase 9's constraint engine exists, there is no code capable of deciding "is this candidate actually valid," so any creation endpoint opened now would necessarily accept unvalidated bookings, directly contradicting the project's central premise (this is a constraint-based system, not a CRUD app). Building the endpoint now and "just not linking it from the UI" is not a real safeguard - a real client could still call it.

**Trade-offs:** Test coverage for `Allocation` construction and lifecycle transitions must go through repositories/entity factories directly rather than through HTTP in this phase - an acceptable, deliberate limitation until Phase 15 (extra-lab booking) and Phase 19 (PDF import approval) build the two real creation paths on top of Phase 9's validated constraint engine.

---

## ADR-040: At-Most-One-Published-ScheduleVersion Enforced via a Partial Unique Index

**Context:** ADR-009 (Phase 1) already established that exactly one `PUBLISHED` `ScheduleVersion` should exist per term at a time, but left the enforcement mechanism unspecified pending real implementation.

**Decision:** `ScheduleVersionService.publish()` actively supersedes the term's previous `PUBLISHED` version in the same call, **and** a partial unique index (`uq_schedule_version_one_published_per_term ON schedule_version (academic_term_id) WHERE status = 'PUBLISHED'`) makes the invalid two-published state structurally impossible at the database level.

**Alternatives:** Relying on application code alone (the service always supersedes correctly) was rejected per this project's standing defense-in-depth pattern (see HC design generally, and `chk_subject_lab_type_pref`/`chk_allocation_target_invariant` elsewhere) — a future bug, a bulk script, or a direct SQL fix could otherwise silently leave two `PUBLISHED` versions for one term, an inconsistency the rest of the system (student timetable view, Phase 18+) has no way to detect or recover from gracefully.

**Reasons:** A partial unique index is the cheapest possible database-level guarantee for this specific invariant — it costs one small index and is verified directly: a second `PUBLISHED` row insert for the same term was rejected by Postgres in a live transactional test (docs/11-TESTING-STRATEGY.md).

**Trade-offs:** None significant — this mirrors the exact pattern already used for `subject`'s required/preferred lab-type mutual exclusivity (ADR-028) and `cr_assignment`'s at-most-one-active-per-division/user partial indexes (Phase 4).

---

## ADR-041: One `SchedulingConstraint` Class Per Hard Constraint, Spring-Discovered

**Context:** HC-01 through HC-12 needed an implementation strategy. A single method (or class) branching over all twelve rules was one option; twelve independently-testable classes implementing a shared interface was the other.

**Decision:** `SchedulingConstraint` (interface: `id()`, `evaluate(context, candidate)`) with one `@Component` implementation per HC. `ConstraintEngine` receives `List<SchedulingConstraint>` via Spring constructor injection (auto-discovered, no manual wiring) and sorts it into a fixed evaluation order.

**Alternatives:** A single `AllocationValidator.validate(...)` method with twelve inline checks was rejected — it would couple unrelated rules in one function (a capacity-logic change risking a software-logic regression in the same method), make isolated unit testing of one rule impossible without exercising all twelve, and require editing a shared method (merge-conflict risk) every time a future constraint is added.

**Reasons:** Twelve independent classes mean twelve independent test classes (docs/11-TESTING-STRATEGY.md), and Spring's auto-discovery means adding HC-13 later is purely additive - a new `@Component`, zero changes to `ConstraintEngine` itself.

**Trade-offs:** Slightly more boilerplate (twelve small files vs. one) - accepted; this project consistently favors explicit, separately-testable units over compact-but-coupled code (the same reasoning behind `LabSoftware`/`LabEquipment` as explicit entities rather than implicit joins, ADR-021).

---

## ADR-042: Evaluate All Constraints, Never Fail-Fast

**Context:** `ConstraintEngine.evaluate(...)` could stop at the first failing constraint (cheaper) or run every constraint regardless of earlier failures (more expensive per-candidate, more informative).

**Decision:** Every registered constraint always runs; `ConstraintEvaluation` collects every result and every violation, not just the first.

**Reasons:** A candidate can be simultaneously invalid for unrelated reasons (wrong capacity *and* missing software *and* faculty unavailable) - reporting only the first discovered reason is worse for the Lab Assistant/CR trying to understand why a request failed, and would require re-running the engine to discover the *next* reason after fixing the first. This also directly feeds Phase 12 (full explanation) and Phase 13 (alternative ranking) without a second evaluation pass. Verified with a dedicated multi-failure test (a fixture failing three constraints at once, asserting all three violations are present) and live in Docker.

**Trade-offs:** Marginally more work per candidate (twelve checks always run, not an early subset) - accepted as negligible at this project's scale (~15 labs, one candidate evaluated at a time in Phase 9; Phase 25 will benchmark formally if this ever needs revisiting for Phase 10's candidate-generation loop).

---

## ADR-043: `ConstraintResult` Gains a Third Outcome — `NOT_APPLICABLE`, Distinct from `PASS`

**Context:** HC-11 (CR Authorization) is meaningless for a `SchedulingRequest` with no CR actor at all (e.g. future automated REGULAR generation, Phase 14) or for a `LAB_ASSISTANT` actor - forcing it to report `PASS` in that case would be a category error: the rule was never actually evaluated, not satisfied.

**Decision:** `ConstraintOutcome` (`PASS`/`FAIL`/`NOT_APPLICABLE`) replaces Phase 8's plain `boolean passed` field on `ConstraintResult`. `NOT_APPLICABLE` counts as non-failing for overall `ConstraintEvaluation.valid()`, but is reported distinctly in `results()`.

**Alternatives:** Reporting `NOT_APPLICABLE` cases as `PASS` was rejected - it would silently conflate "this candidate is authorized" with "authorization doesn't apply here," a distinction future explainability (Phase 12, "why was this valid") may genuinely need to render correctly (e.g. never claiming "CR authorization passed" for a request that had no CR actor at all).

**Reasons:** No other HC currently needs a third state (HC-04's vacuous DIVISION-candidate pass and HC-08/09's empty-requirement pass are both genuinely, truthfully `PASS` - the rule *is* satisfied, trivially - so they deliberately do not use `NOT_APPLICABLE`, keeping its use narrow and meaningful rather than a catch-all).

**Trade-offs:** A breaking change to Phase 8's `ConstraintResult` shape - accepted since nothing outside `ConstraintResult` itself consumed the old shape yet (verified by search before changing it), making this a clean evolution, not a real breaking change to any consumer.

---

## ADR-044: `SchedulingActor` — a Domain-Neutral Actor Field on `SchedulingRequest`, Not an HTTP/Security Dependency

**Context:** HC-11 needs to know who originated a request (to decide whether CR-ownership applies at all). `SchedulingRequest`/`SchedulingContext` are deliberately JPA/HTTP-free domain objects (NFR-08); pulling in Spring Security's `Authentication`/`SecurityContext` would violate that.

**Decision:** `SchedulingActor{userId: Long, role: UserRole}`, a plain record, added as a nullable field on `SchedulingRequest` - resolved by the caller *before* the request is constructed, the same way `facultyId` is already resolved upstream (Phase 8). Reuses `UserRole` (already a plain, framework-free enum) rather than inventing a duplicate role type.

**Alternatives:** Threading a Spring Security `Authentication` object into the scheduling domain was rejected outright - it would make every future consumer of `SchedulingRequest` (Phase 10-19, and any future non-HTTP caller such as a batch import job) carry a web-framework dependency it doesn't need. A separate `SchedulingAuthorizationContext` service call from inside the constraint was also considered and rejected - it would make the constraint responsible for *resolving* identity, not just *checking* it, mixing two different concerns.

**Reasons:** Consistent with the project's existing "resolve ambiguity upstream, hand the constraint engine an unambiguous request" architecture (docs/05-SCHEDULING-ENGINE.md's faculty-resolution note, Phase 8) - actor resolution is exactly the same kind of upstream concern as faculty resolution.

**Trade-offs:** `SchedulingRequest`'s constructor signature changed (test call-sites updated) - a one-time, contained cost, accepted for the same reason ADR-043 accepted its own breaking change.

---

## ADR-045: `CrOwnershipService.getCurrentAssignment` (Non-Throwing), Not `requireOwnsDivision`, Inside `CrAuthorizationConstraint`

**Context:** A real bug, found via manual Docker verification: the first `CrAuthorizationConstraint` called `CrOwnershipService.requireOwnsDivision(...)` and caught its thrown `ApiException` subtypes to build a `FAIL` result. Because `requireOwnsDivision` is itself `@Transactional`, the exception crossing that method's boundary marked the *shared surrounding* transaction rollback-only before the constraint's catch block ran - the enclosing transaction later failed with `UnexpectedRollbackException` even though the constraint itself had already produced a correct result.

**Decision:** `CrAuthorizationConstraint` calls `CrOwnershipService.getCurrentAssignment(userId)` (`Optional<CrAssignment>`, never throws) and compares the resolved division directly - no exception ever crosses a `@Transactional` boundary for this expected-failure path.

**Alternatives:** Keeping `requireOwnsDivision` and instead wrapping the constraint's call in `TransactionTemplate.execute(...)` with `PROPAGATION_REQUIRES_NEW` (a fresh, disposable transaction the exception could freely poison without affecting the caller) was considered and rejected - it would add real transactional-boundary complexity to a single read-only check, purely to keep using a throwing API that already has a non-throwing equivalent doing the same underlying query.

**Reasons:** This is the direct, architectural fix, not a workaround: PART 2 of this phase's brief already required that "constraints do not throw normal business-validation exceptions for expected invalid candidates" - the original implementation violated that rule in spirit even though it looked compliant (catching the exception locally), and this manual-verification finding is what surfaced the violation concretely. The general lesson (documented in docs/14-INTERVIEW-PREPARATION.md Problem 4): catching an exception thrown by another `@Transactional`-advised bean method does not undo the transactional marker Spring's AOP layer already set at the point the exception left that method.

**Trade-offs:** None - `getCurrentAssignment` already existed for `GET /api/cr-assignments/me` (Phase 4), so this is pure reuse, not new surface area.

---

## ADR-046: `CandidateGenerator` Generates From Every Lab — No Capacity/Software/Type Prefilter

**Context:** The Phase 1 sketch of the scheduling pipeline (docs/05-SCHEDULING-ENGINE.md) originally described a "Generate Candidate Labs" step that prefiltered by capacity, required software, and lab type *before* hard-constraint validation. Phase 10 needed to decide whether to implement that prefilter or generate unconditionally and let `ConstraintEngine` (Phase 9) be the sole validity authority.

**Decision:** `CandidateGenerator` queries every lab in the system (`LabRepository.findAll`, ordered by code) and builds a `CandidateAllocation` for each one, unconditionally - no capacity/software/equipment/type/availability conditional exists anywhere in `CandidateGenerator` itself.

**Alternatives:** Prefiltering by capacity/software/type (the original sketch) was rejected - it would duplicate HC-07/HC-08/HC-10's own logic in a second location, creating a real risk of Phase 9 and Phase 10 silently disagreeing about validity as either evolves independently. It would also make a prefiltered-out lab's rejection unexplainable: a lab excluded before `ConstraintEngine` ever sees it produces no `ConstraintViolation` for Phase 12/13 to read later - the CR would simply never learn *why* C-304 isn't in their results.

**Reasons:** "Which labs should be considered" and "is a considered lab valid" are different questions with different owners (PART 2 of the Phase 10 brief) - conflating them by prefiltering would blur that boundary. At the current ~15-16 lab scale, generating unconditionally costs nothing meaningful (verified live: one full generation run across 16 labs completes in well under a second).

**Trade-offs:** More total constraint evaluations than a prefiltered approach would produce (every lab is fully evaluated, not just plausible ones) - accepted as negligible at this scale; a future phase could reintroduce a *safe* prefilter (PART 11 of the brief explicitly allows this) if evidence of a real performance problem ever emerges, but none exists today.

---

## ADR-047: Invalid Candidates Are Preserved, Never Discarded, Inside `CandidateGenerationResult`

**Context:** `CandidateGenerationResult` could have been designed to return only the valid candidates (discarding rejected ones after generation), or to retain every evaluated candidate regardless of outcome.

**Decision:** `CandidateGenerationResult` holds every `EvaluatedCandidate` from the run; `validCandidates()`/`invalidCandidates()` are computed, filtered views over the same underlying list, not two separately-populated collections.

**Reasons:** Phase 12 (explainability) and Phase 13 (alternative suggestions) both need a rejected candidate's full `ConstraintViolation` list later - discarding invalid candidates now would force either re-running generation (wasted work, and a second chance for results to drift between runs) or duplicating violation data into some other structure. Keeping one list with computed views is simpler than either.

**Trade-offs:** `CandidateGenerationResult` holds slightly more data in memory than a valid-only design would (all ~15-16 candidates' `ConstraintEvaluation`s, not just the valid subset) - negligible at this project's scale.

---

## ADR-048: `CandidateGenerator` Is a `@Service`, Not an Interface

**Context:** PART 34 of the Phase 10 brief raised the question of whether `CandidateGenerator` should be an interface (like `SchedulingConstraint`) or a concrete class.

**Decision:** A concrete `@Service` class, no interface.

**Reasons:** `SchedulingConstraint` is an interface because it has twelve real implementations dispatched over polymorphically by `ConstraintEngine` - genuine value from the abstraction. `CandidateGenerator` has exactly one implementation and no dispatch requirement, the same situation `SchedulingContextFactory` and `CandidateAllocationFactory` (Phase 8/9) were already in as concrete classes. Adding an interface here would be ceremony with no consumer, which this project's working rules explicitly discourage (PART 34: "do not create interfaces solely for ceremony").

**Trade-offs:** None significant - if a second implementation is ever genuinely needed (e.g. a caching or bulk-optimized variant), extracting an interface at that point is a small, mechanical refactor with no design cost paid today for a need that may never arrive.

---

## ADR-049: Three Soft-Scoring Factors Deferred — No Data Was Invented to Manufacture Them

**Context:** docs/07-ALLOCATION-SCORING.md's original design proposed six weighted factors. Phase 11's mandatory pre-implementation readiness analysis (its brief's PART 1) had to decide, for each, whether real data actually backs it in the current schema, or whether implementing it would require fabricating a proxy.

**Decision:** Additional Environment Fit, Faculty Preference, and Fewer Timetable Gaps are deferred - no `AllocationScorer` bean, no configured weight, and critically no new table/column was added to manufacture data for them. Additional Environment Fit has no "preferred/recommended software" concept anywhere (only all-required software/equipment joins); Faculty Preference has only `FacultyAvailability` (allowed windows, not a preference); Fewer Timetable Gaps is structurally meaningless while every candidate in one `CandidateGenerationResult` (Phase 10) shares the exact same date/time - only the lab varies, so no lab choice can change a gap.

**Alternatives considered:** Treating `FacultyAvailability` as a preference proxy (rejected - availability means "allowed," not "desirable," and conflating them would mislead scoring toward a meaning the data was never recorded for). Counting installed-software quantity as an "environment fit" proxy (rejected - more unrelated software on a lab is not a real quality signal; PART 16 of the brief explicitly prohibits this). Adding new schema (a `faculty_preference` table, a `subject_preferred_software` join, an `operating_hours` config) specifically to unlock these factors (rejected - PART 66 of the brief: the phase's objective is truthful optimization using existing data, not manufacturing enough tables to reach a target score of 100).

**Reasons:** A fabricated score is worse than an honestly absent one - it would look like a real preference signal to anyone reading a `ScoreContribution`, when it would actually be measuring something unrelated or nonexistent. `ScoringFactorId` keeps all six as stable enum constants specifically so a future phase, once real data exists, can register a scorer bean without an ID-numbering change.

**Trade-offs:** The enabled scoring model totals 60 points, not the originally sketched 100 - accepted; docs/07-ALLOCATION-SCORING.md is explicit that the denominator was never a fixed 100, and `ScoringEngine` computes each candidate's applicable maximum from whichever factors actually registered a bean, never a hardcoded constant.

---

## ADR-050: Balanced Utilization Is a Relative (Min-Max Normalized) Comparison, Never an Absolute Percentage

**Context:** The original design sketch computed utilization as `allocatedMinutes / availableMinutes`, implying a true percentage. Phase 11's readiness analysis found no working-days/daily-operating-hours concept exists anywhere in the schema or configuration to serve as that denominator.

**Decision:** `BalancedUtilizationScorer` compares each candidate lab's scheduled minutes (within the term's currently `PUBLISHED` `ScheduleVersion`) only to the *other candidate labs in the same scoring run* - min-max normalized (`(maxLoad - candidateLoad) / (maxLoad - minLoad)`), never divided by any notion of "available" time.

**Alternatives:** Inventing a fixed institutional operating-hours config (e.g. "09:00-17:00, 6 days") to compute a true percentage was considered and rejected - it would be an arbitrary, unverified assumption about how this specific college actually operates, not a real institutional fact anyone provided. The brief's own PART 22 example formula (ratio against the single most-loaded lab, forcing it to exactly zero) was also considered and rejected in favor of min-max normalization, since the most-loaded candidate being forced to zero regardless of how close the rest of the field is felt unstable and poorly explainable.

**Reasons:** A relative comparison is exactly what "balanced" means for this factor's purpose - spreading load across the candidate pool - and doesn't require asserting a fact (operating hours) nobody actually confirmed. Min-max normalization stays bounded `[0, weight]` and degrades gracefully (full credit for everyone) when every candidate is equally loaded, including the common all-zero case.

**Trade-offs:** The factor cannot answer "is this lab busy in absolute terms," only "is this lab busier than its peers right now" - acceptable, since ranking candidates relative to each other is the actual job this factor does inside `ScoringEngine`.

---

## ADR-051: `ScoringContext` Introduced as a Second, Narrower Context Alongside `SchedulingContext`

**Context:** `AllocationScorer.score(...)` needs candidate-independent data the same way `SchedulingConstraint.evaluate(...)` does, but Balanced Utilization additionally needs data that is inherently *relative across the whole valid-candidate set being scored this run* (the min/max scheduled-load), which no single candidate's `SchedulingContext` can supply on its own.

**Decision:** A new `ScoringContext` record wraps the existing `SchedulingContext` (unchanged, reused as-is) plus the per-lab scheduled-minutes map and its precomputed min/max, assembled once per `ScoringEngine.score(...)` call and passed to every scorer.

**Alternatives:** Extending `SchedulingContext` itself with scoring-specific fields (rejected - `SchedulingContext` is Phase 8/9 infrastructure also consumed by `ConstraintEngine`; adding scoring-only fields to it would blur its established candidate-independent-data-for-constraints role). Giving `AllocationScorer.score(...)` a raw `Map<Long,Long>` parameter instead of a wrapper type (rejected - `CapacityFitScorer`/`PreferredLabTypeScorer` don't need it at all, and a bare map parameter every scorer must accept but most ignore is a worse interface than one cohesive context object, mirroring `SchedulingContext`'s own "load once, reuse across every candidate" role one layer up).

**Reasons:** Keeps `SchedulingContext` exactly as Phase 8/9 left it (no cross-phase modification without a real reason) while still avoiding one utilization query per candidate - `ScoringEngine` computes the min/max exactly once for the whole run, not once per scorer invocation.

**Trade-offs:** One more small type in the codebase - accepted; it is a plain data holder like `SchedulingContext`, performs no queries itself, and only `BalancedUtilizationScorer` reads the parts beyond `schedulingContext()`.

---

## ADR-052: The Phase 10 "16-Lab" Figure Was Stale Docker Volume Data, Not a Seeder Defect

**Context:** Phase 10's original completion report observed 16 labs where the seeded dataset should have 15. Phase 11's mandatory pre-phase investigation (its brief's PART 2) required determining the exact cause before writing any scoring code that might otherwise silently encode a wrong assumption about the lab pool.

**Decision:** Investigated via direct SQL against the live Docker Postgres volume rather than guessing: `SELECT id, code, created_at FROM lab ORDER BY id` showed labs 1-15 all created within the same second (`DevLabSeeder`'s single transactional run) and a 16th, `E-101`, created roughly 27 minutes later on a different day - clearly a manually-created row from an earlier ad-hoc verification session, never cleaned up because the Docker named volume (`postgres-data`) persists across `docker compose down`/`up` cycles unless `-v` is explicitly passed. `DevLabSeeder` itself was re-read and confirmed already fully idempotent (`findByCode(...).orElseGet(...)` for every entity it creates) - it was never capable of producing a 16th row on its own. Verified `E-101` had zero dependent rows in `allocation`/`lab_software`/`lab_equipment`/`lab_unavailability` before deleting it; lab count is now confirmed 15 via both direct SQL and `GET /api/labs`.

**Reasons:** Distinguishing "the seeder has a bug" from "stale local environment state" matters - fixing the wrong thing (e.g. adding defensive dedup logic to an already-idempotent seeder) would have been a wasted, misleading change. The real fix was data hygiene, not code.

**Trade-offs:** None - this is a one-time cleanup of local development state, not a schema or application-code change. Documented here so a future phase encountering an unexpected lab count in a long-lived local Docker volume knows to check for stale manual-verification leftovers before assuming a seeder regression.

---

## ADR-053: `AllocationRecommendation`, Not `AllocationDecision`

**Context:** Phase 8 deliberately deferred naming the "final outcome" type until scoring (Phase 11) and explanation (Phase 12) both existed to give it a real shape. Phase 12's brief explicitly required evaluating both names.

**Decision:** `AllocationRecommendation`. No `AllocationDecision` type exists anywhere in the codebase.

**Alternatives:** `AllocationDecision` (the name several earlier phase docs used as a placeholder) was rejected specifically because "decision" implies something was decided/committed - closer to the connotation of an approved, actionable outcome. Nothing here is committed: no `Allocation` row is created, no lab is reserved, and the result can be stale the instant the read transaction ends.

**Reasons:** Precise terminology matters for a system whose entire premise is that a snapshot is not a booking (PART 2 of the Phase 12 brief). "Recommendation" accurately signals "advisory, could change" the way "decision" does not - a reader (or a future engineer building Phase 15/16 on top of this) should not have to read the implementation to learn that recommending a lab doesn't reserve it.

**Trade-offs:** None significant - this is a one-time naming choice with no functional cost either way.

---

## ADR-054: Two Distinct Explanation Types (`ExplainedValidCandidate` / `RejectedCandidateExplanation`), Not One Type With Nullable Score Fields

**Context:** PART 6's draft sketch proposed a single `ExplainedCandidate` type with nullable `rank`/`score`/`applicableMaxScore`/`normalizedScore` fields, used for both valid and invalid candidates. PART 28 separately required that an invalid candidate's explanation must never contain `score = 0`, since that would misleadingly suggest scoring was applied to a hard rejection.

**Decision:** Two separate record types. `ExplainedValidCandidate` has no nullable score fields - every field is always populated, because only valid, scored candidates are ever wrapped in one. `RejectedCandidateExplanation` has no score field at all, not even a nullable one.

**Alternatives:** A single type with nullable score fields (the brief's own PART 6 sketch) was considered and rejected - a nullable field is a runtime convention a caller must remember to check ("is this null because it's invalid, or because of a bug upstream?"); a type that structurally cannot hold a score removes that question entirely. This mirrors the same reasoning `ScoredCandidate` (Phase 11) already applied by rejecting invalid candidates in its own constructor rather than allowing a zero/null score to exist for one.

**Reasons:** Type-level guarantees are stronger than documentation or nullable-field discipline - `RejectedCandidateExplanation.score` cannot exist to be forgotten. This also keeps each type's responsibility narrow: `ExplainedValidCandidate` is "this candidate, ranked and scored"; `RejectedCandidateExplanation` is "this candidate, and every reason it failed."

**Trade-offs:** Two types instead of one means `AllocationRecommendation` needs two separate list fields (`rankedValidCandidates`, `rejectedCandidates`) rather than one polymorphic list with a `valid` flag - accepted, since a caller iterating "all candidates regardless of validity" was never a real requirement of this phase (Phase 10's `CandidateGenerationResult` already serves that role if ever needed).

---

## ADR-055: Display Labels Are Separate Static Lookup Classes, Not Embedded Fields on Domain Types

**Context:** PART 35/36 required human-readable labels for machine identifiers (`HardConstraintId`, violation `errorCode`, `ScoringFactorId`) without losing the machine identifier itself, and without turning every domain result object into a UI string.

**Decision:** Three small, stateless static-lookup classes - `HardConstraintLabels`, `ViolationErrorCodeLabels`, `ScoringFactorLabels` - each mapping one enum/string identifier to a short display string. Explanation records (`ConstraintCheckExplanation`, `ViolationExplanation`) call these once at construction and store the resulting label alongside the untouched machine identifier; the underlying Phase 9/11 domain types (`HardConstraintId`, `ConstraintViolation`, `ScoringFactorId`) themselves are completely unmodified.

**Alternatives:** Adding a `displayLabel()` method directly to `HardConstraintId` (an enum) was considered and rejected - it would couple a Phase 9 domain enum to a Phase 12 presentation concern, and any future addition/renaming of a display label would then require touching Phase 9's tested code. A single giant label-lookup class covering all three identifier kinds was also considered and rejected in favor of three focused, single-responsibility classes matching the three kinds of code being labeled.

**Reasons:** Keeps "what a candidate failed" (domain fact, Phase 9) fully separate from "what to show a human" (presentation concern, Phase 12) - exactly the "structured + display layers" boundary the brief required (PART 36). A future UI/API layer can add or restyle a label without touching validation logic at all.

**Trade-offs:** Three small files instead of one - accepted; each is under 30 lines and trivially testable/extendable.

---

## ADR-056: Structural vs. Temporal Conflict Classification Drives the Entire Alternative-Search Decision

**Context:** Phase 13 needed a principled way to decide, for a request with zero valid candidates, whether searching alternative times could plausibly help - without duplicating any Phase 9 constraint logic to answer that question.

**Decision:** Every existing `ConstraintViolation.errorCode()` is classified once, statically, as `STRUCTURAL` (true regardless of time of day: capacity, software, equipment, lab type, academic relationship, authorization) or `TEMPORAL` (could change if the time changes: lab/faculty/batch/division conflict, faculty availability, lab unavailability). A candidate is "structurally viable" iff none of its violations are `STRUCTURAL`. Alternative-time search is attempted iff at least one candidate is structurally viable.

**Alternatives:** Re-running a cheaper/partial constraint check specifically to answer "would this candidate become valid at a different time" was considered and rejected - it would be a second, parallel validity concept alongside the real `ConstraintEngine`, at real risk of drifting out of sync with HC-01..HC-12 as they evolve. Classifying the *already-produced* violation codes instead requires no new validity logic at all - just a lookup table over data Phase 9 already emits.

**Reasons:** This correctly handles the brief's mandatory mixed case (12 labs fail software, 3 Cloudera-capable labs fail only a temporal constraint - search must still proceed for those 3) with a single boolean check, no special-casing. It also naturally prevents pointless search: if every candidate has at least one structural failure, no amount of time search can ever help, and none is attempted (verified live: `slotsSearched=0` for a structurally-impossible request).

**Trade-offs:** An unrecognized future error code defaults to `STRUCTURAL` (the conservative choice) - a genuinely time-solvable new constraint would need its code added to the temporal set explicitly, or it will simply never trigger a search for it. Accepted: failing to search in an ambiguous case is far safer than searching pointlessly or, worse, claiming a time change could fix something it cannot.

---

## ADR-057: Alternative Ranking Is Lexicographic (Day, Then Time, Then Score, Then Lab Code) — Never One Merged Number

**Context:** An alternative suggestion has at least three things that could matter for ranking: how far away in time it is, how good the lab is (Phase 11's score), and a need for a deterministic tie-break. The brief's own example (a same-time 60%-score alternative should be preferred over a two-hours-later 90%-score one) rules out "just sort by score."

**Decision:** A four-key lexicographic `Comparator` chain: day displacement ascending, then time-of-day displacement ascending, then Phase 11's normalized score descending, then lab code ascending. No step combines two of these into one derived number.

**Alternatives:** A single weighted composite score (e.g. `score - k * displacementMinutes`) was considered and rejected - any weight `k` is an arbitrary, undocumented judgment call about how much disruption is "worth" how many score points, and the brief explicitly warns against exactly this ("do not silently combine everything into one unexplained magic score," PART 13).

**Reasons:** Lexicographic ranking is fully explainable in one sentence per level, requires no tuning, and matches the intuitive priority the brief itself describes: minimizing disruption (day, then time) matters more than optimizing lab quality once a candidate is already valid. Verified directly with an adversarial fixture (low score close in time beats high score far away).

**Trade-offs:** A suggestion that is only slightly closer in time but meaningfully worse in score will still outrank a much-better-scored, slightly-farther one - accepted as the correct behavior per the brief's own stated preference for minimizing disruption, not a defect.

---

## ADR-058: No `SAME_TIME_DIFFERENT_LAB` `AlternativeType` — It Is Structurally Unreachable

**Context:** The brief's own draft sketch (PART 14) listed `SAME_TIME_DIFFERENT_LAB` as a plausible `AlternativeType` value alongside `SAME_DAY_DIFFERENT_TIME` and `DIFFERENT_DAY`.

**Decision:** `AlternativeType` has exactly two values: `SAME_DAY_DIFFERENT_TIME` and `DIFFERENT_DAY`. No same-time-different-lab value exists.

**Reasons:** Phase 10's `CandidateGenerator` already evaluates every lab at the exact requested time as part of building the original `AllocationRecommendation`. If any lab were valid then, `ExplainableAllocationService.recommend(...)` would already return `RECOMMENDED`, and `AlternativeSuggestionService.findAlternatives(...)` returns `ALTERNATIVES_NOT_NEEDED` immediately, before `SchedulingSlotProvider` is ever invoked - the code path that would produce a `SAME_TIME_DIFFERENT_LAB` suggestion is provably unreachable given how candidate generation already works. Creating the enum value anyway would violate the brief's own explicit rule (PART 14): "do not create enum values for unsupported behavior."

**Trade-offs:** None - a caller wanting "is there a same-time solution" already has the answer from `AllocationRecommendation.status()`/`recommendedCandidate()`/`otherValidCandidates()` (Phase 12) without needing a Phase 13 type for it.

---

## ADR-059: `SchedulingSlotPolicy` Is a Single, Centralized, User-Sourced Configuration — Never Guessed College Hours

**Context:** Phase 13's alternative-time search fundamentally needs to know what times are worth trying - working days, daily hours, session duration, how far ahead to look. No such rule existed anywhere in this repository before this phase (docs/ASSUMPTIONS.md A-35), and the brief explicitly forbade inventing one.

**Decision:** The missing rules were requested directly from the user rather than assumed. The answer (fixed 2-hour sessions on the hour, 09:00-19:00, Monday-Saturday, small look-ahead) is centralized in exactly one component, `SchedulingSlotPolicy`, backed by `app.scheduling.*` configuration - following this project's existing constructor-`@Value` convention.

**Alternatives:** Silently defaulting to a "reasonable-sounding" college schedule (e.g. 09:00-17:00, weekdays only, hourly lunch break) was considered and rejected outright - it would have been exactly the fabrication the brief's PART 75 explicitly prohibited, and a wrong assumption baked into scheduling logic is far more costly to discover later than a clarifying question asked once, up front.

**Reasons:** Keeping the policy in one component (rather than scattering magic time/day literals across `SchedulingSlotProvider`) means a future correction to real college policy - if these values ever prove wrong - requires changing configuration in exactly one place, with zero code changes to conflict analysis, scoring, or generation.

**Trade-offs:** `max-lookahead-days` was not given an exact number by the user ("up to N, a small number like 2-3") - this project chose 3 as its own documented default within that stated bound, explicitly flagged as a project decision rather than a college policy fact (docs/ASSUMPTIONS.md A-35).

---

## ADR-060: `SessionRequirement` Is a Distinct Type From `SchedulingRequest`, Never a Placeholder-Dated Reuse

**Context:** Phase 14 needed an input shape for "what must be scheduled" (a session that has no assigned date/time yet) alongside the existing Phase 8 `SchedulingRequest` ("evaluate this at this concrete date/time"). Reusing `SchedulingRequest` with a sentinel/placeholder date was a tempting shortcut to avoid a new type.

**Decision:** `SessionRequirement` is a separate record (key, allocationType, targetType, divisionId, batchId, subjectId, facultyId, academicTermId, actor - no date/time fields at all) with a `toRequest(TimeSlot slot)` method that produces a concrete `SchedulingRequest` only once a candidate slot is being tried. `SchedulingRequest` itself gains no new fields and no new "not yet decided" semantics.

**Alternatives:** Reusing `SchedulingRequest` with a placeholder date (e.g. `LocalDate.MIN`) - rejected outright per the brief's explicit instruction, since every one of Phase 8-13's invariants and every existing caller assumes a `SchedulingRequest`'s date/time is real and evaluable, and a placeholder value would either need special-casing everywhere or would silently produce nonsense if it ever leaked into a real evaluation path.

**Reasons:** Keeping "what must happen" (`SessionRequirement`) and "evaluate this at this instant" (`SchedulingRequest`) as separate types makes each one's invariants simple and unconditional - a `SchedulingRequest` is always concretely evaluable, full stop, which is exactly what every Phase 8-13 consumer already assumes and is never asked to change.

**Trade-offs:** One extra small type and one small conversion method - accepted, since the alternative would have quietly weakened `SchedulingRequest`'s own contract for every existing caller.

---

## ADR-061: Provisional Occupancy Integrated via Additive Overloads, Never a Second Validity Path

**Context:** Phase 14's backtracking search needs HC-01/02/04/05 to see both persisted allocations and the search's own in-progress (not-yet-persisted) decisions, without duplicating any of Phase 9's tested constraint logic and without turning any constraint class into a database-writing service.

**Decision:** New overloaded methods accepting a `SchedulingSearchState` were added alongside the existing single/two-arg signatures at each layer the data must pass through (`SchedulingContextFactory.build(request, searchState)`, `CandidateAllocationFactory.build(context, labId, searchState)`, `CandidateGenerator.generate(request, searchState)`, `ExplainableAllocationService.recommend(request, searchState)`). The original signatures are unchanged and simply delegate to the new ones with `SchedulingSearchState.empty()`. `SchedulingSearchState.toSnapshot()` produces plain `ExistingAllocationSnapshot` records - the exact type HC-01/02/04/05 already consumed before Phase 14 existed - merged onto the same lists the persisted data already populates.

**Alternatives:** A parallel "provisional conflict checker" duplicating HC-01/02/04/05's overlap logic inside the backtracking engine itself - rejected, since it would create a second source of truth for validity that could silently drift from Phase 9's tested classes as they evolve. Modifying HC-01/02/04/05 in place to accept a new parameter - rejected as a larger, riskier change to twelve already-tested constraint classes when a strictly additive extension achieves the same result with zero behavior change for every existing caller.

**Reasons:** This was only possible because Phase 9's constraints already read conflict data exclusively from plain `List<ExistingAllocationSnapshot>` records, never JPA-coupled - an investigation this phase's brief explicitly required before choosing an integration strategy. Confirmed by the full pre-existing test suite passing unmodified (aside from Mockito stub-arity updates, a test-infrastructure concern, not a behavior change) after the additive overloads were introduced.

**Trade-offs:** Four classes now carry two overloads of the same method instead of one - a small, deliberate duplication of method signatures (not logic) in exchange for zero risk to twelve already-tested constraint classes.

---

## ADR-062: Bounded DFS Backtracking With a Hard Node-Count Limit, Not an Unbounded Search

**Context:** Multi-requirement scheduling is a CSP with worst-case exponential complexity; an unbounded search could run indefinitely on a pathological or genuinely infeasible input.

**Decision:** `AutomaticSchedulingEngine` performs depth-first search with backtracking, bounded by a configurable `maxNodes` (default 2000, `app.scheduling.backtracking.max-nodes`). Reaching the limit produces `SEARCH_LIMIT_REACHED`, a status explicitly distinct from `NO_SOLUTION` - "we stopped searching" is never conflated with "we proved this is impossible."

**Alternatives:** A wall-clock timeout instead of a node count - rejected, since it would make search behavior non-deterministic across runs on different hardware/load, breaking the determinism requirement (ADR requirement carried over from every earlier phase's own testing philosophy). A node count is deterministic and directly reproducible in a unit test.

**Reasons:** Bounding the search is mandatory for a production system - the brief itself required it, and exponential worst-case behavior on ~15 labs and a handful of requirements is a real, not hypothetical, risk once a caller supplies pathological or near-infeasible input (a large date range, many requirements, few real options).

**Trade-offs:** A search that would have found a valid complete schedule two nodes past the limit reports `SEARCH_LIMIT_REACHED` instead - a known, accepted trade-off of any bounded search, with the limit itself exposed as configuration so it can be raised for a specific deployment without a code change.

---

## ADR-063: Dynamic MRV, Recomputed at Every Search Node — Supersedes the Phase 1 Static Sketch

**Context:** The original Phase 1 planning sketch in docs/05-SCHEDULING-ENGINE.md described most-constrained-first ordering computed once, at the top of the search. Phase 14's actual implementation needed to decide whether that static ordering was still correct once backtracking is real.

**Decision:** `AutomaticSchedulingEngine.solve(...)` recomputes each unassigned requirement's number of remaining valid choices at every recursion node (Minimum-Remaining-Values), not once at the top of the search - since which requirement is "most constrained" can genuinely change once earlier requirements have consumed some of the shared slot/lab space.

**Alternatives:** The original static, top-level ordering - rejected once it was worked through by hand (roughly ten constructed CSP scenarios during this phase) and shown to be provably wrong in scenarios where an earlier assignment changes which requirement is now most constrained; a static ordering computed before any assignment exists cannot reflect that.

**Reasons:** Dynamic MRV is also what empirically avoids backtracking on the brief's own worked "R1: X-or-Y, R2: X-only" example - it always schedules the more-constrained requirement (R2) first, sidestepping the exact greedy trap the brief uses to motivate backtracking in the first place. This was proven directly by a package-private, test-only `useMrv=false` toggle isolating the underlying backtracking/undo mechanism from MRV's benefit, so both properties (backtracking works; MRV usually avoids needing it) are demonstrated separately rather than conflated into one ambiguous test.

**Trade-offs:** Recomputing MRV at every node costs more per node than a static ordering (`O(remaining requirements × slots)` per node instead of a one-time `O(requirements × slots)`) - accepted, since correctness (and materially fewer backtracks in practice) matters more than this constant-factor cost at the project's actual scale. Documented in docs/05-SCHEDULING-ENGINE.md as an explicit complexity trade-off, not glossed over.

---

## ADR-064: Immutable `SchedulingSearchState`, Never Save-Trial-Then-Rollback

**Context:** The brief explicitly forbade a "provisionally persist to the database, then roll back the transaction" approach to representing in-progress search decisions - both for correctness (any observer, including a concurrent request, could see the trial state) and for the simple reason that Phase 14 must never write to the database during search at all.

**Decision:** `SchedulingSearchState` is an immutable record holding a `List<PlannedAllocation>`; each recursive step produces a new state via `with(PlannedAllocation)` rather than mutating a shared collection, and backtracking is simply "don't carry this state forward," not an explicit undo/rollback operation.

**Alternatives:** A mutable, shared list with explicit add/remove-on-backtrack calls - considered, since it would be marginally cheaper per node; rejected in favor of immutability once it was clear the search's actual node counts (bounded by `maxNodes`, in the thousands at most) make the performance difference immaterial, while immutability eliminates an entire class of "did every backtrack path correctly undo its own mutation" bugs by construction.

**Reasons:** This directly satisfies the brief's "no save-trial-then-rollback" requirement in its strongest form - not just "don't hit the database," but "there is no mutable shared state to leak between recursive branches at all." It also makes `SearchBookkeeping.observe(state)` (used to track the best state seen anywhere during search, for `PARTIAL`/`NO_SOLUTION` reporting) trivially safe to call at any point without needing to defensively copy anything.

**Trade-offs:** Each recursion node allocates a new `SchedulingSearchState`/list rather than mutating one shared instance - accepted per the brief's own explicit preference ("prefer immutable state; correctness over micro-optimization").

---

## ADR-065: `PlannedAllocation.toSnapshot()` Uses a Sentinel `-1L` Allocation Id, Never `null`

**Context:** A provisional (not-yet-persisted) planned allocation has no real database identity yet. The first implementation modeled this as `allocationId = null` in the `ExistingAllocationSnapshot` it produces for constraint evaluation - a reasonable-seeming choice at design time, since there genuinely is no persisted id.

**Decision:** `PlannedAllocation` defines `PROVISIONAL_ALLOCATION_ID = -1L` and uses it in `toSnapshot()` instead of `null`.

**Reasons:** This was not a design preference - it fixes a real bug found live in Docker: `LabConflictConstraint`/`FacultyConflictConstraint`/`BatchConflictConstraint`/`DivisionWideConflictConstraint` all build their `ConstraintViolation.details()` map via `java.util.Map.of("existingAllocationId", existing.allocationId(), ...)`, and `Map.of` throws `NullPointerException` on any null value. This was never exercised by any mocked unit test (mocks bypass the real constraint classes entirely) and crashed the whole Spring Boot process the first time a real provisional conflict occurred during live multi-requirement search. `-1L` is documented as never colliding with a real PostgreSQL `BIGINT` identity value, which always starts at 1 and is positive (docs/ASSUMPTIONS.md A-14).

**Alternatives:** Changing all four constraint classes to use `Map.ofEntries` with a null-tolerant entry, or to build the map conditionally - rejected as a larger, riskier change touching four already-tested Phase 9 classes, when the actual defect is fully addressable at its true source (a provisional snapshot claiming to have no id when a synthetic, always-valid one is all that's actually needed downstream).

**Trade-offs:** None significant - a regression test (`PlannedAllocationTest`) exercises the real, unmocked `LabConflictConstraint` against a snapshot built via `toSnapshot()` to guard against this specific defect recurring.

---

## ADR-066: Four-Status Result Model — `SEARCH_LIMIT_REACHED` Is Never Conflated With `NO_SOLUTION`

**Context:** A multi-requirement search can end in more than two meaningfully different ways: everything got scheduled; some things got scheduled and the rest are genuinely impossible; nothing is possible at all; or the search ran out of budget before it could determine either.

**Decision:** `AutomaticScheduleStatus` has exactly four values - `COMPLETE`, `PARTIAL`, `NO_SOLUTION`, `SEARCH_LIMIT_REACHED` - each with a precise, non-overlapping meaning. `SearchBookkeeping` tracks the best state observed anywhere during search (`observe()`) so `PARTIAL`/`NO_SOLUTION` results can report real, best-effort assignments without a separate greedy fallback pass.

**Alternatives:** Collapsing `NO_SOLUTION` and `SEARCH_LIMIT_REACHED` into one `PARTIAL`/`FAILED` status - rejected, since they mean fundamentally different things to a caller: one is a proof of infeasibility within the search budget, the other is an explicit admission the search didn't finish. Reporting the latter as the former would falsely claim impossibility a larger node budget might disprove.

**Reasons:** This distinction is directly testable and directly tested - a dedicated unit test constructs a scenario where a tiny `maxNodes` cuts a solvable search short (`SEARCH_LIMIT_REACHED`) and a separate scenario exhausts a full, adequate budget on a genuinely infeasible input (`NO_SOLUTION`), proving the two paths are reachable and distinguishable in practice, not just in name.

**Trade-offs:** None significant - four precise statuses cost nothing extra to compute, since `SearchBookkeeping` already tracks the information needed to distinguish them.

---

## ADR-067: Automatic Scheduling Remains Advisory — No Persistence, No Production API, No `ScheduleVersion`

**Context:** Consistent with Phase 12/13 (ADR-053), Phase 14's result is itself a snapshot computed during one read-only transaction; the brief explicitly reconfirmed this boundary rather than letting Phase 14 quietly become the first phase to persist automatically.

**Decision:** `AutomaticSchedulingEngine.schedule(...)` runs under `@Transactional(readOnly = true)`, never constructs an `Allocation` entity, never creates or touches a `ScheduleVersion`, and is not exposed through any REST controller - it exists only as an internal service, exercised by tests and by a temporary, deleted-after-use dev diagnostic runner.

**Alternatives:** Adding a production `POST /api/scheduling/automatic` endpoint now that the engine exists - rejected as outside this phase's explicit scope (deciding *which* valid multi-session schedule to actually commit, and under what authorization/workflow, is a real design question the brief deferred, not an oversight to quietly resolve here) and because no documented FR currently calls for one.

**Reasons:** Committing a generated schedule safely under concurrent real-world writes is exactly the kind of problem Phase 16's FCFS/concurrency work exists to solve properly (ADR-010) - persisting from Phase 14 now would mean either reinventing that safety net ad hoc or, worse, skipping it. Verified live: `automaticSchedulingNeverChangesAllocationRowCount` (`AutomaticSchedulingIT`) and the manual Docker verification's before/after row-count check both confirm zero persistence across every scenario run.

**Trade-offs:** A caller cannot yet act on a generated schedule through this system - it must be reviewed and committed by some future, still-undesigned workflow. Documented as a known limitation in docs/05-SCHEDULING-ENGINE.md, not silently implied to be "done."

---

## ADR-068: Ownership Fields Omitted From the Request DTO Entirely, Not Merely Validated

**Context:** Phase 15's `ExtraLabSearchRequest`/`ExtraLabBookingRequest` needed a decision about whether `divisionId`/`facultyId`/`academicTermId` belong on the wire at all - a common, tempting pattern is to accept them from the client "for convenience" and then validate them server-side against the resolved ownership.

**Decision:** None of the three fields exist anywhere in either request DTO. `divisionId`/`academicTermId` are resolved exclusively from `CrOwnershipService.getCurrentAssignment(userId)`; `facultyId` exclusively from `FacultyAssignmentResolutionService`. There is no code path in `ExtraLabService` that ever reads any of the three from client input.

**Alternatives:** Accepting the fields and validating them against the resolved assignment (reject on mismatch) - rejected per the phase brief's own explicit guidance ("prefer omitting it entirely if unnecessary"). A validate-on-mismatch design still requires a human reader to trust that every code path actually performs the check; omitting the field entirely makes the trust question structurally moot - there is nothing to forget to validate.

**Reasons:** This is the strongest possible form of "never trust client-supplied ownership" (HC-11's whole reason for existing) - not "trust but verify," but "there is nothing here to trust in the first place." It also simplifies the API surface: a CR client never needs to know or send its own division id to use this workflow at all.

**Trade-offs:** None significant - the resolved division/faculty are still returned in every response (`ExtraLabAllocationResponse`), so a client can display them; it simply never supplies them as input.

---

## ADR-069: Search Reuses the Full Pipeline via One `AlternativeSuggestionService` Call; Booking Revalidates Only the Selected Candidate

**Context:** Search needed to expose ranking/rejection/alternatives (Phase 10-13); booking needed to revalidate a specific, already-chosen lab before persisting. These are architecturally different questions with different natural implementations.

**Decision:** `ExtraLabService.search` makes exactly one call, to `AlternativeSuggestionService.findAlternatives(request)` - which itself already calls `ExplainableAllocationService.recommend` and, when needed, searches alternative times - and maps its result directly to the API response. `ExtraLabService.book` does **not** call `CandidateGenerator` (which would regenerate and re-evaluate all ~15 labs) - it calls `SchedulingContextFactory.build` + `CandidateAllocationFactory.build` + `ConstraintEngine.evaluate` directly for the one selected `labId`.

**Alternatives:** Calling `CandidateGenerator.generate(...)` at book time too, then filtering for the selected lab's `EvaluatedCandidate` - considered and rejected as needless work: generating and evaluating every other lab in the system provides no additional correctness for a request that has already committed to one specific lab. `ConstraintEngine.evaluate(...)` is the same real validity check regardless of how many other candidates happen to be evaluated alongside it in a given caller.

**Reasons:** Search's job (helping a CR choose) and booking's job (confirming a choice is still valid) genuinely need different amounts of the pipeline - reusing the narrowest correct subset for booking is not a shortcut, it's using each existing component for exactly the question it answers, per the phase brief's own instruction ("correctness is more important than premature optimization" - but here, evaluating 15 candidates to answer a question about 1 is not premature optimization, it is simply unnecessary work with zero correctness benefit).

**Trade-offs:** None significant - both paths route through the identical, unmodified `ConstraintEngine`; there is no risk of the two questions being answered by two different validity definitions.

---

## ADR-070: EXTRA Allocations Attach Directly to the Currently `PUBLISHED` Version, Stamped `PUBLISHED` Immediately — Phase 15 Confirms, Does Not Redecide, A-11/§16

**Context:** This decision was already made in Phase 1 (docs/ASSUMPTIONS.md A-11) and reconfirmed in Phase 8's `AllocationType`/`ScheduleVersion` class javadocs, before any code existed to actually exercise it. Phase 15 is the first phase where a real HTTP request reaches this path, so it was re-verified against the real implementation rather than assumed still correct.

**Decision:** `ExtraLabService.book` resolves the term's currently `PUBLISHED` `ScheduleVersion` via `scheduleVersionRepository.findByAcademicTermIdAndStatus(termId, PUBLISHED)`, attaches the new `Allocation` directly to it, and constructs it with `AllocationStatus.PUBLISHED` from the start - never `APPROVED`, never a new `DRAFT` version. If no `PUBLISHED` version exists for the term, booking fails `409 NO_PUBLISHED_SCHEDULE` rather than inventing one.

**Reasons:** Re-verified rather than re-decided: the reasoning from A-11 (fast, FCFS makeup-lab booking would be defeated by waiting for the next official timetable cut) still holds, and nothing discovered during Phase 15's implementation contradicted it - confirmed by reading `Allocation`'s own Phase 8 class javadoc before writing a line of Phase 15 code, exactly as the phase brief's pre-analysis step required.

**Trade-offs:** A `PUBLISHED` `ScheduleVersion` is not fully frozen once published - it can keep accumulating EXTRA rows indefinitely, which must not be mistaken for a bug by a future Phase 18 versioning-history UI (already flagged in A-11 itself). `NO_PUBLISHED_SCHEDULE` is a real, reachable failure mode for a term that has never had a REGULAR timetable published - by design, not an oversight; EXTRA booking is additive to an official timetable, not a substitute for one.

---

## ADR-071: `LAB_ASSISTANT` Is Excluded From EXTRA Booking Creation

**Context:** `LAB_ASSISTANT` retains administrative authority generally (CR management, PDF import approval, publishing), and it would have been technically easy to also authorize it on `POST /api/allocations/extra`/`.../search` "just in case an administrator needs to book on a CR's behalf."

**Decision:** `@PreAuthorize("hasRole('CR')")` on `search`/`book`/`cancel`/`mine`; `LAB_ASSISTANT` receives `403` on all four. Only `GET /api/allocations/extra/activity` (read-only) is `LAB_ASSISTANT`-authorized.

**Alternatives:** Authorizing both `CR` and `LAB_ASSISTANT` on the write endpoints - rejected because no functional requirement in this project's confirmed role story asks for it (the story is explicitly "CR schedules/cancels its class; Lab Assistant adds CRs, approves/imports, views CR activity" - never "Lab Assistant books on a CR's behalf"). Adding it now would be speculative authorization surface with no real use case behind it, exactly the kind of unjustified permission this project's RBAC design otherwise avoids granting by default (see ADR-034's identical reasoning for faculty-availability read access).

**Reasons:** If a genuine future need for administrative EXTRA booking emerges, it is a straightforward, backward-compatible addition (loosening one `@PreAuthorize` annotation) - not a redesign. Keeping the write path CR-only now keeps the authorization model simple and matches the confirmed requirements exactly, rather than a guess at future ones.

**Trade-offs:** None significant, and explicitly reversible without a breaking change.

---

## ADR-072: Concurrency Is Deliberately Deferred to Phase 16 — Not Silently Assumed Solved by Phase 15's Transaction

**Context:** `ExtraLabService.book` already runs inside one `@Transactional` write boundary, re-validates the selected candidate against current data, and only then inserts. It would be easy - and wrong - to describe this as "solving" the concurrent double-booking problem the project's own overview names as a core requirement.

**Decision:** Phase 15's transaction boundary is documented, explicitly and repeatedly (this ADR, docs/03-SYSTEM-ARCHITECTURE.md §23, docs/05-SCHEDULING-ENGINE.md, docs/09-AUTHORIZATION-RBAC.md, docs/11-TESTING-STRATEGY.md's concurrency-test section), as necessary but *not sufficient* for concurrency safety - it eliminates the "stale search result" race (a single request's view of data going stale between search and book) but not the "two simultaneous requests" race (both transactions reading the same not-yet-committed-against state before either commits). No row-level locking, exclusion constraint, or `SERIALIZABLE` isolation was added in this phase.

**Alternatives:** Adding a locking/exclusion mechanism now, ahead of Phase 16 - rejected per the phase brief's explicit instruction ("do NOT implement concurrency protection... do not add a giant locking system in Phase 15"). Silently letting the transaction boundary read as "concurrency-safe" without saying otherwise - rejected as dishonest by this project's own working rules; a real, disabled/documentary concurrency test exists in docs/11-TESTING-STRATEGY.md specifically to make the remaining gap concrete and testable, not just asserted in prose.

**Reasons:** Building the real concurrency-safety mechanism (ADR-010's row-locking-vs-exclusion-constraint decision) deserves its own phase with its own dedicated concurrent-request test proving the guarantee actually holds - conflating it with Phase 15's already-substantial scope would risk delivering an unproven, "probably fine" guarantee dressed up as a real one.

**Trade-offs:** Two CRs racing for the same lab/time within milliseconds of each other could, in the current implementation, both pass their own transaction's revalidation before either commits (isolation-level dependent) - a real, acknowledged, currently-open gap, closed by Phase 16, not before.
