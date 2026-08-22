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

## HC-01 — Lab Conflict

- **Rule:** One lab hosts at most one active (`APPROVED`/`PUBLISHED`) allocation at any overlapping instant, regardless of `target_type` or `allocation_type`.
- **Inputs:** `lab_id`, `date`, `[start_time, end_time)` of candidate; all active allocations for the same `lab_id`/`date`.
- **Reject when:** any existing active allocation for the same lab on the same date overlaps the candidate interval.
- **Valid example:** Lab C-301 has BDA 09:00–11:00; candidate CNS in Lab C-302 09:00–11:00 → different lab, no conflict.
- **Invalid example:** Lab C-301 has BDA 09:00–11:00; candidate CNS also in Lab C-301, 10:00–12:00 → overlaps → rejected.
- **Source data implemented, Phase 8:** the `allocation` table and `AllocationQueryService.findActiveForLab(labId, date)` both exist and are independently verified (docs/04-DATABASE-DESIGN.md §7) — "active" is always `AllocationStatus.blocksScheduling()` (`APPROVED`/`PUBLISHED`), proven to exclude `CANCELLED` rows in a live query test. **Not implemented yet:** the actual `LabConflictConstraint` class that runs this query per candidate and applies `TimeIntervalUtils.overlaps(...)` — Phase 9.
- **Error code:** `LAB_CONFLICT`.

## HC-02 — Faculty Conflict

- **Rule:** One faculty member teaches at most one overlapping session at a time, regardless of batch/division/lab.
- **Inputs:** `faculty_id`, `date`, candidate interval; all active allocations for that `faculty_id`/`date`.
- **Reject when:** the same faculty already has an overlapping active allocation — even in a different lab, even for a different batch.
- **Valid example:** Faculty "BDA Faculty" teaches A1 09:00–11:00 in C-301; Faculty "CNS Faculty" teaches A2 09:00–11:00 in C-302 → different faculty → no conflict.
- **Invalid example:** Faculty X teaches A1 09:00–11:00 in C-301 and is also requested for A2 09:00–11:00 in C-302 → same faculty, overlapping, different labs → **still rejected**.
- **Source data implemented, Phase 8:** `AllocationQueryService.findActiveForFaculty(facultyId, date)` — precomputed once per `SchedulingContext` (candidate-independent, unlike HC-01) since faculty conflict never depends on which lab is being evaluated. **Not implemented yet:** the actual `FacultyConflictConstraint` class — Phase 9. Distinct from HC-03 (Faculty Availability, Phase 7) — see docs/03-SYSTEM-ARCHITECTURE.md for the availability-vs-conflict distinction.
- **Error code:** `FACULTY_CONFLICT`.

## HC-03 — Faculty Availability

- **Rule:** A session may only be scheduled within a faculty member's declared `faculty_availability` window(s) for that day of week and term.
- **Inputs:** `faculty_id`, `academic_term_id`, `day_of_week` derived from `date`, candidate interval; matching active `faculty_availability` rows.
- **Reject when:** no availability row (or continuous run of adjacent rows) for that faculty/term/day fully contains the candidate interval — including when there are zero rows at all (absence means unavailable, never "available all day").
- **Valid example:** Faculty available Mon 09:00–13:00; candidate Mon 09:00–11:00 → contained → valid.
- **Invalid example:** Faculty available Mon 09:00–13:00; candidate Mon 12:30–14:30 → not fully contained → rejected.
- **Source data implemented, Phase 7:** `faculty_availability` (mandatory `academic_term_id`, half-open `[start_time, end_time)`, overlap-rejected at write time, adjacent rows permitted — see docs/04-DATABASE-DESIGN.md §6) and `FacultyAvailabilityService.isAvailable(...)` (the reusable evaluation logic — merges adjacent stored rows for evaluation only, never mutates the database) both exist and are independently verified today. **Not implemented yet:** the actual `FacultyAvailabilityConstraint` class that plugs this evaluation into the scheduling pipeline (`SchedulingContext` → candidate validation, docs/05-SCHEDULING-ENGINE.md) — Phase 9+. Distinct from HC-02 (Faculty Conflict, an already-*booked* overlap) — see docs/03-SYSTEM-ARCHITECTURE.md for the availability-vs-conflict distinction.
- **Error code:** `FACULTY_UNAVAILABLE`.

## HC-04 — Batch Conflict

