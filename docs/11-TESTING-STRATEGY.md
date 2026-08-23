# Testing Strategy

No test result is ever claimed without actually running the suite (project working rule). This document maps test categories to the requirements/constraints they must cover; actual pass/fail status is only asserted once the corresponding implementation phase has run these tests for real.

## Categories

| Category | Tooling | What it covers |
|---|---|---|
| Unit tests | JUnit 5 + Mockito | Individual `SchedulingConstraint`, scorer, and domain-object logic in isolation — no Spring context, no DB |
| Repository tests | Spring Data JPA test slice + Testcontainers (real PostgreSQL) | Query correctness (esp. the partial indexes and time-overlap queries in [04-DATABASE-DESIGN.md](04-DATABASE-DESIGN.md)) |
| Integration tests | `@SpringBootTest` + Testcontainers | Full request → controller → service → constraint engine → DB round trip |
| Security tests | `@SpringBootTest` + MockMvc | RBAC/ownership enforcement per [09-AUTHORIZATION-RBAC.md](09-AUTHORIZATION-RBAC.md) |
| Concurrency tests | Integration test with parallel threads/`ExecutorService` against Testcontainers Postgres | FCFS + double-booking prevention (Phase 16) |
| Algorithm tests | JUnit 5, deterministic fixtures | Candidate generation, scoring formulas, most-constrained-first ordering, backtracking, partial-failure reporting |
| PDF parsing/import tests | JUnit 5 with sample fixture PDFs | Extraction → normalization → mapping → validation pipeline |
| Frontend tests | Vitest + React Testing Library | Component behavior, role-gated rendering (UI-level only — never the sole authorization guarantee) |
| End-to-end critical flows | Manual (documented in [17-DEMO-SCENARIOS.md](17-DEMO-SCENARIOS.md)) or Playwright if time permits | Full user journeys across roles |

## Traceability: Hard Constraints → Mandatory Test Scenarios

| Scenario | Expected result | Constraint(s) exercised |
|---|---|---|
| A1 BDA 09:00–11:00 (Lab C-301, Faculty X) + A2 CNS 09:00–11:00 (Lab C-302, Faculty Y) | **VALID** | HC-04 (passes — different batches), HC-01/HC-02 (pass — different lab/faculty) |
| Same as above but same faculty for both | **INVALID** | HC-02 |
| Same as above but same lab for both | **INVALID** | HC-01 |
| A1 BDA 09:00–11:00 + A1 CNS 10:00–12:00 (same batch, overlapping) | **INVALID** | HC-04 |
| DIVISION-wide session 09:00–11:00 + A2 batch session 10:00–12:00 (same division) | **INVALID** | HC-05 |
| DIVISION A 09:00–11:00 + BATCH A2 09:00–11:00 requested, existing is DIVISION | **INVALID** (bidirectional) | HC-05 |
| Faculty available Mon 09:00–13:00; session Mon 09:00–11:00 | **VALID** | HC-03 |
| Faculty available Mon 09:00–13:00; session Mon 12:00–14:00 | **INVALID** | HC-03 |
| Batch strength 64, Lab capacity 60 | **INVALID** | HC-07 |
| Batch strength 64, Lab capacity 65 | **VALID** | HC-07 |
| BDA requiring Cloudera, candidate lab lacks it | **INVALID** | HC-08 |
| BDA requiring Cloudera, candidate lab has it | **VALID** | HC-08 |
| Lab under maintenance on requested date | **INVALID** | HC-06 |
| CR of Division CS-A requests allocation with `divisionId` = IT-B | **INVALID (`FORBIDDEN_DIVISION_ACCESS`)** | HC-11 |
| Subject requires two software items, lab has only one | **INVALID** | HC-08 (ALL-required semantics) |
| `09:00–11:00` vs `11:00–13:00` (back-to-back) | **VALID (no overlap)** | Time-overlap utility (NFR-10) |
| `09:00–11:00` vs `10:00–12:00` | **INVALID (overlap)** | Time-overlap utility |

## Laboratory Domain Tests — Implemented (Phase 5)

