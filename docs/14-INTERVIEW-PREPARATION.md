# Interview Preparation

**Status: Phase 28 — consolidated and verified against the actual codebase, docs/11, docs/12, docs/15, docs/16, and live-verified behavior through Phase 27.** This is a rewrite, not an append — earlier phases' Q&A material was reviewed, deduplicated, corrected where stale, and reorganized into one coherent guide. Every fact below traces to real code, a real test, or a real, executed measurement. Where something is a design intention rather than an observed result (most notably: GitHub Actions has not yet run, since no push has occurred), that distinction is stated explicitly, not blurred.

A note before anything else: **do not memorize this document sentence-by-sentence.** See "Avoid Memorization Traps" (§23) for what's actually worth committing to memory, and explain the rest naturally, in your own words, from genuine understanding of the system.

---

## 1. Quick Facts

| Fact | Value |
|---|---|
| One-line description | A full-stack college lab-scheduling platform that allocates labs under faculty, capacity, software, equipment, and availability constraints, with concurrency-safe booking, versioned timetables, audit history, PDF import, and honest analytics |
| Backend | Java 21, Spring Boot 4.1.1, Spring Data JPA, Spring Security, Flyway |
| Frontend | React 19, TypeScript, Vite, Tailwind, React Router, TanStack Query |
| Database | PostgreSQL 16 |
| Architecture | Modular monolith (not microservices) |
| Backend tests | **300** (0 failures, 0 errors) |
| Frontend tests | **69** (0 failures) |
| Integration test classes | **19** `*IT`/`*ConcurrencyIT` classes (Testcontainers-backed) |
| Hard constraints | **12** (`HC-01`–`HC-12`), one class each |
| Scoring factors | **3** implemented (Capacity Fit 30, Preferred Lab Type 15, Balanced Utilization 15 — 60 total, not artificially padded to 100) |
| Demo dataset | 15 labs (3 with Cloudera-equivalent software: B-201, C-202, B-301), 1 division, 3 batches, 2 subjects, 2 faculty |
| Deployment | Docker Compose: React/nginx + Spring Boot + PostgreSQL, health-aware startup, named-volume persistence |
| CI/CD | GitHub Actions workflow written and locally validated; **not yet observed running on GitHub** (no push has occurred) |
| Design decisions documented | 139 numbered ADRs across `docs/15-DESIGN-DECISIONS.md` |

---

## 2. 30-Second Pitch

"I built a constraint-based lab scheduling system for a college's multi-program academic structure — not a booking form. Every request, whether it's the official semester timetable or a class representative booking an ad-hoc makeup lab, runs through the same constraint engine: capacity, required software and equipment, lab type, faculty and lab availability, and conflict detection. Valid candidates get ranked by a scoring model, and a backtracking search can auto-generate a full multi-session timetable when a greedy pass would fail. The part I'm proudest of is the concurrency story: two people can race to book the same lab and slot, and PostgreSQL — not application code — guarantees exactly one of them wins, which I proved with genuinely parallel requests, not just sequential tests standing in for a race."

---

## 3. 2-Minute Explanation

**Problem:** A college has a shared pool of labs, several programs/streams/divisions/batches, and needs to schedule practicals so nothing double-books a lab, a faculty member, or a student batch — while still allowing legitimate simultaneous use (different batches, different labs, different faculty, same time). On top of the official semester timetable, class representatives book ad-hoc makeup labs on a first-come, first-served basis, checked live against everything already booked.

