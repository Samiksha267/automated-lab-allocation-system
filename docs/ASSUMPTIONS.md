# Assumptions Log

This file records engineering assumptions made when requirements are ambiguous or unspecified. Each entry should be revisited if it turns out to be wrong.

## Phase 0 — Repository Analysis (2026-08-21)

### A-01: Greenfield project
**Assumption:** `c:\Lab_allocation` is an empty directory with no prior code, no git history, and no existing technology choices. This is a from-scratch build, not a migration or refactor.
**Reason:** Directory listing showed zero files/folders.
**Impact:** No legacy constraints to preserve; free to establish structure per PART 43 (backend) and standard Vite/React layout (frontend) from Phase 1 onward.

### A-02: [RESOLVED in Phase 2] Git initialization deferred to Phase 1, actually done in Phase 2
**Original assumption:** git init deferred to Phase 1.
**Resolution:** Phase 1 ended up being documentation-only (per explicit instruction to stop after Phase 1 docs were consistent); `git init` was actually run at the start of Phase 2 (Project Foundation), which is when real project files first existed to commit.
**Impact:** None — purely a timing note for anyone reading the phase history later.

### A-03: Monorepo layout
**Assumption:** Frontend (`frontend/`) and backend (`backend/`) will live in a single repository (monorepo), with `docs/` at the root, per PART 43 folder guidance and PART 6 architecture diagram (no separate repos, no Node.js layer between React and Spring Boot).
**Impact:** Single CI pipeline in GitHub Actions can build/test both; Docker Compose at root orchestrates both services + PostgreSQL.

### A-04: Java 21 + Spring Boot 3.x, Node 20 LTS for frontend tooling
**Assumption:** "Java 21+" means Java 21 LTS targeted with Spring Boot 3.3+ (Spring Boot 3.x requires Java 17+; 3.3+ has best Java 21 support). Frontend build tooling (Vite) will run on Node 20 LTS, even though Node itself is explicitly excluded from the *runtime* request path between React and Spring Boot (PART 6) — Node is only a build-time dependency for the frontend, not a server in the request path.
**Impact:** `pom.xml`/`build.gradle` will pin Java 21; `package.json` `engines` will pin Node 20.

### A-05: Build tool — Maven over Gradle
**Assumption:** Maven will be used for the backend build, as it is the more common default for Spring Boot tutorials/interviews and keeps `pom.xml` dependency management explicit and easy to document in `13-DEVELOPER-SETUP.md`.
**Impact:** Reversible early; if you prefer Gradle, say so before Phase 1 scaffolding.

### A-06: Database naming and local dev credentials
**Assumption:** Local dev DB will be named `lab_allocation`, with a `.env.example` (not `.env`) committed for local Docker Compose credentials. No real credentials will ever be committed.
**Impact:** Documented in `12-DEPLOYMENT-GUIDE.md` once created.

### A-07: Constraint engine scope for initial implementation
**Assumption:** HC-01 through HC-12 (PART 22) will each be a separate `SchedulingConstraint` implementation from the first working version of the constraint engine — not added incrementally as afterthoughts — because the batch/division-wide distinction (PART 11–15) is structurally load-bearing (affects the data model itself, e.g. `Allocation.targetType`), and retrofitting it later would require a migration and re-test of every constraint.
**Impact:** `Allocation` entity will carry an explicit `targetType` (DIVISION | BATCH) from its first migration, per PART 15's instruction to avoid "null batch ID" hacks.

### A-08: Three login roles only; Faculty has no login initially
**Assumption:** Per PART 6/PART 7, `User` entity roles are restricted to `LAB_ASSISTANT`, `CR`, `STUDENT`. `Faculty` is a pure domain/scheduling entity with no `User` row, no login, no JWT identity — confirmed explicitly in the spec, not inferred.
**Impact:** No auth endpoints or password fields on `Faculty`; if faculty login is added later it will require a new migration linking `Faculty` to `User`.