| Test | Class | What it proves |
|---|---|---|
| `end <= start` rejected | `LabUnavailabilityServiceTest` | Interval validation runs before any repository lookup |
| `end == start` rejected | `LabUnavailabilityServiceTest` | Boundary case of the half-open interval rule |
| Valid interval proceeds | `LabUnavailabilityServiceTest` | The validation doesn't over-reject valid intervals |
| Duplicate lab-software rejected | `LabCapabilityServiceTest` | `LAB_SOFTWARE_ALREADY_ASSIGNED`, not a raw DB error |
| Duplicate lab-equipment rejected | `LabCapabilityServiceTest` | `LAB_EQUIPMENT_ALREADY_ASSIGNED`, not a raw DB error |
| Full RBAC + validation + capability filtering, real endpoints | `LabApiIT` (Testcontainers, environment-blocked here) | LAB_ASSISTANT creates, CR/STUDENT get 403, unauthenticated gets 401; capacity=0 rejected; duplicate code rejected cleanly (not raw SQL); Cloudera filter, capacity filter, AND-combination, and ALL-semantics multi-software filter all proven against real seeded/persisted data; unavailability interval validated end-to-end |

**Manually verified against the Dockerized stack (2026-08-22):** all 8 of the phase brief's required scenarios (A–H — lab creation, unauthorized mutation, invalid capacity, duplicate code, Cloudera filter, capacity filter, combined filter, invalid unavailability interval) executed with real `curl` requests against the running containers, plus a restart-and-recount idempotency check on the dev seed data (15 labs → 16 after one test-created lab, software/lab_software/lab_equipment row counts unchanged across the restart).

## Constraint Engine Tests — Implemented (Phase 9)

### HC → Test Traceability Matrix

| HC | Constraint class | Positive test | Negative test | Edge case | Integration coverage |
|---|---|---|---|---|---|
| HC-01 Lab Conflict | `LabConflictConstraint` | No existing allocations → PASS | Overlapping same-lab allocation → FAIL | Back-to-back (no overlap); different-date same-time not treated as conflict; CANCELLED rows never block | `ConstraintEngineIT` (same-lab conflict, end-to-end) |
| HC-02 Faculty Conflict | `FacultyConflictConstraint` | No existing faculty allocations → PASS | Overlapping allocation, different lab → FAIL | Distinguished from HC-03 in a dedicated combined test | `ConstraintEngineIT` (A1/A2 scenario) |
| HC-03 Faculty Availability | `FacultyAvailabilityConstraint` | Within declared window → PASS | Outside declared window → FAIL | Day-of-week derived from `allocationDate`, never independently supplied | `ConstraintEngineIT` (A1/A2 scenario) |
| HC-04 Batch Conflict | `BatchConflictConstraint` | Different batches, same division, simultaneous → PASS | Same batch overlapping → FAIL | DIVISION candidate passes vacuously (no batch) | `ConstraintEngineIT` (A1/A2 scenario) |
| HC-05 Division-Wide Conflict | `DivisionWideConflictConstraint` | BATCH vs. sibling BATCH → PASS | BATCH vs. DIVISION (both directions), DIVISION vs. DIVISION → FAIL | Different divisions never conflict | Full 6-row matrix unit-tested |
| HC-06 Lab Availability | `LabAvailabilityConstraint` | No unavailability windows → PASS | Overlapping `lab_unavailability` window → FAIL | Candidate starting exactly when unavailability ends → PASS; permanently inactive lab → FAIL | `ConstraintEngineIT`-adjacent manual Docker scenario |
| HC-07 Capacity | `CapacityConstraint` | Capacity ≥ required (both BATCH and DIVISION) → PASS | Capacity < required → FAIL | `details` carries exact required/actual capacity | `ConstraintEngineTest` multi-failure fixture |
| HC-08 Required Software | `RequiredSoftwareConstraint` | Empty requirements; lab has all + extra software → PASS | Missing one of several required items → FAIL | BDA/Cloudera pass and fail, both live in Docker | `ConstraintEngineIT` (Cloudera scenario) |
| HC-09 Required Equipment | `RequiredEquipmentConstraint` | Empty requirements; quantity ≥ required → PASS | Quantity < required; no association row (0 available) → FAIL | Never a null-pointer failure on a missing association | Manual Docker scenario |
| HC-10 Required Lab Type | `RequiredLabTypeConstraint` | No required type; matching type → PASS | Mismatched required type → FAIL | Preferred-only type never fails HC-10 (mandatory test) | Manual Docker scenario, isolated at the per-constraint-result level |
| HC-11 CR Authorization | `CrAuthorizationConstraint` | CR owning the division → PASS | CR not owning / no assignment → FAIL | No actor / LAB_ASSISTANT actor → `NOT_APPLICABLE`; STUDENT actor defensively FAIL | Manual Docker scenario |
| HC-12 Academic Relationship | `AcademicRelationshipConstraint` | Coherent batch/division/subject/faculty → PASS | Batch-division mismatch, subject-year mismatch, no/wrong faculty assignment → FAIL | Three sub-checks, first-failure-wins internally | `ConstraintEngineIT`, manual Docker scenario |

