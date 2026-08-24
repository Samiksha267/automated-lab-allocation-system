# Hard Constraints

Every hard constraint is a `SchedulingConstraint` implementation (see [05-SCHEDULING-ENGINE.md](05-SCHEDULING-ENGINE.md)), independently unit-tested. Hard constraints are never weakened into score penalties — a violation is always a rejection, never a lower score (see [07-ALLOCATION-SCORING.md](07-ALLOCATION-SCORING.md) for why this separation matters).

## Scheduling Terminology

| Term | Definition |
|---|---|
| **Session** | A single planned occurrence of a subject's practical, at one date/time, for one batch or one division. |
| **Allocation** | The persisted record of a session assigned to a specific lab and faculty (the `allocation` table row). |
| **Candidate** | A *proposed* (lab, faculty, time) combination being evaluated for a session, before it is validated/scored/committed. |
| **Constraint** | A rule a candidate is checked against. |
| **Hard constraint** | A rule that, if violated, makes a candidate invalid — never selectable, regardless of score. |
| **Soft constraint (scoring factor)** | A rule that influences ranking among otherwise-valid candidates but never disqualifies one. |
| **Conflict** | The outcome when every generated candidate (or the one specifically requested) fails one or more hard constraints. |
| **Schedule version** | An immutable, timestamped snapshot of a term's official allocations (`schedule_version`); exactly one is `PUBLISHED` per term at a time. |
| **Regular allocation** | `allocation_type = REGULAR` — sourced from the official timetable / PDF import, requires Lab Assistant review and approval. |
| **Extra allocation** | `allocation_type = EXTRA` — CR-created makeup/additional practical, FCFS, no review step, but every hard constraint still applies. |
| **Division-wide allocation** | `target_type = DIVISION` — occupies the entire division; no batch within it may have a simultaneous session. |
| **Batch allocation** | `target_type = BATCH` — occupies only the named batch; sibling batches in the same division remain free. |

## Time Overlap — the one rule everything else depends on

All time comparisons use half-open intervals `[startTime, endTime)`. Two intervals A and B overlap iff:

```
startA < endB   AND   startB < endA
```

**Examples:**
- `09:00–11:00` vs `10:00–12:00` → `09:00 < 12:00` and `10:00 < 11:00` → **conflict**.
- `09:00–11:00` vs `11:00–13:00` → `09:00 < 13:00` and `11:00 < 11:00` → **false → no conflict** (back-to-back sessions are valid; the boundary instant belongs to the second session only).
- Naive equality checks (`startA == startB`) are explicitly forbidden by NFR-10 — they miss every partial overlap.

---

## HC-01 — Lab Conflict — **implemented (Phase 9)**

- **Rule:** One lab hosts at most one active (`APPROVED`/`PUBLISHED`) allocation at any overlapping instant, regardless of `target_type` or `allocation_type`.
- **Class:** `LabConflictConstraint` (`com.college.laballocation.scheduling.constraint`).
- **Inputs:** `candidate.lab().existingAllocations()` — pre-loaded per-candidate by `CandidateAllocationFactory` via `AllocationQueryService.findActiveForLab(labId, date)`; "active" is always `AllocationStatus.blocksScheduling()`, verified live to exclude `CANCELLED` rows.
- **Reject when:** any existing active allocation for the same lab overlaps the candidate interval (`TimeIntervalUtils.overlaps`) on the same `allocationDate` — the date check is a defensive re-verification, not solely trusted from the upstream query.
- **Valid example:** Lab C-301 has BDA 09:00–11:00; candidate CNS in Lab C-302 09:00–11:00 → different lab, no conflict.
- **Invalid example:** Lab C-301 has BDA 09:00–11:00; candidate CNS also in Lab C-301, 10:00–12:00 → overlaps → rejected.
- **Error code:** `LAB_CONFLICT`.

## HC-02 — Faculty Conflict — **implemented (Phase 9)**