**Users/roles:** Three roles, each with a genuinely different frontend and backend authorization scope — Lab Assistant (full administrative access: lab/faculty/subject data, CR assignment, timetable-version publication, PDF import, analytics, audit log), CR (their own division's timetable, plus search/book/cancel extra labs — scope resolved server-side from their authenticated assignment, never from a client-supplied division id), and Student (read-only access to the currently published timetable).

**Architecture:** React/TypeScript frontend, Spring Boot backend, PostgreSQL — a modular monolith, not microservices, because there's no independent-scaling need at this size and keeping the booking transaction inside one process/database makes correctness far easier to guarantee than a distributed alternative would.

**Scheduling engine:** Twelve independently-testable hard constraints filter candidate labs (lab/faculty conflict, faculty/lab availability, batch and division-wide conflict, capacity, required software/equipment/lab type, academic-relationship and CR-authorization validity); a weighted scoring model ranks the valid survivors; a most-constrained-first backtracking search can generate a full multi-session schedule, backtracking when a later session runs out of options.

**Concurrency/versioning:** Booking is protected by real PostgreSQL exclusion constraints plus a per-division pessimistic lock — proven with genuinely parallel HTTP requests, not sequential ones. The official timetable is versioned (`DRAFT → PUBLISHED → SUPERSEDED`), with a database-enforced invariant that exactly one version per term can be published at a time; old versions are preserved, never deleted.

**Testing/deployment:** 300 backend tests and 69 frontend tests, layered unit/integration/security/concurrency coverage, live Docker verification at every phase, a production-shaped Docker Compose deployment with health checks and a `prod`-profile secret guard, and a GitHub Actions CI pipeline (written and locally validated; GitHub-hosted execution not yet observed).

---

## 4. 5-Minute Deep Dive

Use this as talking points, not a script — expand whichever the interviewer leans into.

- **Domain model:** `Program → Stream → AcademicYear → Division → Batch` (real FK-linked tables, not a denormalized "class" field), `AcademicTerm` as an independent concept from `AcademicYear` (semester vs. study-year — a real naming trap the model was designed to avoid), `Subject`/`Faculty`/`SubjectFacultyAssignment` (batch-level assignment overrides division-level, resolved by exact-match-first), `Lab`/`LabType`/`Software`/`Equipment`/`LabUnavailability`, `FacultyAvailability` (a declared weekly boundary, not a booked session), `ScheduleVersion`/`Allocation` (see below), `TimetableImport`/`TimetableImportRow`, `AuditLog`.
- **Constraint engine:** One class per hard constraint (`ConstraintEngine` runs all twelve, never fail-fast, so every rejection reason is available at once); a `CandidateAllocation` is evaluated, never persisted, until it's the winning choice.
- **Candidate generation:** Builds one candidate per lab, evaluates every one through the real constraint engine (never "return the first valid lab" — that's exactly the naive system this project avoids), keeps both valid and rejected candidates with their reasons attached.
- **Scheduler:** Most-constrained-first (MRV) ordering, depth-first backtracking with immutable in-memory search state (never a database write mid-search), bounded by a configurable node limit.
- **PostgreSQL protection:** Three `EXCLUDE` constraints (lab/faculty/batch × time-range overlap) plus a per-division pessimistic lock for the one invariant that can't be a symmetric exclusion constraint (DIVISION-vs-BATCH cross-type conflict).
- **Role workflows:** CR's division is always server-resolved (`GET /api/cr-assignments/me`); Student's timetable always resolves by `status = PUBLISHED`, never `MAX(version_number)`.
- **PDF import:** Extraction → parsing → normalization → mapping → constraint validation → staging → correction → atomic approval → separate publication. Nothing is inserted until a human confirms it.
- **Analytics:** Weighted utilization (`SUM(booked)/SUM(available)`, never an average of percentages), scoped to the current published version, and an honest refusal to fabricate conflict/success-rate data that isn't actually persisted.
- **Testing:** 300 backend + 69 frontend, plus live Docker verification, clean-database Flyway verification, and Phase 25's real performance benchmarks.
- **Performance:** Scheduler ~18ms median for 10 easy requirements, ~1.1s for 100; booking ~39ms; concurrent contention correctly serializes with exactly one winner every time.

---

## 5. Architecture

```text
React 19 + TypeScript (Vite, TanStack Query, React Router)
        ↓ REST/JSON (bearer JWT)
Spring Boot 4.1.1 modular monolith (Java 21)
   packages: academic / lab / subject / faculty / scheduling
             (constraint / generation / scoring / explanation /
              conflict / alternative / automatic / extra)
             audit / timetableimport / analytics / security / user
        ↓ Spring Data JPA
PostgreSQL 16 (Flyway-migrated, V1–V14)
```

Also real and in place: JWT authentication (HS256, `io.jsonwebtoken`), Spring Security method-level RBAC (`@PreAuthorize`), Flyway (schema is the only source of truth — `ddl-auto: validate`, never auto-generated), Docker/Docker Compose (multi-stage builds, health checks, named-volume persistence), GitHub Actions (workflow written and locally validated, execution not yet observed).

### Why a modular monolith, not microservices?

Everything in this system shares one team, one deployment unit, and — most importantly — one transaction boundary the FCFS booking guarantee depends on. A booking's "revalidate, then insert, inside one transaction with a database-level exclusion constraint" story only works cleanly because it's one database one process is talking to; splitting scheduling and booking into separate services would turn that into a distributed-transaction problem for zero actual benefit at this scale. The domain is still cleanly separated *within* the monolith (`constraint`/`generation`/`scoring`/`extra`/`audit`/`analytics` are real, independent packages with narrow interfaces between them) — modularity was chosen at the package level, not the deployment level, because nothing here needs independent scaling or independent deployment. If one subsystem ever did (e.g. the scheduler needing dedicated compute for a much larger problem size), that's an argument for extracting *that one piece* later, with real evidence, not a reason to start distributed from day one.

### Technology choices

**Why Java/Spring Boot?** Strong static typing suits a domain this relationally strict (labs, faculty, batches, terms all cross-reference each other); Spring's transaction management is what makes the FCFS/concurrency guarantee tractable; the JUnit/Mockito/Testcontainers ecosystem lets the scheduling engine be tested in real isolation from the web and persistence layers.

**Why React + TypeScript?** Component-based UI matches three genuinely different role-scoped applications sharing common primitives (`DataTable`, `AsyncSection`, route guards); TypeScript catches an entire class of "the backend renamed a field" bugs at compile time rather than in a browser console.

**Why PostgreSQL?** The data is inherently relational (foreign keys everywhere), and the concurrency-safety requirement depends directly on transactional/locking/exclusion-constraint guarantees a relational database provides natively — a document store would push conflict prevention entirely into application code, recreating the exact check-then-insert race this project is built to eliminate.

**Why Flyway?** Versioned, forward-only, ordered migrations mean the schema's history is itself reviewable and reproducible from a clean database — verified directly, live, in Phase 24/26 (empty Postgres → `V1`...`V14` applied in order → `Hibernate validate` passes cleanly).

**Why Docker?** Reproducible from a fresh checkout with no host-installed Java/Maven/Node — the build tooling lives entirely inside multi-stage Dockerfiles.

**Why GitHub Actions?** Free for a public/personal repository, native Docker on Linux runners (which is *why* Testcontainers-backed integration tests, blocked in this project's local sandbox, should finally run there), and no separate CI infrastructure to operate.

---

## 6. Domain / Data Model

```text
Program → Stream → AcademicYear → Division → Batch      AcademicTerm (independent)
Subject ←── SubjectFacultyAssignment ──→ Faculty
Lab ←→ LabType, Software (via LabSoftware), Equipment (via LabEquipment), LabUnavailability
Faculty ──→ FacultyAvailability (declared weekly windows, term-scoped)

ScheduleVersion (DRAFT/PUBLISHED/SUPERSEDED) ──1:N──→ Allocation (targetType: DIVISION | BATCH)
TimetableImport ──1:N──→ TimetableImportRow (staged, correctable, never a real Allocation until approved)
AppUser (LAB_ASSISTANT | CR | STUDENT) ──→ CrAssignment (a CR's own division, historically preserved)
AuditLog (append-only, immutable, references every actor/action/resource above)
```

Relationships that matter, not columns: `SubjectFacultyAssignment`'s optional `batch` means "division-wide unless a specific batch overrides it," resolved exact-match-first; `Faculty` has no login identity at all (never an `AppUser` row — they're referenced data, not accounts); `Program`/`Stream`/`LabType` are real database rows an administrator can extend without a code deploy, while `UserRole`/`TermStatus`/`AllocationStatus` are genuinely stable Java enums the application's own logic branches on.

### Allocation model

An `Allocation` targets either a whole `DIVISION` or one specific `BATCH` (an explicit `targetType` enum column, backed by both application-level factory methods and a database `CHECK` constraint — never an implicit "null batch means division-wide" convention). This is the single design decision that makes "two different batches of the same division can share a time slot, as long as their lab and faculty don't collide" correct: batch-vs-batch conflict and division-wide-vs-anything conflict are two genuinely different checks (`BatchConflictConstraint`/`DivisionWideConflictConstraint`), each scoped correctly.

**The Phase 22 fix:** a batch-scoped timetable query (`AllocationSpecifications.batchId`) was a strict `batch.id = batchId` equality — correct for a Lab Assistant's precise administrative filter, but wrong for "what does this batch's timetable look like," because it silently excluded every division-wide session the batch's students actually attend. Fixed with `batchIdOrDivisionWide` (`batch.id = :batchId OR batch IS NULL`), applied only to the student/CR-facing query, leaving the administrative one untouched. Found by re-reading the filter against the actual requirement before writing any UI code — not by a failing test.

---

## 7. Scheduling & Constraints

Twelve hard constraints, one class each (`HC-01`–`HC-12`): lab conflict, faculty conflict, faculty availability, batch conflict, division-wide conflict, lab availability (temporary unavailability windows), capacity, required software, required equipment, required lab type, academic-relationship validity, CR authorization. Each is independently unit-testable; `ConstraintEngine` runs all twelve against every candidate, never fail-fast, so a candidate failing for two reasons reports both — better UX and better debugging than "here's the first thing wrong."

Candidate generation (`CandidateGenerator`) builds one `CandidateAllocation` per lab in the system for a request, evaluates every single one through the real engine, and keeps both valid and invalid candidates — rejected ones retain their full structured violation list, so "why isn't C-304 an option?" has a real answer, not silence.

### The BDA/Cloudera example

BDA (Big Data Analytics) requires Cloudera. Of the 15 labs in the demo dataset, 3 have Cloudera-equivalent software installed (B-201, C-202, B-301 — verified directly via `GET /api/labs?software=CLOUDERA`). A real search against this data (Phase 25 benchmark, `docs/16-PERFORMANCE-BENCHMARKS.md`) examined all 15, rejected 12 for `SOFTWARE_MISMATCH`, and returned exactly those 3 as ranked valid candidates — matching the seeded data precisely, not a hypothetical.

---

## 8. Backtracking Algorithm

### Why not greedy?

A greedy, fixed-order pass can commit an earlier requirement to a lab a later, more-constrained requirement needed exclusively — by the time the later one is reached, its only option is already taken, and greedy has no way to reconsider. Proven directly by the flagship scenario: R1 can use lab X or Y; R2 can only use X. Fixed order (R1 first, no backtracking) assigns R1→X, then R2 fails — even though R1→Y / R2→X is a fully valid schedule.

### Whiteboard version

```text
schedule(requirements):
    if all assigned:
        return success

    r = mostConstrained(requirements)      # MRV — fewest remaining valid candidates, recomputed every node
    candidates = rank(validCandidates(r))  # already-valid candidates only, ranked by score

    for candidate in candidates:
        assign(candidate)                  # provisional, in-memory only

        if schedule(remaining):
            return success

        undo(candidate)                    # immutable search state — "undo" costs nothing but discarding a reference

    return failure
```

- **Most-constrained-first (MRV):** schedule the requirement with the fewest valid choices first, recomputed fresh at every search node (not a static ordering decided once) — this is exactly what usually lets the search avoid the backtrack the flagship scenario needs when order is otherwise fixed.
- **Search state:** `SchedulingSearchState` is a plain immutable record of provisional decisions — never a database write mid-search, which is both a correctness requirement (a rolled-back write is still momentarily visible to a concurrent reader) and what makes "undo" free.
- **Ranking, never overriding:** scoring only ever decides *which order* to try candidates that already passed every hard constraint — it can never make an invalid candidate selectable, because `computeChoices` only ever receives candidates the real `ConstraintEngine` already validated.
- **Bounded search:** a configurable `maxNodes` (default 2000) stops the search and reports `SEARCH_LIMIT_REACHED` — distinct from `NO_SOLUTION` (search space genuinely exhausted, proven infeasible). Measured directly: `maxNodes=5` against a solvable 20-requirement workload stopped after visiting **6 nodes** (the limit plus the one node that detects it was hit) and correctly reported `SEARCH_LIMIT_REACHED`, in 50ms — never a false `NO_SOLUTION`, never a hang.
- **Pruning, honestly scoped:** this means ordinary constraint-driven branch rejection — a candidate that fails a hard constraint is never explored further down that branch. No advanced CSP techniques (constraint propagation, arc consistency) are implemented; don't claim them.

### Performance (Phase 25, `docs/16-PERFORMANCE-BENCHMARKS.md`)

| Scenario | Requirements | Median | Backtracks |
|---|---|---|---|
| Easy | 10 | ~18ms | 0 |
| Constrained | 10 | ~53ms | 0 |
| Forced backtracking | 10 (5 pairs) | <1ms | 5 (exactly one per pair) |
| Scaling | 100 | ~1.1s | 0 |

Nodes explored grew exactly linearly with requirement count (10→100 requirements, 11→101 nodes). Wall-clock time grew faster than linear (~57x time for a 10x requirement increase) — attributed to MRV re-evaluating every *remaining* requirement's candidate count at each node, so per-node cost itself grows as the search progresses. **This is an observed scaling result from four data points, not a formal complexity proof** — say exactly that if asked.

---

## 9. Concurrency & Transactions

### The question: what happens if two CRs book the same lab at the same time?

Not "we checked before insert" — that's race-prone by construction (two transactions can each read "lab free" before either commits, a classic write-skew anomaly). The real answer is two layers:

1. **Application-level revalidation:** every booking re-runs the full constraint engine against current data, inside the same transaction as the insert — closes the "stale search result" gap (Phase 15).
2. **Database-level backstop (Phase 16):** three PostgreSQL `EXCLUDE` constraints (`lab_id`/`faculty_id`/`batch_id`, each `WITH =`, combined with a `tsrange(...) WITH &&` time-overlap check, via a GiST index requiring `btree_gist`) reject a conflicting insert at commit time regardless of what any application code believed. For the one invariant that can't be a symmetric exclusion constraint — DIVISION-vs-BATCH cross-type conflict — a per-division `SELECT ... FOR UPDATE` pessimistic lock serializes every booking transaction for one division, so the existing constraint-engine check can no longer race.

**Why the database layer too, not just application locking?** Because application checks can have bugs, a future code path might construct an `Allocation` without going through the booking service, and — most fundamentally — multiple backend instances behind a load balancer each have their own in-process view of "what's booked," making any purely in-JVM safeguard (`synchronized`, `ReentrantLock`) structurally unable to coordinate across instances. The database is the one component every instance genuinely shares.

### FCFS semantics, precisely

Among transactions competing for the same resource, whichever transaction PostgreSQL actually commits first wins; every other conflicting transaction fails cleanly with `409 ALLOCATION_CONFLICT`. This is deliberately **not** defined as "whichever HTTP request arrived first" — there's no reliable way to observe true network arrival order across concurrent requests; commit order is the only ordering the system can actually prove and enforce.

### Real evidence (Phase 25, genuinely parallel `Promise.all` requests, not sequential)

| Concurrent Requests | Successes | 409 Conflicts | Median | P95 | Final DB Count |
|---|---|---|---|---|---|
| 2 | 1 | 1 | 89ms | 89ms | **1** |
| 5 | 1 | 4 | 141ms | 258ms | **1** |
| 10 | 1 | 9 | 263ms | 544ms | **1** |

Latency clearly rises with contention (this is the *correct*, intentional cost of correctness — PostgreSQL's exclusion constraints and the per-division lock genuinely serialize the real conflict). A separate, non-conflicting parallel pair (different lab, different faculty, different batch, same time) — **both succeeded** — proves concurrency protection doesn't over-serialize unrelated work. Final blocking-allocation count was verified directly via SQL after every run, not inferred from HTTP status alone.

---

## 10. Security

### Authentication

`POST /api/auth/login` → email normalized (trimmed/lowercased) → BCrypt password verification → active-account check → HS256 JWT issued, containing only user id and role (never a password, never a full profile). Every subsequent request carries it in `Authorization: Bearer`; a custom `OncePerRequestFilter` (`JwtAuthenticationFilter`) validates the signature/expiration, then **re-fetches the user from the database on every single request**, not just at login, before populating Spring Security's context.

**Why reload the user every request instead of trusting the token's claims?** So account deactivation (or, if roles ever become mutable, a role change) takes effect immediately, without waiting for the token to expire — the server retains authority over "is this still a valid, active session," never fully delegating that decision to a signed artifact that could be up to 60 minutes stale. No refresh-token flow exists — don't claim one.

### Authorization: is hiding a UI button enough?

No. Frontend route guards (`RequireRouteRole`) are a navigation/UX convenience — they stop a CR who mis-clicks or types a Lab-Assistant URL from staring at a broken screen full of errors. They are not, and were never claimed to be, the security boundary. The backend's `@PreAuthorize` is authoritative and cannot be bypassed by anything the browser does — verified directly: `401` for no/invalid/expired token, `403` for an authenticated request that fails a role or ownership check, for every role-restricted endpoint in the system.

### CR scope

A CR cannot change their division by editing the browser — `divisionId` isn't even a field the extra-lab booking/search request DTOs accept. Scope comes entirely from `GET /api/cr-assignments/me`, resolved server-side from the authenticated user's own `CrAssignment` row. A cross-division cancellation attempt (the one place a foreign allocation id *could* be supplied) is rejected with `403 FORBIDDEN_DIVISION_ACCESS`.

### Production JWT secret guard (Phase 26)

Docker Compose already required `JWT_SECRET` to be set at all (`${JWT_SECRET:?...}`, fails `docker compose up` if missing). That only protects the Compose path — a `prod`-profile backend started outside Compose could still silently fall back to the codebase's documented dev-only default secret. `ProductionJwtSecretGuard` (`@Profile("prod")`) closes that gap: it fails Spring context startup if the secret is missing, is that exact dev placeholder, or is under 32 bytes (HS256's minimum). Defense in depth — two independent layers protecting the same real risk.

---

## 11. Audit & Versioning

### Audit immutability

Application-level "there's no update endpoint" was judged insufficient — it only proves *this codebase, today* has no code path to mutate the table, not that a future migration, manual hotfix, or framework bug never could. Real protection is two layers: Hibernate `@Immutable` on the entity (removes its `UPDATE` code path entirely) plus a PostgreSQL `BEFORE UPDATE OR DELETE` trigger (V12 migration) that rejects mutation regardless of which code — or absence of code — attempts it.

**The real bug that proved the trigger's worth (Phase 17):** adding the trigger exposed a *pre-existing* latent issue — inserting two `AuditLog` rows in one transaction caused Hibernate's dirty-checking to spuriously decide the first, already-inserted row was "dirty" and queue a needless `UPDATE` at flush time, which the trigger correctly rejected as a `500`. Fixed with `@Immutable` (removes Hibernate's dirty-checking for the entity entirely). The lesson: the trigger didn't create the bug, it *exposed* one that would have silently succeeded — quietly rewriting a "historical" row seconds after it was written — without it.

### Timetable versioning

`ScheduleVersion` status: `DRAFT → PUBLISHED → SUPERSEDED`, strictly forward, never backward. Exactly one `PUBLISHED` version per term is enforced by a **partial unique index** (`uq_schedule_version_one_published_per_term`, on `academic_term_id WHERE status = 'PUBLISHED'`) — not just application logic, for the same reason audit immutability isn't just application logic. Old versions are never deleted (auditability, a real reference point for "what did students actually see on date X," and because an EXTRA allocation booked under an old version still needs a valid `scheduleVersionId` to point to) — no automated rollback exists; restoring an old version means republishing it as a new act, not data recovery.

Publication is one `@Transactional` method: lock the term (per-term pessimistic lock, mirroring Phase 16's per-division lock), validate the target is `DRAFT`, supersede the existing published version, **flush**, publish the target, promote its `APPROVED` allocations to `PUBLISHED`, write audit events — all-or-nothing.

**Why the explicit flush?** This is the project's best bug story — see §16.

Concurrent publication of two different drafts for the same term serializes via the per-term lock rather than racing: both succeed, in a well-defined order, and the database ends with exactly one `PUBLISHED` version — verified with two genuinely parallel HTTP requests.

---

## 12. PDF Import

```text
upload → PDFBox text extraction → parser → normalization → entity mapping
  → constraint validation → staging → correction/review → atomic approval
  → allocations created inside a DRAFT ScheduleVersion → separate, explicit publication
```

**Why staging instead of inserting directly?** PDF extraction is fundamentally unreliable (wrong column boundaries, ambiguous abbreviations, unknown names), and every row must pass the real scheduling constraints before it can be trusted. Before approval: staged rows exist, but `allocation` rows created by that import = **0**, verified directly via SQL. A human review-and-correct step exists because no parser can substitute for a Lab Assistant confirming "yes, this is what I meant."

**Atomic approval:** revalidates every row against *live* state at approval time (not trusting review-time results, which can go stale — a real test caught exactly this: two independently-valid-at-review-time imports that conflicted with each other by the time of approval), then commits all-or-nothing inside one transaction. An import with one still-erroring row produces zero confirmed allocations; correcting it and re-approving produces exactly the intended count — never a partial import.

**Honest limitations:** text-layer PDFs only, no OCR; a strict, documented column layout; every row must map to existing subject/faculty/division/batch/lab data already in the system (nothing is auto-created); one allocation per parsed row. Don't claim arbitrary PDF format support.

---

## 13. Analytics

Real metrics only, computed from persisted data at query time (`GROUP BY`/`SUM`/`COUNT ... FILTER` in PostgreSQL, not loaded into Java and reduced): lab utilization, extra-lab totals/breakdowns, peak usage (busiest day/lab/time-slot), unused labs, a term-scoped summary. Every query is scoped to the term's current `PUBLISHED` version — `DRAFT` and `SUPERSEDED` allocations are structurally excluded (same `status = PUBLISHED` resolution the student timetable uses, never `MAX(version_number)`); cancelled allocations are excluded from active utilization.

### Utilization formula

```text
utilizationPercent = bookedMinutes / availableMinutes × 100   (per lab)
overallUtilizationPercent = SUM(booked across all labs) / SUM(available across all labs) × 100
```

**Never** an average of per-lab percentages — a lab available 10 hours and one available 2 hours aren't equally significant. A dedicated test proves the two numbers actually differ on the same fixture (58.3% weighted vs. 75% naive average). Available minutes = (working days in range) × (the college's already-configured daily scheduling window) minus overlapping `LabUnavailability` time, with overlapping unavailability windows merged first so they're never double-subtracted.

### Why no conflict rate or booking success rate?

Because the data to compute them honestly doesn't exist. This system detects conflicts extensively at request time (the whole constraint engine, every `409` returned), but persists none of it — a rejected booking or search writes no row anywhere; the audit log records only successful state changes. `GET /api/analytics/conflicts` returns `evidenceAvailable: false` with an explicit explanation, never an invented number. Extra-lab `successfulBookings` is a real, countable figure; `failedBookingDataAvailable` is always `false` — the system refuses to compute a rate over a denominator it doesn't have.

---

## 14. Testing

Layered, not just "we have tests":

- **Unit** — one class per constraint/scorer, mocked collaborators, fast (JUnit 5 + Mockito + AssertJ).
- **Integration** (`*IT`, Testcontainers, Maven Failsafe, 19 classes) — real PostgreSQL, full request pipeline, security, migrations.
- **Concurrency** (`*ConcurrencyIT`) — genuinely parallel `ExecutorService`/`CountDownLatch`-barriered requests, not sequential.
- **Frontend** (Vitest + Testing Library) — route guards, role-scoped workflows, error states (409 handling, empty vs. error distinction).
- **Live Docker verification** — every phase's claims re-checked against the real running stack, not just green tests (this caught several of the bugs in §16).
- **Clean-database verification** — a genuinely empty PostgreSQL, Flyway `V1`→latest, Hibernate `validate`, healthy startup — proves deployment doesn't depend on an old developer database.
- **Performance benchmarks** (Phase 25) — deterministic scenarios, warm-up runs, multiple measured runs, median/p95, correctness checked alongside timing.

**Current counts: 300 backend tests, 69 frontend tests, both 0 failures** (re-confirmed at the start of Phase 27/28).

### The Testcontainers limitation, precisely

This project's local development sandbox runs Maven inside a container with no Docker-socket access to the host daemon — every `*IT`/`*ConcurrencyIT` class has been "written correctly, environment-blocked here" since Phase 18, substituted every time by equivalent live verification against the real Dockerized stack. Phase 27 wired `mvn verify` (Maven Failsafe, already configured in `pom.xml`) into GitHub Actions, whose Linux runners have a native, unnested Docker daemon — this *should* finally let all 19 classes execute for real. **This has not yet been observed** — no push has occurred. Never say "all CI tests passed" until that's actually true.

---

## 15. Performance

Methodology: deterministic scenarios (fixed inputs, no randomness where avoidable), 5 warm-up runs before 20 measured runs for JVM benchmarks (`System.nanoTime()`), real HTTP timing (`performance.now()`) for live endpoint benchmarks, median/p95/min/max reported — never a single lucky measurement. Correctness verified alongside every timing (e.g. the same-slot concurrency benchmark's final blocking-allocation count was checked via direct SQL, not inferred).

### Numbers worth remembering

| Operation | Result |
|---|---|
| Scheduler, 10 easy requirements | ~18ms median |
| Scheduler, 10 constrained | ~53ms median |
| Scheduler, 100 requirements | ~1.1s median |
| Extra-lab booking (full production path) | ~39ms median |
| Timetable retrieval | ~9–11ms median |
| Analytics endpoints | ~9–15ms median |
| Same-slot contention (10 concurrent) | 1 success, 9 conflicts, DB count = 1 |

### Why no optimization was made

`EXPLAIN ANALYZE` on the two highest-suspicion queries (timetable retrieval, analytics lab-utilization) showed PostgreSQL choosing straightforward sequential scans with sub-millisecond execution — the *correct* plan at this project's actual data volume (~36 allocation rows), not evidence of a missing index. Phase 23 had flagged `(schedule_version_id, status, allocation_date)` as a *possible* future composite index; Phase 25 benchmarked first and the evidence didn't justify adding it — an index that isn't needed yet still costs write-path overhead for a read-side benefit nothing currently measures. "I checked, and the data said no" is the actual answer, not "I didn't think of it."

---

## 16. Deployment & CI/CD

```text
Browser → frontend (nginx:1.27-alpine, static Vite build, SPA fallback) → cross-origin REST
        → backend (eclipse-temurin:21-jre-alpine, non-root user, Flyway on startup) → PostgreSQL 16
```

Docker Compose, health-aware startup (`depends_on: condition: service_healthy`, not a fixed sleep), named-volume persistence (survives `down`, destroyed only by explicit `down -v`), `.env`-driven secrets (never committed; `.env.example` tracked with placeholders only), an explicit non-wildcard CORS allow-list, a `prod` Spring profile with `ProductionJwtSecretGuard`. **TLS is not implemented here** — any real deployment needs an external reverse proxy or platform-managed certificate; don't claim HTTPS is included.

### CI/CD (Phase 27)

```text
Push / PR / Manual
   ├─ Backend: compile → unit tests → Testcontainers integration tests → upload jar
   └─ Frontend: npm ci → lint → test → build          (parallel with backend)
        ↓ (needs: both green)
   Docker: compose config → build → prod-profile smoke test → cleanup (always)
```

`npm ci`, not `npm install` — uses `package-lock.json` exactly, fails if lock/package metadata disagree, reproducible dependency tree, appropriate for CI (and matches the deployment Dockerfile's own build step). No GitHub Secrets were needed — CI-only JWT/DB values live directly in the workflow's `env:` block, disposable and unique per run.

**Honest CI status:** the workflow is written, its YAML validated, and every command's logic dry-run tested locally (including a full local `prod`-profile Compose boot reproducing the Docker job's exact steps). **It has not been pushed, so GitHub-hosted execution has not been observed.** State this precisely if asked — "designed to work" and "observed to work" are different claims.

---

## 17. Bugs & Debugging Stories

### Hardest bug (primary): schedule-version publication + Hibernate flush ordering

- **Symptom:** publishing a `DRAFT` when a *different* version was already `PUBLISHED` — one ordinary, sequential HTTP request, zero concurrency — returned `500`, on the very first real attempt.
- **Root cause:** `ScheduleVersionService.publish` loads the target version before looking up the term's existing published version. Hibernate's default flush ordering follows *load order*, not *mutation order* — so the target's `status = PUBLISHED` update reached PostgreSQL before the existing version's `status = SUPERSEDED` update. For one instant, mid-transaction, two rows for the same term both read `PUBLISHED`, and the partial unique index — working exactly as designed — rejected it as a duplicate key violation.
- **Fix:** an explicit `.flush()` immediately after superseding the old version, forcing that update to reach the database before publishing the new one — removing the dependency on Hibernate's flush-ordering heuristic entirely, rather than fighting it indirectly.
- **Lesson:** a mandatory database-level unique index (added because the requirements called for a "final safeguard," not because a bug was expected) caught a real, previously-invisible ordering assumption on the very first real exercise of the code path.

### Hardest bug (alternative): immutable audit log vs. a spurious Hibernate `UPDATE`

See §11 — the audit trigger exposed a pre-existing latent bug where Hibernate's dirty-checking issued an unwanted `UPDATE` against a just-inserted, JSON-`metadata`-bearing entity, which the append-only trigger correctly rejected as a `500`. Fixed with `@Immutable`.

### Other real bugs (all found, root-caused, fixed, and re-verified live)

- **Recommendation scores displayed as "0" (Phase 21):** `normalizedScore` is a `0.0–1.0` ratio; the frontend rendered it with `.toFixed(0)` — every candidate below 50% match displayed as `"0"`. Found via live testing against real data (a unit-test mock happened to encode the same wrong "it's already a percentage" assumption, so it never caught the bug). Fixed: `Math.round(score * 100)`.
- **Batch-scoped timetable hid division-wide practicals (Phase 22):** see §6.
- **`docker-compose.yml` silently ignored `SPRING_PROFILES_ACTIVE` from `.env` (Phase 27):** the backend's environment block had `SPRING_PROFILES_ACTIVE: dev` as a literal string, never interpolated — so Phase 26's own deployment guide had been documenting a step that silently did nothing. Found while designing Phase 27's `prod`-profile CI smoke test (the first time that path was actually exercised). Fixed, then verified: a real isolated stack booted under `prod` with a CI-style secret, `ProductionJwtSecretGuard` allowed it, and the known demo credentials correctly returned `401` (no seed data under `prod`).
- **`@PreAuthorize` denial returning `500` instead of `403` (Phase 4):** the denial exception was being caught by the project's own global exception handler before Spring Security's access-denied handling ever saw it — fixed with an explicit handler.
- **`MaxUploadSizeExceededException` handler collision (Phase 19):** a competing `@ExceptionHandler` for an exception type the base class already handled caused `IllegalStateException` at *application startup*, not request time — only discoverable by actually booting the container. Fixed by overriding the existing protected hook instead of declaring a new handler.

---

## 18. Key Design Decisions

Selected from 139 documented ADRs (`docs/15-DESIGN-DECISIONS.md`) — the ones worth explaining, not the full list:

1. **Modular monolith**, not microservices — one shared transaction boundary the FCFS guarantee depends on (§5).
2. **PostgreSQL hard constraints (exclusion constraints + partial unique indexes)**, not application-only checks — the database is the one component every instance genuinely shares (§9, §11).
3. **JWT with per-request DB reload**, not fully stateless trust — deactivation takes effect immediately (§10).
4. **Constraint engine separated from scoring** — an invalid candidate is structurally unreachable by any scorer, not merely filtered by a runtime check that could be forgotten (§7).
5. **MRV + backtracking**, not greedy — proven necessary by a real scenario, not assumed (§8).
6. **FCFS via database commit order**, not "first request received" — the only ordering the system can actually prove (§9).
7. **Immutable audit trigger**, not "no update endpoint exists" — a defense that only matters when something upstream already went wrong, and it already has, twice (§11).
8. **Schedule versioning with preserved history** — no in-place edits, no silent timetable mutation, a real database-enforced "exactly one published version" invariant (§11).
9. **PDF staging, never direct insert** — a human review step is cheap insurance against unreliable extraction (§12).
10. **Analytics evidence policy: refuse to invent what isn't persisted** — conflict/success-rate metrics say so explicitly rather than showing a plausible-looking fabricated number (§13).
11. **Measurement before optimization** — Phase 25 benchmarked before deciding on the speculative index Phase 23 had flagged, and didn't add it (§15).
12. **Docker Compose, not Kubernetes** — no orchestration need at this scale; formalized, not replaced, in Phase 26.
13. **CI as verification, not automatic production deployment** — no hosting target selected, and automatic deployment is a materially higher-risk action than build verification (§16).

---

## 19. Scalability & Limitations

**Honest current scale:** designed for college-scale scheduling — tens of labs/divisions, a moderate number of allocations per term. Not claimed to handle millions of requests or multiple institutions at once.

**Scheduler's honest limitation:** backtracking has a combinatorial worst case (this is a CSP, not claimed polynomial). Mitigated, not eliminated, by MRV ordering, hard-constraint pre-filtering (small branching factor in practice), and a bounded node limit that fails safely (`SEARCH_LIMIT_REACHED`) rather than hanging.

**What would change if scale grew by orders of magnitude:**
- Additional indexes — added only with fresh benchmark evidence at the new scale, the same discipline Phase 25 already used.
- A real constraint solver (e.g. OR-Tools) if the scheduling problem itself grew large enough that provable optimality started to matter more than a valid, good-enough schedule.
- Async processing for large PDF imports, if import size grew well beyond what synchronous request handling comfortably serves.
- Caching for read-heavy analytics/timetable endpoints, if and when a real access pattern justified it.
- Multiple backend instances behind a load balancer — already safe today, because the concurrency guarantee lives in PostgreSQL, not in-process.

**Current, real limitations** (not hidden): Testcontainers unexecuted in the local sandbox (substituted with live verification); GitHub-hosted CI execution not yet observed; PDF import supports one strict text-layer format only; no persisted failed-booking/conflict telemetry (a genuine, disclosed observability gap, not an oversight); single-instance Compose deployment (no clustering/HA); no automated backups (manual `pg_dump`/restore documented); no zero-downtime upgrade.

---

## 20. Resume Bullets

- Built a constraint-based lab scheduling backend (Java 21/Spring Boot) with a 12-rule constraint engine, MRV-ordered backtracking scheduler, and PostgreSQL exclusion constraints guaranteeing exactly one winner under genuinely parallel concurrent booking requests — verified with real parallel HTTP load, not sequential tests.
- Designed and implemented a versioned-timetable system (DRAFT/PUBLISHED/SUPERSEDED) with a database-enforced one-published-per-term invariant, an immutable append-only audit log (Hibernate + PostgreSQL trigger, defense in depth), and a PDF-import pipeline with staged review and atomic, revalidated approval.
- Delivered a full-stack, role-scoped platform (React/TypeScript + Spring Boot + PostgreSQL) with 300 backend and 69 frontend tests, Docker Compose deployment with health-aware startup, and a GitHub Actions CI pipeline covering unit, integration, and Docker-build verification.

### ATS / Recruiter Version

"Full-stack college lab-scheduling platform (Java, Spring Boot, PostgreSQL, React, TypeScript, Docker, GitHub Actions) with a rule-based constraint engine, backtracking auto-scheduler, and database-guaranteed concurrency-safe booking — built and tested end-to-end, including live Docker verification and real parallel-request concurrency proof."

### Interviewer Deep-Dive Version

Lead with: constraint solving (12 independent hard constraints, evaluated exhaustively, never fail-fast), transactional correctness (PostgreSQL exclusion constraints + per-resource pessimistic locking, proven with genuine parallel requests), database invariants as the final authority over application logic (partial unique indexes, append-only triggers — both caught real bugs live), timetable versioning with preserved history, and evidence-based performance work (benchmark first, optimize only when the data says to — and say so when it says not to).

---

## 21. STAR Stories

**Story 1 — Concurrency.** *Situation:* two class representatives could book the identical lab/date/time slot. *Task:* guarantee exactly one succeeds, correctly, under real simultaneous requests — not "probably fine" application logic. *Action:* added three PostgreSQL exclusion constraints (lab/faculty/batch × time-range) plus a per-division pessimistic lock for the one cross-type case exclusion constraints can't express, and proved it with genuinely parallel HTTP requests at 2/5/10-way contention. *Result:* exactly one success and N-1 clean `409`s at every contention level, confirmed by direct SQL count, with correctness prioritized over raw throughput — latency rose under contention, by design.

**Story 2 — Version publication bug.** *Situation:* the very first real "publish while another version is already published" request 500'd. *Task:* find the actual root cause, not just catch the exception. *Action:* traced it to Hibernate flushing entity updates in load order rather than mutation order, causing a transient duplicate-`PUBLISHED` state the database's own partial unique index correctly rejected. *Result:* fixed with one explicit `.flush()` at the right point; documented as ADR-088 and re-verified live; the underlying database invariant (added as a "safeguard," not because a bug was expected) is what actually caught the bug in the first place.

**Story 3 — Immutable audit bug.** *Situation:* a routine CR reassignment (two audit inserts in one transaction) started 500ing the moment the append-only trigger was added. *Task:* figure out why a *correctness-improving* change broke something. *Action:* traced it to Hibernate's dirty-checking spuriously issuing an `UPDATE` against an already-inserted entity, which the new trigger correctly rejected. *Result:* fixed with `@Immutable`; the real lesson was that the trigger didn't create the bug, it exposed a pre-existing one that would otherwise have silently corrupted "immutable" history.

**Story 4 — Performance decision.** *Situation:* a prior phase had flagged a specific composite index as a plausible future addition. *Task:* decide, with evidence, whether to add it. *Action:* ran `EXPLAIN ANALYZE` on the actual queries at real data volume before touching the schema. *Result:* PostgreSQL was already choosing correct, sub-millisecond sequential scans — no index added, write-path cost avoided, and the reasoning documented rather than silently doing nothing.

---

## 22. Rapid-Fire Q&A

- **Process vs. transaction?** A process is an OS-level unit of execution; a transaction is a database-level unit of atomicity (all-or-nothing). This project's booking/publication/approval flows are each one transaction, not necessarily one process step.
- **Optimistic vs. pessimistic locking?** Optimistic assumes no conflict and checks at commit (a version column, retry on mismatch); pessimistic acquires a lock upfront and makes others wait. This project uses pessimistic locking (`SELECT ... FOR UPDATE`) for the per-division/per-term serialization cases, plus database exclusion constraints as an independent backstop.
- **401 vs. 403?** 401 = "I don't know who you are" (no/invalid/expired token). 403 = "I know who you are, and you're not allowed to do this" (authenticated, wrong role or wrong scope).
- **Unique index vs. application check?** An application check only holds if every code path remembers to run it; a unique index is enforced by the database on every write, from any code path, including ones that don't exist yet.
- **Unit vs. integration test?** Unit tests isolate one class with mocked collaborators; this project's integration tests (`*IT`) boot a real Spring context against a real Testcontainers PostgreSQL and exercise the full HTTP → service → database path.
- **Docker image vs. container?** An image is the built, immutable artifact; a container is a running instance of one. `docker build` produces images; `docker compose up` runs containers from them.
- **JWT authentication vs. authorization?** The JWT proves *who* the caller is (authentication, verified by signature); `@PreAuthorize`/ownership checks decide *what* that identity is allowed to do (authorization) — a valid token alone grants nothing beyond "you are this user."
- **DRAFT vs. PUBLISHED?** `DRAFT` is a Lab Assistant's in-progress revision, invisible to Students/CRs. `PUBLISHED` is the one current, operationally visible version per term, enforced by a database-level partial unique index.

---

## 23. Avoid Memorization Traps

Do not try to recite this document. Memorize:

- The **architecture** (three tiers, modular monolith, why).
- **Three hard problems** you solved: concurrency-safe booking, backtracking scheduling, version-publication ordering.
- **Three design decisions** you can defend: database-level constraints over application-only checks, MRV/backtracking over greedy, refusing to fabricate analytics.
- **Two bug stories**: the flush-ordering publish bug, the immutable-audit-trigger bug.
- **Key benchmark numbers**: scheduler ~18ms/~1.1s, booking ~39ms, 10-way contention → 1 success/9 conflicts/DB count 1.
- **Testing counts**: 300 backend, 69 frontend.
- **Limitations**: CI not yet observed running, Testcontainers blocked locally, no persisted conflict telemetry.

Then explain everything else naturally, from actually understanding it.

### Facts to Memorize (compact sheet)

```text
Backend tests:              300 (0 failures)
Frontend tests:              69 (0 failures)
Integration test classes:    19 (*IT / *ConcurrencyIT, Testcontainers)
Hard constraints:            12 (HC-01–HC-12)
Scoring factors:              3 (Capacity Fit 30 / Preferred Lab Type 15 / Balanced Utilization 15)
Demo dataset:                 15 labs (4 Cloudera), 1 division, 3 batches, 2 subjects, 2 faculty
Scheduler, 10 easy:          ~18ms median
Scheduler, 100 requirements: ~1.1s median
Booking latency:             ~39ms median
Timetable retrieval:         ~9-11ms median
Analytics endpoints:         ~9-15ms median
10-way contention:            1 success, 9 conflicts, DB count = 1
ADRs documented:             139
```

---

## 24. Claims to Avoid

Do **not** say:

- "AI scheduler" — no ML/AI exists; it's a deterministic constraint engine + backtracking search.
- "Distributed microservices" — it's a modular monolith, deliberately.
- "Deployed to production / AWS / any live host" — it's a local/Docker Compose deployment; no production hosting target has ever been selected.
- "Millions of users" — designed and validated at college scale.
- "All GitHub CI tests passed" — the workflow has not been pushed; execution has not been observed. Say "designed to work, not yet observed running" until it actually runs.
- "All PDF formats supported" — one strict, documented text-layer format; no OCR.
- "Conflict analytics measures failed booking history" — it explicitly does not; no failed attempt is ever persisted, and the API says so.
- "Zero-downtime deployment" — upgrading recreates the backend container; there's a brief unavailability window.
- "We prevent conflicts with application-level checks" — the real guarantee is the database (exclusion constraints), not application code alone; say both, correctly ordered.
- "The scheduler is polynomial / has proven complexity bounds" — it's a CSP with a bounded, not eliminated, worst case.

---

## 25. Questions to Ask an Interviewer

Optional, general (not project-boasting): How does your team handle production concurrency/race-condition issues when they show up? How do you approach database migrations on a live system? How are architecture decisions like monolith-vs-microservices reviewed and revisited as a team? What does your CI/CD pipeline catch that code review doesn't? How do you decide when a performance optimization is actually worth the added complexity?