### A-09: Batch count and academic hierarchy are data-driven, not hardcoded
**Assumption:** Number of batches per division, number of divisions per year, number of labs (~15), and the Program → Stream → Year → Division → Batch hierarchy will all be rows in configurable tables, not enum constants — per PART 10 and PART 16's explicit "never assume exactly two batches" / "do not hard-code 15" instructions.
**Impact:** Slightly more schema complexity up front (self-referential or explicit hierarchy tables) in exchange for not needing schema changes when the college's structure changes.

---

## Phase 1 — Requirements + Architecture (2026-08-21)

### A-10: [RESOLVED/REFINED in Phase 1] Extra allocations never carry review states at all
**Original assumption:** EXTRA allocations skip `PENDING_REVIEW` via a `DRAFT → APPROVED` transition on the `Allocation` entity itself, while REGULAR goes through `PENDING_REVIEW`.
**Resolution (Phase 1):** refined further — `DRAFT`/`PENDING_REVIEW`/`CONFLICT`/`REJECTED` were removed from `Allocation.status` entirely and moved to `TimetableImportEntry.validation_status`/`timetable_import.status`, since an `Allocation` row is now only ever created once already known valid (see docs/03-SYSTEM-ARCHITECTURE.md §5). `Allocation.status` is just `APPROVED → PUBLISHED → CANCELLED` for both types; the REGULAR-vs-EXTRA distinction is *which version they attach to and when*, not a difference in the status enum's shape.
**Impact:** Simpler schema (no unused states on `Allocation`), and no ambiguity about what "conflict" or "rejected" means for a row that already represents a committed, valid booking.

### A-11: [RESOLVED in Phase 1] EXTRA allocations publish immediately; REGULAR allocations wait for explicit publish
**Original assumption:** left open whether EXTRA allocations need a separate/faster publish path.
**Resolution (Phase 1, docs/03-SYSTEM-ARCHITECTURE.md §5):** EXTRA allocations are created directly against the term's currently *published* `schedule_version` and stamped `PUBLISHED` in the same transaction — no waiting on the next official timetable cut. REGULAR allocations are created against a `DRAFT` version and only become visible when the Lab Assistant explicitly publishes it.
**Reason:** Waiting for a scheduled publish would defeat the purpose of fast, FCFS makeup-lab booking; a CR needs to see (and a student needs to see) a validly-booked extra lab right away.
**Impact:** A published `ScheduleVersion` is not fully frozen — it can continue to accumulate new EXTRA allocation rows after its initial publish. This is intentional, not a bug, and should be called out explicitly in any Phase 18 versioning-history UI so it isn't mistaken for one.

### A-12: Concurrency mechanism left undecided until Phase 16
**Assumption:** ADR-010 in docs/15-DESIGN-DECISIONS.md intentionally does not commit to a final PostgreSQL locking/constraint mechanism yet — it leans toward a range-overlap exclusion constraint (`btree_gist`) combined with transactional revalidation, but names this "provisional."
**Reason:** PART 34 asks to "evaluate" multiple strategies; committing now without implementing/testing would risk documenting something untested as fact, which the working rules explicitly forbid ("do not claim something works unless verified").
**Impact:** Phase 16 must rewrite ADR-010 with the actual chosen mechanism and link to the passing concurrent-request integration test before this assumption is considered resolved.

### A-13: Class strength lives on Batch/Division, no Student entity
**Assumption:** Capacity checks (HC-07) read a plain `strength` integer maintained on `batch`/`division` by the Lab Assistant; no `Student` entity is created in the initial system.
**Reason:** The phase brief explicitly warns against creating thousands of student rows just to compute a headcount; a single maintained number is realistic, implementable, and demoable.
**Impact:** If per-student features (attendance, individual views) are ever required, a `Student` entity is a additive migration, not a rework — tracked in [18-FUTURE-IMPROVEMENTS.md](18-FUTURE-IMPROVEMENTS.md).

### A-14: Surrogate keys are `BIGINT` identity columns, not UUIDs
**Assumption:** All primary keys use auto-incrementing `BIGINT`, not `UUID`.
**Reason:** No cross-system/offline-generation requirement exists that would need UUIDs; `BIGINT` keeps indexes smaller and joins cheaper for a single-database modular monolith, and is simpler to reason about in demo/viva walkthroughs.
**Impact:** Reversible per-table if a specific entity later needs client-generated IDs (unlikely for this domain).