### Engine-Level Tests (`ConstraintEngineTest`)

| Test | What it proves |
|---|---|
| Results returned in the documented deterministic order regardless of constraint injection order | `ConstraintEngine`'s own sort produces the stable order, not Spring's bean-discovery order (constraints shuffled with a fixed seed before registration) |
| A1/A2 (different batches, same division, simultaneous) is fully valid | The signature "not a CRUD app" scenario — zero violations, all applicable HC-01/02/03/04/05 individually confirmed PASS |
| A candidate failing capacity + software + faculty availability simultaneously returns all three violations | All-results evaluation, not fail-fast — proven with a controlled fixture (not just documentation) |

### Integration Test (`ConstraintEngineIT`, Testcontainers, environment-blocked here)

Real repository-backed data via `SchedulingContextFactory`/`CandidateAllocationFactory`/the real Spring-wired `ConstraintEngine` (all twelve `@Component` constraints auto-discovered): the A1/A2 valid scenario, a same-lab-conflict scenario, and the BDA/Cloudera pass-vs-fail scenario, all against a real PostgreSQL instance. Written correctly for CI/future environments; manual Docker verification (below) covers what this cannot run here.

**Manually verified against the Dockerized stack (2026-08-23), via a temporary `@Profile("dev")`-only `ApplicationRunner` deleted after use (no production allocation-creation API was added just to test this):** all 16 of the phase brief's required scenarios executed against the real `ConstraintEngine` and real seeded demo data — valid A1/A2 (zero violations); same-lab/same-faculty/same-batch/division-wide conflicts each rejected; faculty-unavailable and lab-temporarily-unavailable each rejected; BDA/Cloudera pass vs. no-Cloudera fail; equipment-quantity pass/fail; required-lab-type pass/fail plus preferred-only-never-fails-HC-10; invalid academic relationship rejected; unauthorized CR context rejected; and a multi-failure candidate returned two simultaneous violations together. All 16 expected-vs-actual validity results matched. All temporary rows the harness created (a test allocation, a lab-unavailability window, an equipment requirement, a temporary lab-type flip) were confirmed cleaned up afterward via direct `psql` query. Regression re-verified: all Phase 3-8 endpoints still `200`; `/api/allocations` still `404`.

## Scheduling Domain & Allocation Persistence Tests — Implemented (Phase 8)

| Test | Class | What it proves |
|---|---|---|
| BATCH-targeted request requires a batchId | `SchedulingRequestTest` | Structural validation runs with no Spring/database |
| DIVISION-targeted request rejects a batchId | `SchedulingRequestTest` | Same, opposite direction |
| Invalid time range rejected / valid range accepted | `SchedulingRequestTest` | Reuses `TimeIntervalUtils.isValid` |
| Date + LocalTime + configured zone converts to the expected `Instant`, in both UTC and a fixed +05:30 zone | `SchedulingTimeMapperTest` | The college-timezone bridge is deterministic and zone-correct, not machine-local-timezone-dependent |
| Start/end conversion produces a valid `InstantRange` | `SchedulingTimeMapperTest` | The range helper composes both conversions correctly |
| `Allocation.forBatch` with a batch belonging to the target division succeeds | `AllocationTest` | Happy path |
| `Allocation.forBatch` with a null batch is rejected | `AllocationTest` | Structural invariant |
| `Allocation.forBatch` with a batch from a *different* division is rejected | `AllocationTest` | The cross-table check no CHECK constraint can express |
| `Allocation.forDivision` never has a batch | `AllocationTest` | Structural invariant, opposite direction |
| Invalid time range rejected regardless of target type | `AllocationTest` | Interval validation applies uniformly |
| `publish()`/`cancel()` transition guards (APPROVED→PUBLISHED, reject re-publish, reject re-cancel) | `AllocationTest` | Lifecycle transitions match docs/03-SYSTEM-ARCHITECTURE.md §5 |
| First schedule-version for a term needs no reason; a second requires one | `ScheduleVersionServiceTest` | Matches docs/04-DATABASE-DESIGN.md §7's "required for v2+" rule |
| Publishing supersedes the term's previously-published version in the same call | `ScheduleVersionServiceTest` | ADR-009's versioning rule, service-level |
| Publishing with no existing published version succeeds | `ScheduleVersionServiceTest` | The common first-publish case isn't over-guarded |
| `SchedulingContextFactory` assembles a context for a BATCH request, including batch-scoped existing allocations | `SchedulingContextFactoryTest` | The factory correctly wires `AllocationQueryService`/`BatchService` together |
| `SchedulingContextFactory` never calls `BatchService` for a DIVISION request | `SchedulingContextFactoryTest` | No wasted lookups for a request with no batch |
| Full DB constraint + query-path verification (Testcontainers, environment-blocked here) | `AllocationPersistenceIT` | `chk_allocation_target_invariant`, `chk_allocation_interval`, `uq_schedule_version_term_number`, `uq_schedule_version_one_published_per_term`, and all four active-allocation query paths (lab/faculty/batch/division), with cancelled rows correctly excluded |