- **Rule:** One faculty member teaches at most one overlapping session at a time, regardless of batch/division/lab.
- **Class:** `FacultyConflictConstraint`.
- **Inputs:** `context.existingFacultyAllocations()` — precomputed once per `SchedulingContext` by `SchedulingContextFactory` (candidate-independent, unlike HC-01) via `AllocationQueryService.findActiveForFaculty`.
- **Reject when:** the same faculty already has an overlapping active allocation — even in a different lab, even for a different batch.
- **Valid example:** Faculty "BDA Faculty" teaches A1 09:00–11:00 in C-301; Faculty "CNS Faculty" teaches A2 09:00–11:00 in C-302 → different faculty → no conflict.
- **Invalid example:** Faculty X teaches A1 09:00–11:00 in C-301 and is also requested for A2 09:00–11:00 in C-302 → same faculty, overlapping, different labs → **still rejected**.
- **Error code:** `FACULTY_CONFLICT`. Distinct from HC-03 (Faculty Availability) — verified with a dedicated test proving a faculty generally available but already booked passes HC-03 and fails HC-02 for the identical candidate.

## HC-03 — Faculty Availability — **implemented (Phase 9)**

- **Rule:** A session may only be scheduled within a faculty member's declared `faculty_availability` window(s) for that day of week and term.
- **Class:** `FacultyAvailabilityConstraint` — a thin wrapper around the existing, already-tested `FacultyAvailabilityService.isAvailable(...)` (Phase 7); no merging/containment logic duplicated. Day-of-week is always derived from the candidate's `allocationDate.getDayOfWeek()`, never accepted independently.
- **Reject when:** no availability row (or continuous run of adjacent rows) for that faculty/term/day fully contains the candidate interval — including when there are zero rows at all (absence means unavailable, never "available all day").
- **Valid example:** Faculty available Mon 09:00–13:00; candidate Mon 09:00–11:00 → contained → valid.
- **Invalid example:** Faculty available Mon 09:00–13:00; candidate Mon 12:30–14:30 → not fully contained → rejected.
- **Error code:** `FACULTY_UNAVAILABLE`.

## HC-04 — Batch Conflict — **implemented (Phase 9)**