### A-15: [SUPERSEDED in Phase 7 — see A-32] Faculty availability is optionally term-scoped, not mandatorily
**Assumption:** `faculty_availability.academic_term_id` is nullable; a null value means "applies every term."
**Reason:** Matches how availability is actually communicated by faculty (recurring weekly pattern) without forcing re-entry every term for the common case; term-scoped overrides remain possible when needed.
**Impact:** Scheduling context queries must handle both scoped and unscoped rows (`term_id = :term OR term_id IS NULL`) — documented so this isn't missed when the repository query is implemented in Phase 7.
**Superseded:** Phase 7 implemented `academic_term_id` as mandatory instead, per the phase brief's explicit recommendation — see A-32 below and ADR-031 in [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md). This entry is kept, not deleted, per this project's standing practice of recording changed decisions rather than erasing them.

### A-16: Lab unavailability is date-level, not datetime-level
**Assumption:** `lab_unavailability` uses `start_date`/`end_date` (whole days), not `start_datetime`/`end_datetime`.
**Reason:** Maintenance windows in practice are described as "closed March 3–5," not sub-day; this keeps HC-06 checks simpler (date-range containment, no time-of-day overlap math) for the initial system.
**Impact:** If a lab ever needs a same-day partial closure (e.g. "unavailable 2–4pm for inspection"), this table would need a datetime variant — noted for future revisit, not built now.

## Phase 2 — Project Foundation (2026-08-21)

### A-17: Spring Boot 4.1.1 instead of the originally planned "3.x"
**Assumption/change:** The backend targets Spring Boot 4.1.1, not 3.x as A-04 originally framed it.
**Reason:** Not discretionary — `start.spring.io` rejected Spring Boot 3.3.x as below its currently supported version range at the time this phase's scaffolding was generated (2026-08-21); 4.1.1 was the latest stable release offered. This reflects the real state of the Spring ecosystem now, not a preference.
**Impact:** Some artifact/package names differ from Spring Boot 3.x conventions (e.g. `spring-boot-starter-webmvc` not `spring-boot-starter-web`; `org.springframework.boot.resttestclient.TestRestTemplate` not `org.springframework.boot.test.web.client.TestRestTemplate`; `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` not `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`). Documented in [03-SYSTEM-ARCHITECTURE.md §10](03-SYSTEM-ARCHITECTURE.md) so this isn't mistaken for an error against Spring Boot 3.x reference material. Superseded A-04's Spring Boot framing; Java 21 and Node version framing in A-04 are otherwise unaffected.

### A-18: Node v24.14.0 used for local development instead of planned Node 20 LTS
**Assumption:** Local development uses whatever Node is already on the machine (v24.14.0), rather than blocking on installing Node 20 LTS specifically.
**Reason:** It built, tested, and ran the Vite/React toolchain without any compatibility issue; forcing a Node 20 install would have added friction for no observed benefit.
**Impact:** The Docker frontend build stage still pins `node:20-alpine` explicitly, so the *containerized* build remains reproducible and version-pinned regardless of the host machine's Node version — only bare-metal local dev uses the host's newer Node.

### A-19: Testcontainers/`docker-java` cannot reach this machine's Docker daemon — separated into Failsafe, not fixed
**Assumption/limitation:** Rather than continuing to chase a fix for what appears to be a `docker-java` client / Docker Desktop 4.86.0 (Engine 29.7.2, API 1.55) compatibility gap (see full diagnosis in [13-DEVELOPER-SETUP.md](13-DEVELOPER-SETUP.md) Known Limitations), the Testcontainers-backed integration test was structurally separated (via Maven Failsafe, `*IT` naming) from the default fast build/test path, per ADR-014.
**Reason:** The `docker` CLI and `docker compose` both work correctly on this machine (verified directly); only the Java `docker-java` library's connection negotiation fails, across three different transport configurations tried (default named pipe, explicit named pipe, explicit TCP). This is very likely to work correctly in other environments (e.g. Linux CI runners) where Testcontainers is most commonly and reliably used. Spending further time patching around one local Windows machine's Docker Desktop version was judged not worth it versus structuring the build so it doesn't block on this at all.
**Impact:** `mvn test`/`mvn package` are unaffected and pass on this machine; `mvn verify` (which includes the Testcontainers IT) will only succeed in an environment where this compatibility issue doesn't exist. Phase 27's CI pipeline should verify `mvn verify` actually passes in that environment (likely Linux-based GitHub Actions runners) as part of its own setup.