- **Rule:** For a `target_type = BATCH` candidate, the same batch cannot have two overlapping active allocations. A different batch in the *same* division being busy is **irrelevant** to this check.
- **Inputs:** `batch_id`, `date`, candidate interval; all active `BATCH`-type allocations for that `batch_id`/`date` **plus** any active `DIVISION`-type allocation for the batch's `division_id`/`date` (see HC-05 interaction below — division-wide occupancy blocks batches too, but that check is HC-05's responsibility so HC-04 stays focused purely on same-batch overlap).
- **Reject when:** the same batch already has an overlapping active `BATCH` allocation.
- **Valid example:** A1 BDA 09:00–11:00 (Lab C-301, Faculty X); A2 CNS 09:00–11:00 (Lab C-302, Faculty Y) → different batches → **valid**, not rejected by HC-04.
- **Invalid example:** A1 BDA 09:00–11:00; new request A1 CNS 10:00–12:00 → same batch, overlapping → rejected.
- **Source data implemented, Phase 8:** `AllocationQueryService.findActiveForBatch(batchId, date)` — precomputed once per `SchedulingContext`, candidate-independent. **Not implemented yet:** the actual `BatchConflictConstraint` class — Phase 9.
- **Error code:** `BATCH_CONFLICT`.

## HC-05 — Division-Wide Conflict

- **Rule:** For a `target_type = DIVISION` candidate, reject if (a) another division-wide allocation for the same division overlaps, **or** (b) any batch belonging to that division has an overlapping active allocation. Conversely, a new **BATCH** candidate must also be rejected if an overlapping **DIVISION**-wide allocation already exists for its division (occupancy is bidirectional — see the interaction matrix below). This second direction is checked here, not in HC-04, because it depends on `DIVISION`-type rows.
- **Inputs:** `division_id`, `date`, candidate interval, candidate `target_type`; all active `DIVISION`-type allocations for that division, and (when candidate is `DIVISION`) all active `BATCH`-type allocations for any batch under that division.
- **Reject when:**
  - candidate is `DIVISION` and an overlapping `DIVISION` or `BATCH` allocation exists anywhere in that division; or
  - candidate is `BATCH` and an overlapping `DIVISION`-wide allocation exists for its division.
- **Valid example:** No division-wide session scheduled; A1 and A2 both book batch-level sessions simultaneously → valid (governed by HC-04, not blocked by HC-05).
- **Invalid example:** Division A has a division-wide guest lecture 09:00–11:00; a new request for batch A2 at 10:00–11:00 → rejected, whole division is occupied.
- **Cross-division example (explicit — this is deliberately *not* a conflict):** Division A has a division-wide session 09:00–11:00; Division B (unrelated) has a division-wide session 09:00–11:00 → **valid**, HC-05 only ever compares allocations within the *same* `division_id`; two different divisions running division-wide sessions simultaneously is not an academic conflict at all (it may still be rejected by HC-01/HC-02 if they happen to share a lab or faculty, but that is those constraints' job, not HC-05's).
- **Source data implemented, Phase 8:** `AllocationQueryService.findActiveForDivision(divisionId, date)` returns **both** `DIVISION`-wide and `BATCH` rows for that division with a single query, no join required — `division_id` is always set on every `Allocation` row regardless of `target_type` (docs/04-DATABASE-DESIGN.md §7), so the bidirectional check this rule describes is already directly queryable today; the future `DivisionConflictConstraint` (Phase 9) only needs to filter the returned rows by `targetType` and apply the overlap rule, not run two separate joined queries.
- **Error code:** `DIVISION_CONFLICT`.

## HC-06 — Lab Availability (maintenance)