- **Rule:** For a `target_type = BATCH` candidate, the same batch cannot have two overlapping active allocations. A different batch in the *same* division being busy is **irrelevant** to this check.
- **Class:** `BatchConflictConstraint` — deliberately does not check DIVISION-wide occupancy (that is `DivisionWideConflictConstraint`'s job, HC-05); a `DIVISION`-targeted candidate passes this constraint vacuously (no batch to conflict on).
- **Inputs:** `context.existingBatchAllocations()`, precomputed candidate-independent.
- **Reject when:** the same batch already has an overlapping active `BATCH` allocation.
- **Valid example:** A1 BDA 09:00–11:00 (Lab C-301, Faculty X); A2 CNS 09:00–11:00 (Lab C-302, Faculty Y) → different batches → **valid**, not rejected by HC-04 — verified both by unit test and live against real Docker/Postgres data with the actual seeded demo (BDA/A1/Faculty BDA/B-301 vs. CNS/A2/Faculty CNS/C-202).
- **Invalid example:** A1 BDA 09:00–11:00; new request A1 CNS 10:00–12:00 → same batch, overlapping → rejected.
- **Error code:** `BATCH_CONFLICT`.

## HC-05 — Division-Wide Conflict — **implemented (Phase 9)**

- **Rule:** For a `target_type = DIVISION` candidate, reject if (a) another division-wide allocation for the same division overlaps, **or** (b) any batch belonging to that division has an overlapping active allocation. Conversely, a new **BATCH** candidate must also be rejected if an overlapping **DIVISION**-wide allocation already exists for its division (occupancy is bidirectional — see the interaction matrix below).
- **Class:** `DivisionWideConflictConstraint`. `context.existingDivisionAllocations()` already contains both `DIVISION`-wide and `BATCH` rows for the division in one query (`division_id` is always set regardless of `target_type`) — this constraint only filters by `targetType`, no second joined query.
- **Reject when:**
  - candidate is `DIVISION` and an overlapping `DIVISION` or `BATCH` allocation exists anywhere in that division; or
  - candidate is `BATCH` and an overlapping `DIVISION`-wide allocation exists for its division.
- **Valid example:** No division-wide session scheduled; A1 and A2 both book batch-level sessions simultaneously → valid (governed by HC-04, not blocked by HC-05).
- **Invalid example:** Division A has a division-wide guest lecture 09:00–11:00; a new request for batch A2 at 10:00–11:00 → rejected, whole division is occupied.
- **Cross-division example (explicit — this is deliberately *not* a conflict):** Division A has a division-wide session 09:00–11:00; Division B (unrelated) has a division-wide session 09:00–11:00 → **valid** — `context.existingDivisionAllocations()` is already scoped to the candidate's own division, so a different division's rows never even appear in the list.
- **Error code:** `DIVISION_CONFLICT`. The full matrix (§ below) is directly unit-tested, six scenarios.

## HC-06 — Lab Availability (maintenance) — **implemented (Phase 9)**

- **Rule:** A lab under an active `lab_unavailability` window overlapping the candidate's requested interval cannot be scheduled; a permanently `active = false` lab is also rejected defensively.
- **Class:** `LabAvailabilityConstraint`. Bridges `Allocation`'s `LocalDate`/`LocalTime` to `Instant` via `SchedulingTimeMapper.toInstantRange(...)` (Phase 8, ADR-037), then compares against `candidate.lab().unavailabilityWindows()` (pre-loaded per-candidate by `CandidateAllocationFactory`) using `TimeIntervalUtils.overlaps(Instant, Instant, Instant, Instant)` — the same half-open formula, added as an `Instant` overload rather than a separate reimplementation.
- **Reject when:** candidate interval overlaps any `lab_unavailability` row for that lab.
- **Valid example:** Lab C-303 unavailable 2026-08-25 09:00–17:00; candidate 2026-08-26 09:00–11:00 → valid.
- **Invalid example:** Lab C-303 unavailable 2026-08-25 09:00–17:00; candidate 2026-08-25 10:00–12:00 → rejected (overlaps).
- **Error code:** `LAB_UNAVAILABLE`. Verified live in Docker with a real `LabUnavailability` row and the fixed `Asia/Kolkata` zone.

## HC-07 — Capacity — **implemented (Phase 9)**

- **Rule:** Candidate lab's `capacity` must be ≥ the required strength (`batch.strength` for BATCH, `division.strength` for DIVISION).
- **Class:** `CapacityConstraint` — pure ≥ comparison, no fit scoring (a 150-seat lab is a valid candidate even if inefficient; ranking by tightness of fit is Phase 11's Capacity Fit factor, never a hard rejection here).
- **Reject when:** `lab.capacity < required_strength`.
- **Valid example:** Batch strength 64, Lab capacity 65 → valid.
- **Invalid example:** Batch strength 64, Lab capacity 60 → rejected.
- **Error code:** `CAPACITY_VIOLATION`, with `details: {requiredCapacity, labCapacity}`.

## HC-08 — Required Software — **implemented (Phase 9)**

- **Rule:** Every software row in `subject_software_requirement` for the subject must be present in `lab_software` for the candidate lab (`R_s ⊆ L_ℓ`, ALL-required semantics). If the subject has zero software requirements, this check always passes.
- **Class:** `RequiredSoftwareConstraint` — queries `SubjectSoftwareRequirementRepository` directly (subject requirements are deliberately not preloaded into `SchedulingContext`, see docs/05-SCHEDULING-ENGINE.md), compares against `candidate.lab().softwareCodes()` (pre-loaded per-candidate).
- **Reject when:** any required software is missing from the candidate lab.
- **Valid example:** BDA requires Cloudera; Lab C-202 has Cloudera installed → accepted. Verified live against real Docker/Postgres seeded data.
- **Invalid example:** BDA requires Cloudera; Lab C-304 does not have Cloudera despite matching capacity → rejected, even though capacity independently passes (proves constraints combine independently, not short-circuited by an unrelated pass). Verified live.
- **Error code:** `SOFTWARE_MISMATCH`, with `details: {missingSoftware: [...]}`.

## HC-09 — Required Equipment — **implemented (Phase 9)**

- Same structure as HC-08, over `subject_equipment_requirement` (`required_quantity`) / `lab_equipment` (`quantity`): for every required row, `labEquipment.quantity >= subjectEquipmentRequirement.requiredQuantity`.
- **Class:** `RequiredEquipmentConstraint` — a lab with no association row for a required equipment item is treated as `available = 0` (a plain `Map.getOrDefault`, never a null-pointer failure).
- **Error code:** `EQUIPMENT_MISMATCH`, with `details: {shortfalls: [{equipment, required, available}, ...]}`.

## HC-10 — Required Lab Type — **implemented (Phase 9)**

- **Rule:** If `subject.required_lab_type_id` is set, candidate `lab.lab_type_id` must match. If unset, any lab type is acceptable.
- **Class:** `RequiredLabTypeConstraint` — reads only `context.subject().requiredLabTypeId()` (added to `SubjectRef` in Phase 9 specifically for this constraint); `preferredLabTypeId` has no representation anywhere this constraint can see it, structurally preventing it from ever gating validity — verified with a dedicated "preferred-only never fails HC-10" test, both unit and live in Docker.
- **Error code:** `LAB_TYPE_MISMATCH`.

### Formalized Compatibility Rules — Implemented Phase 9

The mathematical model formalized in Phase 6 is now real, executable code, not just a specification:

**Software** (subset relation):
```
R_s = { software required by subject s }   (from subject_software_requirement)
L_ℓ = { software installed in lab ℓ }        (from lab_software)

valid(s, ℓ)  iff  R_s ⊆ L_ℓ
```
Example: `R_BDA = {Cloudera}`. `L_C-202 = {Cloudera, Hadoop, Spark}` → `{Cloudera} ⊆ L_C-202` → valid. `L_C-304 = {}` → `{Cloudera} ⊄ L_C-304` → invalid. Both verified live in Docker with the real seeded data.

**Equipment** (per-item quantity comparison):
```
for every (equipment e, requiredQuantity q) required by subject s:
    availableQuantity(ℓ, e) >= q
```

**Lab type** (single-value equality or absence):
```
valid(s, ℓ)  iff  s.requiredLabType == null  OR  ℓ.labType == s.requiredLabType
```

## HC-11 — CR Authorization — **implemented (Phase 9)**

- **Rule:** A CR may only create/cancel allocations for the division resolved from their own current `ACTIVE` `cr_assignment` (see [04-DATABASE-DESIGN.md §1](04-DATABASE-DESIGN.md)); a `divisionId`/`batchId` supplied in the request that does not belong to that division is rejected regardless of any other validity.
- **Class:** `CrAuthorizationConstraint`. **Applicability, not just pass/fail:** `SchedulingRequest.actor()` (added Phase 9, nullable `SchedulingActor{userId, role}`) is `null` for automated/internal scheduling contexts (e.g. future REGULAR generation, Phase 14) or belongs to `LAB_ASSISTANT` → `NOT_APPLICABLE` (a third `ConstraintOutcome`, counted as non-failing but distinct from `PASS` — this constraint never fails a request just because no CR is involved). Only when `actor.role() == CR` does the ownership check actually run.
- **Reject when:** actor role is CR and the resolved current assignment's division ≠ the request's division (or no active assignment exists at all).
- **Error code:** `FORBIDDEN_DIVISION_ACCESS` (or `CR_ASSIGNMENT_NOT_FOUND` if the CR has no active assignment).
- **Note:** this is the one hard constraint that is fundamentally an authorization check, not a scheduling-resource check — it is still modeled as a `SchedulingConstraint` so it participates in the same validation pipeline and produces the same structured-error shape.
- **A real bug found and fixed here:** the first implementation called `CrOwnershipService.requireOwnsDivision(...)` and caught its thrown `ApiException` family to build a `FAIL` result. Manual Docker verification caught a genuine problem: `requireOwnsDivision` is itself `@Transactional`, so the exception crossing that method boundary marks the *shared* surrounding transaction rollback-only before the catch block runs — the surrounding transaction later failed with `UnexpectedRollbackException` even though this constraint had already produced a correct result. Fixed by switching to `CrOwnershipService.getCurrentAssignment(userId)` (`Optional`-returning, never throws) and comparing directly — no exception ever crosses a transactional boundary for this expected-failure path now. See docs/14-INTERVIEW-PREPARATION.md "Real Engineering Problems Encountered."

## HC-12 — Academic Relationship Validity — **implemented (Phase 9)**

- **Rule:** Every referenced ID must form a coherent hierarchy: `batch.division_id` must match `allocation.division_id`; `subject` must belong to the same `academic_year` as the target division; `subject_faculty_assignment` must exist and match the requested faculty for the resolved `(subject, division/batch, term)` combination (so an arbitrary faculty can't be attached to a subject they don't teach).
- **Class:** `AcademicRelationshipConstraint` — evaluated first in the engine's deterministic order (§ below), since a candidate with incoherent academic relationships isn't usefully checked against resource-conflict rules. Three sub-checks, in order, returning on the first failure (internal-only fail-fast within this one constraint; the engine itself still evaluates every other HC regardless):
  1. Batch belongs to division (`BATCH` candidates only) — `context.batch().divisionId() == context.division().id()`.
  2. Subject belongs to the request's academic hierarchy — `context.subject().academicYearId() == context.division().academicYearId()` (both added to their respective `SchedulingRefs` in Phase 9 specifically for this check — no redundant field invented, reusing the Phase 4 relationship).
  3. Faculty matches the authoritative assignment — re-resolves via `FacultyAssignmentResolutionService` (Phase 4) and compares against `SchedulingRequest.facultyId()`; never silently substitutes the resolved faculty.
- **Reject when:** any referenced entity doesn't belong together (e.g. a Year-3 CS subject requested for a Year-2 IT division), or the requested faculty doesn't match the authoritative assignment.
- **Error code:** `INVALID_ACADEMIC_RELATIONSHIP`.

## Constraint Engine Architecture — **implemented (Phase 9)**

`ConstraintEngine` (`com.college.laballocation.scheduling.constraint`) receives a `SchedulingContext` + one `CandidateAllocation` and runs every Spring-discovered `SchedulingConstraint` (`List<SchedulingConstraint>` auto-injected, one `@Component` per HC above), sorted into a fixed, documented evaluation order (HC-12, HC-07, HC-08, HC-09, HC-10, HC-03, HC-06, HC-01, HC-02, HC-04, HC-05, HC-11 — cheap/foundational checks first, resource-conflict checks last; `HardConstraintId`'s own declared enum order, HC-01..HC-12, is untouched). **Evaluates every constraint, never fails fast** — a candidate failing capacity, software, and faculty availability simultaneously reports all three `ConstraintViolation`s together, proven by both a unit test with a controlled fixture and live Docker verification. Returns a `ConstraintEvaluation{valid, results, violations}`. Every constraint is read-only — no `Allocation` row, lab reservation, or status change happens during evaluation (verified: `POST /api/allocations` still returns 404, no production creation path exists). See docs/05-SCHEDULING-ENGINE.md for the full architecture.

**Consumed by `CandidateGenerator` (Phase 10):** `ConstraintEngine.evaluate(...)` is called once per candidate lab, for every lab in the system, by the new `CandidateGenerator` (`com.college.laballocation.scheduling.generation`) — one `ConstraintEvaluation` per lab, none skipped, none prefiltered by a duplicate check. `CandidateGenerator` contains no constraint logic of its own; this file's rules remain the single source of truth for validity. See docs/05-SCHEDULING-ENGINE.md "Candidate Generation."

**Consumed by `ExplainableAllocationService` (Phase 12), never re-evaluated:** each `ConstraintResult` already produced above (PASS/FAIL/NOT_APPLICABLE, plus a FAIL's `ConstraintViolation`) is read directly from the already-computed `CandidateGenerationResult` and wrapped in a `ConstraintCheckExplanation`/`ViolationExplanation` (`com.college.laballocation.scheduling.explanation`) that adds only a display label — the machine `HardConstraintId`/`errorCode` is preserved unchanged alongside it. `ConstraintEngine.evaluate(...)` is never called a second time to produce an explanation. `ConstraintOutcome.NOT_APPLICABLE` (HC-11) is represented as such explicitly, never rendered as if it were `PASS` — see docs/05-SCHEDULING-ENGINE.md "Explainable Allocation."

**Classified by `ConflictClassification` (Phase 13) — structural vs. temporal, never a semantic change:** Phase 13 needed to know which failures changing the *requested time* could plausibly fix. This is purely a categorization layer over the same thirteen error codes above (`com.college.laballocation.scheduling.conflict.ConflictClassification`) — no `SchedulingConstraint` class was touched, no error code was added or renamed, and no HC's pass/fail logic changed in any way.

| Constraint | Error code | Category | Why |
|---|---|---|---|
| HC-01 Lab Conflict | `LAB_CONFLICT` | TEMPORAL | A different time may find the lab free |
| HC-02 Faculty Conflict | `FACULTY_CONFLICT` | TEMPORAL | A different time may find the faculty free |
| HC-03 Faculty Availability | `FACULTY_UNAVAILABLE` | TEMPORAL | A different time may fall inside an available window |
| HC-04 Batch Conflict | `BATCH_CONFLICT` | TEMPORAL | A different time avoids the batch's existing session |
| HC-05 Division-Wide Conflict | `DIVISION_CONFLICT` | TEMPORAL | A different time avoids the division-wide session |
| HC-06 Lab Availability | `LAB_UNAVAILABLE` | TEMPORAL | A different time may fall outside the unavailability window |
| HC-07 Capacity | `CAPACITY_VIOLATION` | STRUCTURAL | A lab's capacity does not change by time of day |
| HC-08 Required Software | `SOFTWARE_MISMATCH` | STRUCTURAL | Installed software does not change by time of day |
| HC-09 Required Equipment | `EQUIPMENT_MISMATCH` | STRUCTURAL | Installed equipment does not change by time of day |
| HC-10 Required Lab Type | `LAB_TYPE_MISMATCH` | STRUCTURAL | A lab's type does not change by time of day |
| HC-11 CR Authorization | `FORBIDDEN_DIVISION_ACCESS` / `CR_ASSIGNMENT_NOT_FOUND` | STRUCTURAL | An authorization fact about the actor, not the time |
| HC-12 Academic Relationship | `INVALID_ACADEMIC_RELATIONSHIP` | STRUCTURAL | A fact about the request's academic hierarchy, not the time |

A candidate lab is "structurally viable" (`ConflictAnalysis.structurallyViableLabIds()`) iff none of its violations fall in the STRUCTURAL column above — see docs/05-SCHEDULING-ENGINE.md "Conflict Analysis + Alternative Suggestions" for how this single classification drives the entire alternative-time-search decision.

**Extended (Phase 14) to see provisional occupancy — same lists, same logic, no new code path:** HC-01/02/04/05 are the four constraints that check *occupancy* (as opposed to a static capability like HC-07/08/09/10) — each already reads its conflict data from a plain `List<ExistingAllocationSnapshot>` (`candidate.lab().existingAllocations()` for HC-01; `context.existingFacultyAllocations()`/`existingBatchAllocations()`/`existingDivisionAllocations()` for HC-02/04/05). Phase 14's automatic-scheduling search needs these same four constraints to also see *this search's own* not-yet-persisted decisions (so two requirements in one automatic-scheduling call can never be assigned the same lab/faculty/batch/division at an overlapping time) — without duplicating any of HC-01/02/04/05's own conflict-detection logic a second time inside the search.

The mechanism: `SchedulingContextFactory`/`CandidateAllocationFactory` (Phase 8/9) gained new, additive overloads that accept a `SchedulingSearchState` and append its matching provisional snapshots onto the *exact same* lists these four constraints already iterate. Not one line inside `LabConflictConstraint`/`FacultyConflictConstraint`/`BatchConflictConstraint`/`DivisionWideConflictConstraint` changed — each constraint has no way to tell, and no need to know, whether a given `ExistingAllocationSnapshot` in the list it's reading came from a persisted PostgreSQL row or from a decision this search made two levels up the recursion. See docs/05-SCHEDULING-ENGINE.md "Automatic Scheduling / Multi-Session Backtracking" for the full design investigation and the real bug this integration surfaced (a `null` provisional allocation id crashing each constraint's `Map.of(...)`-built violation-details map, fixed with a non-null sentinel entirely inside Phase 14's own `PlannedAllocation`, never inside these constraint classes).

---

## Conflict Interaction Matrix (HC-04 / HC-05 combined view)

This matrix covers **academic occupancy only** (batch/division busy-ness). Lab conflict (HC-01) and faculty conflict (HC-02) are independent checks that always apply in addition to this matrix — a request can pass this matrix and still be rejected for lab or faculty reasons.

| Existing allocation | Requested allocation | Same division? | Same batch? | Academic conflict? | Governing constraint |
|---|---|:---:|:---:|:---:|---|
| BATCH A1 | BATCH A1 | yes | yes | **yes** | HC-04 |
| BATCH A1 | BATCH A2 | yes | no | **no** | HC-04 (passes) |
| BATCH A1 | DIVISION A | yes | n/a | **yes** | HC-05 |
| DIVISION A | BATCH A2 | yes | n/a | **yes** | HC-05 |
| DIVISION A | DIVISION A | yes | n/a | **yes** | HC-05 |
| BATCH A1 (Division A) | BATCH B1 (Division B) | no | no | **no** | — (different division entirely) |
| DIVISION A | DIVISION B | no | n/a | **no** | — (different division entirely; HC-05 only compares allocations sharing the same `division_id`) |

**Reading the matrix:** the only academically-*valid* simultaneous combination is two different batches of the same division, both requested/existing as `BATCH`-type. Every combination involving a `DIVISION`-type row (existing or requested) is a conflict for that division, because a division-wide session by definition occupies every batch beneath it. This directly encodes the confirmed business rule at the top of this phase's brief: *"different batches of the same division MAY have labs simultaneously"* is the **only** exception to an otherwise-occupied academic resource, and it is never expressed as a blanket "division busy" flag. Every row of this matrix is unit-tested (`DivisionWideConflictConstraintTest`), and the two headline rows (BATCH A1/BATCH A2 valid, and the reverse DIVISION-vs-BATCH direction) are additionally verified live against real Docker/Postgres data with the actual seeded BDA/CNS demo.

## Rejection Reporting

Every rejected candidate carries: `errorCode`, human-readable `message`, and `details` (the specific conflicting allocation — id, lab, faculty, time — or the specific missing software/equipment). See [10-API-DOCUMENTATION.md](10-API-DOCUMENTATION.md#error-model) for the wire format. `ConstraintViolation` (Phase 8, populated for real starting Phase 9) is the structured carrier — never an untyped `Map`-only design.