**Manually verified against the Dockerized stack (2026-08-22):** Flyway migrated to v10; one `PUBLISHED` `schedule_version` seeded with zero `Allocation` rows (confirmed via `psql`); five DB-level guarantees proven with real transactional `psql` inserts (target invariant, interval CHECK, version-number uniqueness, one-published-per-term uniqueness — all four rejected exactly as designed); `POST /api/allocations` and `GET /api/allocations` both confirmed `404` (no accidental creation surface); a restart-and-recount idempotency check confirmed the schedule-version row count (1) unchanged across a backend container restart; Phase 3-7 regression endpoints re-verified with no regression.

## Time Interval Utility & Faculty Availability Tests — Implemented (Phase 7)

| Test | Class | What it proves |
|---|---|---|
| `09:00-11:00` overlaps `10:00-12:00` | `TimeIntervalUtilsTest` | Standard overlap formula catches a genuine partial overlap |
| `09:00-11:00` does not overlap `11:00-13:00` | `TimeIntervalUtilsTest` | Back-to-back intervals are correctly non-overlapping (half-open semantics) |
| `09:00-11:00` contains `09:00-11:00` | `TimeIntervalUtilsTest` | Boundary-equal containment holds |
| `09:00-12:00` contains `10:00-11:00` | `TimeIntervalUtilsTest` | Standard fully-contained case |
| `09:00-11:00` does not contain `10:00-12:00` | `TimeIntervalUtilsTest` | An interval extending past the outer bound is correctly rejected |
| `start == end` / `start > end` both invalid | `TimeIntervalUtilsTest` | `isValid` rejects both malformed cases |
| Available within one interval / outside all intervals / correct window found among several | `FacultyAvailabilityServiceTest` | Core evaluation logic against multiple stored windows |
| Adjacent stored intervals evaluated as continuous | `FacultyAvailabilityServiceTest` | The in-memory merge-for-evaluation algorithm (PART 19) correctly spans a request crossing an adjacency boundary, without mutating the database |
| Inactive faculty always unavailable | `FacultyAvailabilityServiceTest` | `isAvailable` short-circuits to `false` regardless of stored rows |
| Wrong term / no records | `FacultyAvailabilityServiceTest` | Missing availability means unavailable, never "available all day" (PART 15) |
| Create valid interval succeeds | `FacultyAvailabilityServiceTest` | Happy path |
| `start >= end` rejected | `FacultyAvailabilityServiceTest` | `INVALID_AVAILABILITY_INTERVAL` |
| Overlapping existing active interval rejected | `FacultyAvailabilityServiceTest` | `FACULTY_AVAILABILITY_OVERLAP`, not a silent merge |
| Adjacent interval allowed | `FacultyAvailabilityServiceTest` | Adjacency is explicitly not treated as overlap |
| Unknown faculty / inactive faculty / unknown term rejected | `FacultyAvailabilityServiceTest` | `FACULTY_NOT_FOUND`, `FACULTY_INACTIVE`, `ACADEMIC_TERM_NOT_FOUND` |
| Full RBAC + validation + overlap/adjacency handling, real endpoints | `FacultyAvailabilityApiIT` (Testcontainers, environment-blocked here) | LAB_ASSISTANT can create/update/deactivate/list/check; CR/STUDENT get 403 on both mutation **and read** (Phase 7's deliberately narrower access model); unauthenticated gets 401; overlapping create rejected `409`; adjacent create allowed `200`; invalid interval rejected `400`; `/check` reflects seeded availability accurately for both an available and an unavailable interval |

**Manually verified against the Dockerized stack (2026-08-22):** all 8 of the phase brief's required scenarios (A-H) executed with real `curl` requests — LAB_ASSISTANT creates valid availability; CR and STUDENT mutation both `403`; unauthenticated read `401`; overlapping create `409 FACULTY_AVAILABILITY_OVERLAP`; adjacent create `200`; BDA Monday 09:00-11:00 check `available:true`; BDA Monday 13:00-14:00 check `available:false` (the deliberate gap between BDA's two Monday windows). Additionally verified: CR read also returns `403` (Phase 7's stricter access model, distinct from Phase 5/6); PATCH and DELETE (soft-deactivate, `active:false`) both work correctly; a restart-and-recount idempotency check confirmed the row count (7: 5 seeded + 2 test-created) was unchanged across a backend container restart; Phase 4-6 regression endpoints re-verified with no regression.

## Subject Requirement Tests — Implemented (Phase 6)

| Test | Class | What it proves |
|---|---|---|
| Inactive software rejected | `SubjectRequirementServiceTest` | `INACTIVE_SOFTWARE` returned, not a silently-added dead requirement |
| Inactive equipment rejected | `SubjectRequirementServiceTest` | `INACTIVE_EQUIPMENT` returned, same guarantee for equipment |
| Both required and preferred lab type set simultaneously rejected | `SubjectRequirementServiceTest` | `INVALID_LAB_TYPE_PREFERENCE`, mirroring the DB `CHECK` constraint at the application layer |
| Setting only `required` (no `preferred`) succeeds | `SubjectRequirementServiceTest` | The common single-field case isn't over-rejected by the mutual-exclusivity check |
| Updating quantity on a nonexistent equipment requirement throws | `SubjectRequirementServiceTest` | `SUBJECT_REQUIREMENT_NOT_FOUND`, not a raw JPA/`Optional.get()` exception |
| Full RBAC + validation + duplicate/quantity handling, real endpoints | `SubjectRequirementApiIT` (Testcontainers, environment-blocked here) | LAB_ASSISTANT can add/update/remove software, equipment, and lab-type requirements; CR/STUDENT get 403 on mutations but can read; unauthenticated gets 401; duplicate software/equipment requirement rejected with `SOFTWARE_REQUIREMENT_ALREADY_EXISTS`/`EQUIPMENT_REQUIREMENT_ALREADY_EXISTS`; zero-or-negative `requiredQuantity` rejected; a subject with no requirements returns the correctly-shaped empty response rather than a null/error |

**Manually verified against the Dockerized stack (2026-08-22):** `GET /api/subjects/{bdaId}/requirements` confirmed BDA requires Cloudera and prefers the Data Engineering lab type; `GET /api/subjects/{cnsId}/requirements` confirmed CNS returns an empty requirements list (proving the optional path, not just its absence of an error); a restart-and-recount idempotency check confirmed `subject_software_requirement` row count unchanged (1) and Phase 5's lab/software/equipment counts unaffected by Phase 6's changes; `/api/auth/me`, `/api/cr-assignments/me`, and the Phase 5 capacity/software lab filters were re-verified working with no regression.

## Academic Domain Tests — Implemented (Phase 4)

| Test | Class | What it proves |
|---|---|---|
| Exact batch assignment resolves correctly | `FacultyAssignmentResolutionServiceTest` | Batch-level match wins when it exists |
| Division-level fallback | `FacultyAssignmentResolutionServiceTest` | Falls back to division-level assignment when no batch-specific one exists |
| Batch-exact wins over division fallback | `FacultyAssignmentResolutionServiceTest` | Division fallback is never even consulted once an exact match is found |
| No assignment → clear not-found error | `FacultyAssignmentResolutionServiceTest` | Never silently guesses |
| CR owns their assigned division | `CrOwnershipServiceTest` | `requireOwnsDivision` passes for the correct division |
| CR cannot claim a different division | `CrOwnershipServiceTest` | `ForbiddenDivisionAccessException` for a division the CR doesn't own |
| Non-CR role rejected from ownership path | `CrOwnershipServiceTest` | `FORBIDDEN` even for an authenticated, real user |
| CR with no active assignment | `CrOwnershipServiceTest` | `CR_ASSIGNMENT_NOT_FOUND`, distinct from a wrong-division rejection |
| Non-CR user rejected from CrAssignment creation | `CrAssignmentServiceTest` | "target user must have role CR" enforced in application code |
| Batch from a different division rejected | `SubjectFacultyAssignmentServiceTest` | Cross-table "batch belongs to division" validation (can't be a DB CHECK constraint) |
| Full RBAC + ownership + validation, real endpoints | `AcademicApiIT` (Testcontainers, environment-blocked here — see docs/13-DEVELOPER-SETUP.md) | LAB_ASSISTANT can create, CR/STUDENT get 403, unauthenticated gets 401; batch/division mismatch → 400; CR `/me` works with and without an assignment; DB constraints (program/stream/faculty/batch uniqueness) are real |

**Manually verified against the Dockerized stack (2026-08-22, since Testcontainers is environment-blocked locally):** every scenario above was also exercised with real `curl` requests against the running containers — this is how two real bugs were caught and fixed this phase (see ASSUMPTIONS A-23, A-24): `@PreAuthorize` denials returning `500` instead of `403`, and `LazyInitializationException` on several read endpoints. Both fixed and re-verified before this phase was called done.

## Authorization Test Matrix (Phase 3, detailed in [09-AUTHORIZATION-RBAC.md](09-AUTHORIZATION-RBAC.md))

- Student cannot schedule, cannot cancel, cannot access any mutating endpoint → `403`.
- CR can schedule for own division; cannot schedule for another division even with a valid but foreign `divisionId`.
- CR can cancel own EXTRA allocation; cannot cancel another division's; cannot cancel any REGULAR allocation.
- Lab Assistant can manage CR assignments and accounts; CR cannot.
- Ended `cr_assignment` immediately loses division-scoped access (no stale-permission caching).

## Algorithm Test Coverage (Phase 10–14)

**Roadmap correction (Phase 8):** this section's heading previously read "Phase 8–14," a Phase-1-era label never revisited. Phase 8 (docs/03-SYSTEM-ARCHITECTURE.md §16) is now confirmed as the schema/domain-object foundation, not algorithm work — every scenario below (candidate generation, scoring, most-constrained-first, backtracking) is Phase 10 onward.

- Candidate generation returns exactly the labs satisfying capacity/software/equipment/type filters — verified against fixture data with known expected sets.
- Capacity-fit scoring formula matches the documented formula in [07-ALLOCATION-SCORING.md](07-ALLOCATION-SCORING.md) for hand-computed fixture values (e.g. strength 64 vs capacity 65/70/150 produces strictly descending scores).
- Balanced-utilization scoring favors a less-used lab when all else is equal.
- Most-constrained-first ordering: a fixture with one rare-software session and several flexible sessions is scheduled with the rare one first (assert ordering directly, not just final outcome).
- Backtracking: a fixture deliberately constructed so a greedy (non-backtracking) assignment would fail sessions D/E, but backtracking to reassign session C's lab produces a full valid schedule — this specific "greedy fails, backtracking succeeds" case is a named required test (per the phase brief).
- Impossible-schedule detection: a fixture with more sessions than any assignment can satisfy returns `PARTIAL` with correct `unresolved` list and per-session rejection reasons, not a silent failure or exception.
- Alternative recommendation: a rejected single request returns alternatives ordered same-time-different-lab → same-lab-different-time → same-day-different-slot, per PART 29's priority order.
- Explanation output: a selected candidate's `ScoreBreakdown` sums to `totalScore` and every listed factor's explanation string is non-empty.

## Concurrency Test (Phase 16 gate)

Fixture: Lab C-301, 09:00–11:00, no existing allocation. Two `CompletableFuture`/thread-pool tasks submit competing `POST /api/allocations/extra` requests for the same lab/time from two different (fixture) CR users simultaneously. **Assertion: exactly one succeeds with `201`, the other receives a conflict error (`LAB_CONFLICT` or equivalent) — never two `201`s, never zero.** This test must pass against the real chosen concurrency mechanism (ADR pending in [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md)) before Phase 16 is considered complete — per the phase brief's explicit gate ("do not continue until double booking is proven impossible under tested concurrency").

## PDF Import Tests (Phase 19)

- Extraction of a well-formed fixture PDF produces the expected set of `TimetableImportEntry` rows.
- A deliberately malformed/ambiguous fixture produces entries flagged `PENDING`/`CONFLICT`, never silently dropped or silently auto-corrected.
- Approval is blocked while any entry remains `PENDING`/`CONFLICT` (`CONFLICT` API error on `/approve`).
- Corrected entries re-run validation and can transition to `VALID`.

## What "Done" Looks Like Per Phase

Each implementation phase (Phase 4 onward) is not considered complete until: implementation compiles, its unit/integration tests exist and pass, and the relevant row(s) in this document's traceability tables are checked off with a note of which test class covers them (added incrementally — this document evolves alongside code, not written once and frozen).