### A-20: [RESOLVED in Phase 3] Docker Desktop's TCP API exposure (`exposeDockerAPIOnTCP2375`) was enabled during Phase 2 diagnosis, left on
**Original assumption:** Acceptable to leave this local-machine setting enabled rather than reverting it, since this is a personal development machine, not a shared/production host.
**Resolution (Phase 3):** Disabled again as part of Phase 3's pre-work safety check (the phase brief explicitly required verifying it was off "unless explicitly required for a verified workflow" — it wasn't; the Testcontainers issue was reproduced with it both on and off). Docker Desktop was restarted afterward and `docker version`/`docker compose up` were re-verified working normally through the standard mechanism.
**Impact:** No unauthenticated Docker Engine API is exposed on this machine as of Phase 3.

## Phase 3 — Authentication + RBAC (2026-08-21/22)

### A-21: Spring Boot 4 ships Jackson 3 under the `tools.jackson.*` namespace, not `com.fasterxml.jackson.*`
**Assumption/discovery:** `ObjectMapper` and related Jackson classes needed by hand-written Spring Security error handlers (`RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`) are `tools.jackson.databind.ObjectMapper`, not the classic `com.fasterxml.jackson.databind.ObjectMapper` — the classic package is present in the dependency tree (as a transitive/test-scope artifact) but is not what Spring Boot 4's own web stack actually uses.
**Reason:** Discovered via a compile failure ("package com.fasterxml.jackson.databind does not exist") and confirmed by inspecting the actual downloaded jar's class listing.
**Impact:** Any hand-written code in this project that needs Jackson directly (rather than going through Spring's `HttpMessageConverter` machinery automatically) must import from `tools.jackson.*`. Documented here so this isn't re-discovered painfully in a later phase.

### A-22: jjwt (`io.jsonwebtoken`) chosen as the JWT library, version 0.12.6
**Assumption:** Spring Security itself does not include JWT encode/decode support for a custom (non-OAuth2) login flow — that support is scoped to Spring Security's OAuth2 Resource Server module, which is not the architecture here (this project issues and validates its own tokens directly, not via an external authorization server). `io.jsonwebtoken:jjwt-api`/`jjwt-impl`/`jjwt-jackson` was added explicitly for this.
**Reason:** jjwt is the most widely used, well-documented Java JWT library for exactly this "roll your own login, issue your own JWT" pattern, and its API (`Jwts.builder()`/`Jwts.parser()`) maps directly onto the claims design in docs/09-AUTHORIZATION-RBAC.md.
**Impact:** `jjwt-jackson` (the JSON provider `jjwt` uses internally) pulls in the classic `com.fasterxml.jackson` artifacts as a transitive dependency — unrelated to, and not a fix for, A-21's Jackson-3-namespace issue in this project's own hand-written code.

## Phase 4 — Academic Domain (2026-08-22)

### A-23: `@PreAuthorize` denials need an explicit `GlobalExceptionHandler` case — real bug found and fixed
**Discovery:** During Docker verification, `POST /api/programs` as a CR or STUDENT (both correctly denied by `@PreAuthorize("hasRole('LAB_ASSISTANT')")`) returned `500 INTERNAL_ERROR` instead of the expected `403 FORBIDDEN`. Root cause: `@PreAuthorize` denials throw `AuthorizationDeniedException` from deep inside the controller method invocation (the AOP proxy), which is still within Spring MVC's own dispatch — `GlobalExceptionHandler`'s `@RestControllerAdvice` catches it there via its generic `Exception` handler *before* the exception can ever propagate out to Spring Security's `ExceptionTranslationFilter`/`RestAccessDeniedHandler` (Phase 3), which only ever sees exceptions that escape the whole dispatch unhandled.
**Fix:** Added an explicit `@ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})` to `GlobalExceptionHandler` mapping to `403 FORBIDDEN` directly. `RestAccessDeniedHandler` (Phase 3) remains in place for URL-level `authorizeHttpRequests` denials, a genuinely different code path that runs before dispatch begins and is unaffected by this bug.
**Impact:** This was caught by actually exercising the real endpoints against a running Dockerized instance (per this phase's explicit "manually exercise representative academic APIs" instruction), not by unit tests alone — the removed Phase 3 `RoleAuthorizationTest` fixture (pure method-security, no web layer) would never have caught this, since it never went through `DispatcherServlet`/`@RestControllerAdvice` at all. Worth remembering: method-security + a global `@RestControllerAdvice` interact in a way that needs an end-to-end HTTP-level test to actually verify, not just a unit-level authorization check.

### A-24: Lazy-loaded associations in response DTOs need `@Transactional(readOnly = true)` on service read methods — real bug found and fixed
**Discovery:** Also during Docker verification, `GET /api/cr-assignments/me` returned `500` with a `LazyInitializationException` ("no session") when mapping `CrAssignment` to `CurrentCrAssignmentResponse`, which dereferences `division.getAcademicYear().getStream().getProgram()`. With `spring.jpa.open-in-view: false` (set in `application.yml` since Phase 2 — deliberate, not the cause of the bug), the Hibernate session closes when the repository call returns; any lazy association not yet initialized by then throws if touched afterward. `StreamService.get()`, `AcademicYearService.get()`, `DivisionService.get()`, `BatchService.get()`, `SubjectService.get()`, `CrAssignmentService.list*()`, and `SubjectFacultyAssignmentService.get()` all had this same latent bug — their `*Response.from(entity)` mapping touches a lazy parent association, and none of the plain (non-mutating) service methods were `@Transactional`.
**Fix:** Added class-level `@Transactional(readOnly = true)` to every academic/subject/faculty service (mutating methods already override it with their own `@Transactional`), so the DTO mapping happens while the session is still open. For `CrOwnershipService` specifically, the fix also required moving the DTO mapping *into* the service (`getCurrentAssignmentResponse`), since the controller previously called `getCurrentAssignment()` then mapped to a DTO afterward, outside any transaction.
**Impact:** `open-in-view: false` did exactly what it's for — surfaced a real bug loudly at request time instead of silently allowing an N+1-prone anti-pattern (implicit lazy loading during response serialization) to work "by accident" and mask itself until a slower production N+1 query pattern was noticed much later. Confirmed fixed by re-running the exact same manual verification steps against the rebuilt image.

## Phase 5 — Laboratory Domain (2026-08-22)

### A-25: Pre-phase academic-year count verified — no seed bug, "25" figure did not match reality
**Assumption/discovery:** Before writing any Phase 5 code, the persisted `academic_year` row count was inspected directly in the running database (via `docker compose up -d postgres` against the volume left over from Phase 4) rather than assumed from the phase brief's mention of "25 academic_year rows." Actual result: **22 rows**, broken down as 4 B.Tech streams (CE/CS/IT/AIML) × 4 years = 16, plus 2 MBA Tech streams (CE/DS) × 3 years = 6, total 22 — matching `DevAcademicSeeder`'s two nested loops exactly, with no duplicate rows and no seed bug.
**Reason:** The Phase 5 brief explicitly required inspecting and documenting the real count rather than modifying the schema/seed "merely because a count looks unexpected." An initial diagnostic query grouping by `stream.code` alone showed `CE: 7`, which looks anomalous until re-run grouped by `(program.code, stream.code)` — it's simply the sum of two *different* streams that happen to share the code "CE" under two different programs (BTECH's CE=4 + MBATECH's CE=3=7), exactly the "CS under B.Tech and CS under another program could coexist" scenario Phase 4's own design docs anticipated. Not a bug; a diagnostic-query artifact.
**Impact:** No schema or seed change was made. This is recorded here specifically so a future reader who also sees an unexpected-looking "25" (or any other number) in a prompt or note knows to verify against the live database and the actual per-program-scoped breakdown before assuming either the number or the design is wrong.

### A-26: Lab location kept flat (`wing`/`floor`/`roomNumber`), no campus/building hierarchy
See ADR-022 in [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md) for the full reasoning — recorded here as the assumption underlying it: the college is assumed to remain a single campus/single building for the scope of this project. If that assumption breaks (a second building or campus), `wing` alone becomes ambiguous and the location model would need revisiting (tracked in [18-FUTURE-IMPROVEMENTS.md](18-FUTURE-IMPROVEMENTS.md)).

### A-27: `lab_unavailability` implemented with full `TIMESTAMPTZ` granularity, superseding the Phase 1 draft's "date-level is sufficient" framing
**Assumption/change:** [04-DATABASE-DESIGN.md](04-DATABASE-DESIGN.md) originally sketched date-level granularity as sufficient for lab maintenance windows. Phase 5 implemented full datetime (`TIMESTAMPTZ`) granularity instead.
**Reason:** The Phase 5 brief explicitly instructed using proper Java temporal types with clear half-open interval semantics, and a same-day partial closure (e.g. "unavailable 2–4pm for inspection") is realistic enough that the extra precision costs nothing to include from the start — cheaper than migrating from `DATE` to `TIMESTAMPTZ` later once real unavailability data exists.
**Impact:** `docs/04-DATABASE-DESIGN.md` §4 updated to reflect this as the actual implementation, with the supersession noted explicitly rather than silently changed.

### A-28: Subject requirements are subject-level, not per-term
**Assumption:** `SubjectSoftwareRequirement`, `SubjectEquipmentRequirement`, and lab-type requirement fields attach to `Subject` directly, not to any per-term offering concept.
**Reason:** See ADR-025 in [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md) — a subject's tooling needs are treated as a curriculum fact, consistent with ADR-018's earlier rejection of a `SubjectOffering` entity.
**Impact:** If a future curriculum change needs term-scoped requirement variation, this assumption would need revisiting alongside ADR-018.

### A-29: No boolean "required" flag on requirement rows — every row is a hard requirement
**Assumption:** `subject_software_requirement`/`subject_equipment_requirement` rows are unconditionally hard (ALL-semantics) requirements; "nice-to-have" software/equipment is left unmodeled in Phase 6.
**Reason:** See ADR-026 — no consumer for a soft/preferred capability flag exists yet; docs/07-ALLOCATION-SCORING.md's "Additional Environment Fit" factor remains a design placeholder until a later phase gives it real data to read.
**Impact:** Adding soft/preferred capability support later requires a new column or table, not just a data change.

### A-30: Lab-type requirement modeled as nullable FK columns on `subject`, not a join table
**Assumption:** `required_lab_type_id`/`preferred_lab_type_id` are two nullable `BIGINT` columns directly on `subject`, mirroring `Lab.labType`, rather than a join table like the software/equipment requirements use.
**Reason:** See ADR-027 — lab-type requirement is genuinely single-valued per subject per role, so a join table would allow a structurally nonsensical multi-row state.
**Impact:** None expected — this mirrors an existing, already-proven pattern (`Lab.labType`) rather than introducing a new one.

### A-31: Software/equipment version matching deferred — identity/code match only
**Assumption:** Requirements match `Software`/`Equipment` by identity (FK) only; `LabSoftware.installedVersion` (Phase 5) is stored but never compared against a requirement-side version constraint in Phase 6.
**Reason:** See ADR-030 — no seeded or real scenario in this project's scope needs version-level discrimination, and a version-comparison concept would be speculative with no consumer.
**Impact:** A future phase needing "lab has Cloudera >= 6.3"-style matching will require a new column/comparison operator on the requirement row — a real, acknowledged gap.

### A-32: Faculty availability term-scoping made mandatory, superseding A-15
**Assumption:** `faculty_availability.academic_term_id` is `NOT NULL` — every availability row belongs to exactly one term; there is no "applies every term" row.
**Reason:** See ADR-031 — a faculty's real availability genuinely changes semester to semester, and this now matches how every other term-relative fact in this project (`SubjectFacultyAssignment`, `CrAssignment`) is already modeled.
**Impact:** A Lab Assistant must re-enter (or a future UI must offer to copy) availability for each new term — a real, accepted data-entry cost, documented in ADR-031's trade-offs.

### A-33: Faculty availability overlap prevention is application-only, no PostgreSQL exclusion constraint
**Assumption:** Overlapping active `faculty_availability` rows for the same faculty/term/day are rejected by `FacultyAvailabilityService` at write time; no `EXCLUDE USING gist` constraint or generated range column exists in the schema.
**Reason:** See ADR-032 — PostgreSQL has no native recurring-weekly range type, and availability data is low-volume/administratively mutated, so the added complexity of a generated-range + exclusion constraint was judged not worth it at this scale.
**Impact:** Under hypothetical high-concurrency writes to the same faculty/term/day, a TOCTOU race exists that a database-level exclusion constraint would close — accepted as a non-issue at this project's actual mutation pattern, noted rather than silently assumed safe.

### A-34: Faculty availability read access restricted to LAB_ASSISTANT, narrower than Phase 5/6's open-read pattern
**Assumption:** `GET /api/faculty/{id}/availability*` requires `LAB_ASSISTANT`, unlike Labs (Phase 5) and Subject Requirements (Phase 6) where any authenticated role can read.
**Reason:** See ADR-034 — raw availability management data has no demonstrated CR/STUDENT consumer yet; the future constraint engine will consume it internally via `FacultyAvailabilityService`, not through this REST surface.
**Impact:** If a future phase needs CR-visible availability (e.g. explaining a failed extra-lab request), this is a straightforward, backward-compatible loosening of `@PreAuthorize` — not a breaking change, but not yet built on a guess either.

## Phase 13 — Conflict Detection + Alternative Suggestions (2026-08-23)

### A-35: College scheduling-slot rules did not exist anywhere in the repository — collected directly from the user
**Assumption/discovery:** Before Phase 13, no document, entity, or configuration anywhere in this repository defined authoritative college scheduling-slot rules (working days, daily start/end times, standard session duration, whether sessions must align to fixed slots or may start at arbitrary times, whether alternative search may cross to another day, or a maximum search window). The only slot-like text anywhere was a single illustrative example ("Monday 09:00–11:00") in docs/01-PROJECT-OVERVIEW.md, never stated as a rule. Per the Phase 13 brief's explicit instruction not to fabricate these, they were collected directly from the user via a clarifying question rather than assumed.
**Resolution:** Sessions run in fixed 2-hour blocks, starting on any whole hour; the day runs 09:00–19:00 (so valid start hours are 09, 10, 11, ..., 17 — the latest start whose 2-hour session still ends by 19:00). Working days are Monday through Saturday (Sunday excluded). Alternative search stays same-day first, then looks ahead up to a small number of additional working days if the requested day alone yields nothing — the user specified "up to N, a small number like 2–3" without naming an exact N, so this project defaults `max-lookahead-days` to 3, documented as its own reasonable choice within the user's stated bound, not a guess about actual college policy. Alternative-time search is bounded to 6 slots searched and 3 suggestions returned, both also given directly by the user.
**Impact:** `SchedulingSlotPolicy` (`app.scheduling.*` in `application.yml`) is the single, centralized, configurable place these rules live — see docs/05-SCHEDULING-ENGINE.md and ADR-056 in docs/15-DESIGN-DECISIONS.md. If real college policy is ever confirmed to differ (e.g. genuinely arbitrary-minute start times, a real lunch-break exclusion, or a different day count), only this one component's configuration needs to change — no scoring/constraint/generation code depends on the specific values.

*New assumptions will be appended here as they arise in later phases, each with Reason and Impact noted.*