- **Rule:** A lab under an active `lab_unavailability` window overlapping the candidate's requested interval cannot be scheduled.
- **Source data (implemented, Phase 5 + Phase 8):** `lab.active` (permanent deactivation — a `false` lab is never a candidate at all) and `lab_unavailability` (`start_date_time`/`end_date_time`, `TIMESTAMPTZ`, half-open `[start, end)` — see [04-DATABASE-DESIGN.md §4](04-DATABASE-DESIGN.md)). The third input, real allocation data, now exists too (`allocation`, Phase 8) — all three data sources HC-06 will combine are real as of this phase; only the comparison logic itself remains unwritten.
- **The type bridge this constraint will need:** `Allocation`'s `allocation_date`/`start_time`/`end_time` are `LocalDate`/`LocalTime`; `lab_unavailability`'s columns are `TIMESTAMPTZ`/`Instant`. `SchedulingTimeMapper` (Phase 8, `com.college.laballocation.scheduling`) is the resolved, tested bridge between the two (`toInstant`/`toInstantRange`, using the configurable `app.college.time-zone`) — introduced now specifically so `LabAvailabilityConstraint` doesn't have to invent this conversion itself, or worse, three future constraints each invent a slightly different one. See ADR-037.
- **Reject when (once implemented):** candidate interval overlaps any `lab_unavailability` row for that lab, using the same half-open interval overlap rule as everywhere else in this document (not the date-only comparison this document's Phase 1 draft originally sketched — Phase 5 implemented full datetime granularity, see docs/04-DATABASE-DESIGN.md).
- **Valid example:** Lab C-303 unavailable 2026-08-25 09:00–17:00; candidate 2026-08-26 09:00–11:00 → valid.
- **Invalid example:** Lab C-303 unavailable 2026-08-25 09:00–17:00; candidate 2026-08-25 10:00–12:00 → rejected (overlaps).
- **Error code:** `LAB_UNAVAILABLE`.
- **Not implemented yet:** the actual `LabAvailabilityConstraint` class that reads all three sources and performs the comparison (now that the timezone bridge exists, this is purely a matter of writing the constraint itself) — Phase 9.

## HC-07 — Capacity

- **Rule:** Candidate lab's `capacity` must be ≥ the required strength (`batch.strength` for BATCH, `division.strength` for DIVISION).
- **Capacity source of truth (implemented, Phase 4):** `batch.strength` and `division.strength` are plain, required, positive integer columns (`chk_batch_strength_positive`, `chk_division_strength_positive` — see V3 migration) maintained directly by the Lab Assistant. No `Student` entity exists or is planned for this purpose (A-13 in ASSUMPTIONS.md) — a single maintained number per batch/division is sufficient for capacity checking and avoids unnecessary enrollment-data upkeep. The sum of a division's batch strengths is **not** enforced to equal the division's own strength (see `Batch` entity javadoc / ASSUMPTIONS.md) — that would be an invented invariant college reality doesn't require.
- **Reject when:** `lab.capacity < required_strength`.
- **Valid example:** Batch strength 64, Lab capacity 65 → valid.
- **Invalid example:** Batch strength 64, Lab capacity 60 → rejected.
- **Error code:** `CAPACITY_VIOLATION`.
- **Not implemented yet:** this constraint itself (the actual `CapacityConstraint` class) belongs to the constraint engine, Phase 9+ — Phase 4 only establishes the data source it will read from.

## HC-08 — Required Software

- **Rule:** Every software row in `subject_software_requirement` for the subject must be present in `lab_software` for the candidate lab (ALL-required semantics — see [04-DATABASE-DESIGN.md §5](04-DATABASE-DESIGN.md)). If the subject has zero software requirements, this check always passes.
- **Both source-data halves now implemented, still not combined:** the "subject requires" half (`subject_software_requirement`, Phase 6 — `SubjectRequirementService`) and the "lab has" half (`lab_software`, Phase 5 — `LabSpecifications.hasAllSoftware`) both exist and are independently queryable/verified today, using **matching ALL-required semantics by construction** (both were designed against the same rule, docs/15-DESIGN-DECISIONS.md). What still doesn't exist is the code that *compares* them for a given (subject, lab) pair — that is the actual `RequiredSoftwareConstraint`, Phase 9+.
- **Reject when (once implemented):** any required software is missing from the candidate lab.
- **Valid example:** BDA requires Cloudera (verified: `GET /api/subjects/{id}/requirements`); Lab C-202 has Cloudera installed (verified: `GET /api/labs?software=CLOUDERA` includes C-202) → the constraint engine will later combine these two independently-verified facts and accept C-202.
- **Invalid example:** BDA requires Cloudera; Lab C-304 does not have Cloudera despite matching capacity → the constraint engine will later reject C-304 (this exact scenario's two halves are both seeded and independently verified — see docs/13-DEVELOPER-SETUP.md — but the rejection itself is not yet computed by any code).
- **Error code:** `SOFTWARE_MISMATCH`.
- **Not implemented yet:** the actual `RequiredSoftwareConstraint` class that reads both tables and compares them — Phase 9.

## HC-09 — Required Equipment

- Same structure as HC-08, over `subject_equipment_requirement` (implemented, Phase 6 — includes `required_quantity`) / `lab_equipment` (implemented, Phase 5 — `quantity`). Future rule: for every required row, `labEquipment.quantity >= subjectEquipmentRequirement.requiredQuantity`.
- **Error code:** `EQUIPMENT_MISMATCH`.
- **Not implemented yet:** the actual `RequiredEquipmentConstraint` class that compares the two — Phase 9.

## HC-10 — Required Lab Type

- **Rule:** If `subject.required_lab_type_id` is set, candidate `lab.lab_type_id` must match. If unset, any lab type is acceptable.
- **Both halves now implemented, still not combined:** `subject.required_lab_type_id` (Phase 6) and `lab.lab_type_id` + static filtering by type code (`GET /api/labs?labType=...`, Phase 5) both exist. **Not the same concept as `subject.preferred_lab_type_id`** (Phase 6) — see [07-ALLOCATION-SCORING.md](07-ALLOCATION-SCORING.md) for the required-vs-preferred distinction; only `required_lab_type_id` feeds HC-10, `preferred_lab_type_id` never gates validity, only scoring.
- **Error code:** `LAB_TYPE_MISMATCH`.
- **Not implemented yet:** the actual `RequiredLabTypeConstraint` class — Phase 9.

### Future Compatibility Rules — Formalized Now, Implemented in Phase 9

Both sides of HC-08/09/10's data exist as of Phase 6; this is the precise rule the future `RequiredSoftwareConstraint`/`RequiredEquipmentConstraint`/`RequiredLabTypeConstraint` classes will implement — specified here so the model is unambiguous before the code is written, not decided ad hoc later.

**Software** (subset relation):
```
R_s = { software required by subject s }   (from subject_software_requirement)
L_ℓ = { software installed in lab ℓ }        (from lab_software)

valid(s, ℓ)  iff  R_s ⊆ L_ℓ
```
Example: `R_BDA = {Cloudera}`. `L_C-202 = {Cloudera, Hadoop, Spark}` → `{Cloudera} ⊆ L_C-202` → valid. `L_C-304 = {}` → `{Cloudera} ⊄ L_C-304` → invalid.

**Equipment** (per-item quantity comparison):
```
for every (equipment e, requiredQuantity q) required by subject s:
    availableQuantity(ℓ, e) >= q
```

**Lab type** (single-value equality or absence):
```
valid(s, ℓ)  iff  s.requiredLabType == null  OR  ℓ.labType == s.requiredLabType
```

These are specifications only as of Phase 6 — no `Constraint` class evaluates them yet (PART 58/60 of the phase brief). Phase 5's static lab filters and Phase 6's requirement retrieval already let a caller answer each side of these questions independently (e.g. `GET /api/subjects/{id}/requirements` and `GET /api/labs?software=...` can be cross-referenced manually, as verified in this phase's manual Docker scenario) — Phase 9 is what will make the combination itself a single, automatic, reusable check.

## HC-11 — CR Authorization

- **Rule:** A CR may only create/cancel allocations for the division resolved from their own current `ACTIVE` `cr_assignment` (see [04-DATABASE-DESIGN.md §1](04-DATABASE-DESIGN.md)); a `divisionId`/`batchId` supplied in the request that does not belong to that division is rejected regardless of any other validity.
- **Reject when:** resolved CR division ≠ target division of the request.
- **Error code:** `FORBIDDEN_DIVISION_ACCESS`.
- **Note:** this is the one hard constraint that is fundamentally an authorization check, not a scheduling-resource check — it is still modeled as a `SchedulingConstraint` so it participates in the same validation pipeline and produces the same structured-error shape, but it runs first, before any resource-conflict constraint, since there's no reason to compute candidates for a request that's unauthorized in the first place.

## HC-12 — Academic Relationship Validity

- **Rule:** Every referenced ID must form a coherent hierarchy: `batch.division_id` must match `allocation.division_id`; `subject` must belong to the same `academic_year`/`stream` as the target division; `subject_faculty_assignment` must exist for the resolved `(subject, division/batch, term)` combination (so an arbitrary faculty can't be attached to a subject they don't teach).
- **Reject when:** any referenced entity doesn't belong together (e.g. a Year-3 CS subject requested for a Year-2 IT division).
- **Error code:** `INVALID_ACADEMIC_RELATIONSHIP`.

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

**Reading the matrix:** the only academically-*valid* simultaneous combination is two different batches of the same division, both requested/existing as `BATCH`-type. Every combination involving a `DIVISION`-type row (existing or requested) is a conflict for that division, because a division-wide session by definition occupies every batch beneath it. This directly encodes the confirmed business rule at the top of this phase's brief: *"different batches of the same division MAY have labs simultaneously"* is the **only** exception to an otherwise-occupied academic resource, and it is never expressed as a blanket "division busy" flag.

## Rejection Reporting

Every rejected candidate carries: `errorCode`, human-readable `message`, and `details` (the specific conflicting allocation — id, lab, faculty, time — or the specific missing software/equipment). See [10-API-DOCUMENTATION.md](10-API-DOCUMENTATION.md#error-model) for the wire format.
