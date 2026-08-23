# Scheduling Engine

**Status (Phase 9):** The Constraint Engine now exists and is real, tested code — `ConstraintEngine`, `SchedulingConstraint`, `ConstraintEvaluation`, and all twelve HC-01..HC-12 implementations (`com.college.laballocation.scheduling.constraint`) evaluate a `CandidateAllocation` against a `SchedulingContext` and return structured `ConstraintResult`s. **No candidate generation, scoring, alternative search, or backtracking exists yet** — Phase 9 establishes whether one already-identified candidate is *valid*; Phase 10 generates the candidate list in the first place (docs/03-SYSTEM-ARCHITECTURE.md §17). `AllocationDecision`, `ScoreBreakdown`, `AlternativeAllocation`, and `SchedulingMetrics` remain design-only, deliberately deferred to the phases that actually need them (Phase 11-13) rather than built now as empty scaffolding.

## The Constraint Engine (Phase 9)

```
SchedulingContext + CandidateAllocation
        ↓
ConstraintEngine.evaluate(context, candidate)
        ↓
List<SchedulingConstraint>  (Spring-discovered, sorted into a fixed evaluation order)
        ↓
List<ConstraintResult>   (every constraint runs - never fails fast)
        ↓
ConstraintEvaluation{valid, results, violations}
```

**All-results evaluation, never fail-fast:** every registered constraint runs against the candidate regardless of earlier failures, so a candidate that simultaneously fails capacity, software, and faculty availability reports all three violations together - this supports explainability (Phase 12) and alternative ranking (Phase 13) without needing to re-evaluate later. Verified with a dedicated unit test (a controlled fixture failing three constraints at once) and live in Docker.

**No mutation:** constraint evaluation is entirely read-only - no `Allocation` row is created, no lab is reserved, no status changes. Verified live: `POST /api/allocations` and `GET /api/allocations` both still return `404` after Phase 9's work - no production allocation-creation path exists, and none was added just to test constraints (a temporary, dev-profile-only `ApplicationRunner` was used for manual verification instead, then deleted - see docs/11-TESTING-STRATEGY.md).

**Context reuse (avoiding N+1):** `SchedulingContext` (candidate-independent: subject/faculty/division/batch identity, existing faculty/batch/division allocations) is built once per request by `SchedulingContextFactory`. `CandidateAllocation` (candidate-specific: lab capacity/type/software/equipment, existing lab allocations, unavailability windows) is built once per candidate lab by the new `CandidateAllocationFactory` - introduced in Phase 9 specifically so HC-01/06/07/08/09/10 each read from one pre-loaded `LabRef` snapshot instead of six independent re-queries of the same lab. Subject software/equipment requirements are the one deliberate exception - they stay outside both objects and are queried directly by HC-08/HC-09 from their dedicated repositories, since duplicating that data into a context that only two constraints need would be a stale-copy risk for no real benefit (the same reasoning Phase 8 already applied when it excluded them from `SchedulingContext`).

**Applicability - PASS / FAIL / NOT_APPLICABLE:** `ConstraintResult` (Phase 8) gained a third outcome (`ConstraintOutcome`, Phase 9) specifically for HC-11 (CR Authorization), which is meaningless for automated/internal scheduling contexts (no CR actor at all) or for a `LAB_ASSISTANT` actor - `NOT_APPLICABLE` is deliberately distinct from `PASS`: it means "this rule was never meaningfully evaluated," not "this candidate satisfies it." Both count as non-failing for overall candidate validity. See ADR in docs/15-DESIGN-DECISIONS.md.

**Deterministic evaluation order:** cheap/foundational checks first (HC-12, HC-07..HC-10), then availability (HC-03, HC-06), then resource conflicts (HC-01, HC-02, HC-04, HC-05), then authorization (HC-11) - a presentation/logging concern only, since no constraint's correctness depends on another constraint's outcome. `HardConstraintId`'s own declared numbering (HC-01..HC-12) is untouched; the evaluation order is a separate, explicit list inside `ConstraintEngine`.

## Persisted State vs. Transient Domain Objects (Phase 8)

This project's scheduling model has two genuinely different kinds of object, and Phase 8 is the phase that made the distinction concrete rather than just conceptual:

| | Persisted (JPA entity) | Transient (plain Java record/class, never an entity) |
|---|---|---|
| **What it represents** | A fact that happened (or was formally decided) | A possibility being considered, evaluated once and then discarded |
| **Examples** | `ScheduleVersion`, `Allocation` | `SchedulingRequest`, `SchedulingContext`, `CandidateAllocation`, `ConstraintResult`, `ConstraintViolation` |
| **Lifecycle** | Survives across requests, versioned, auditable | Exists only for the duration of one scheduling evaluation |
| **Why the split matters** | Every persisted row must satisfy real invariants (CHECK constraints, FKs) since it's permanent | Most candidates evaluated during a scheduling run are rejected — persisting them would be pure waste, and would blur "this was actually booked" with "this was considered and rejected" |

`CandidateAllocation` in particular is deliberately never a JPA entity (PART 22 of the Phase 8 brief, ADR-038) — it is the direct transient counterpart of `Allocation`, evaluated by the Phase 9 constraint engine and discarded the moment a decision is made, whether or not it wins.

## Faculty Availability → Constraint Validation (Phase 7 data, Phase 9 constraint) — **implemented**

```
SubjectFacultyAssignment resolves "which faculty teaches this" (Phase 4, FacultyAssignmentResolutionService)
      ↓
FacultyAvailabilityService.isAvailable(facultyId, academicTermId, dayOfWeek, start, end)   (Phase 7 - reusable domain logic, not itself a constraint)
      ↓
FacultyAvailabilityConstraint (Phase 9) wraps the above as a SchedulingConstraint
      ↓
Rejected candidates carry ConstraintViolation{errorCode: "FACULTY_UNAVAILABLE", ...} (docs/06-CONSTRAINTS.md HC-03)
```

`FacultyAvailabilityService` reuses `TimeIntervalUtils` (`com.college.laballocation.common`, half-open `[start, end)` semantics — `isValid`/`overlaps`/`contains`) for all interval math, and Phase 9's `FacultyConflictConstraint` (HC-02) and `LabConflictConstraint` (HC-01) share the exact same formulas rather than each reimplementing them (PART 8 of the Phase 7 brief, realized in Phase 9). `LabAvailabilityConstraint` (HC-06) additionally uses the new `Instant` overload of `TimeIntervalUtils.overlaps` (Phase 9) for the same half-open comparison over `SchedulingTimeMapper`-bridged instants.

## Problem Formulation

Given a set of session requirements (subject, target batch/division, requested or flexible date/time), assign each a (lab, faculty, time) triple such that:
- every hard constraint in [06-CONSTRAINTS.md](06-CONSTRAINTS.md) holds for every assigned session, and
- as many sessions as possible are assigned within a bounded search effort (not guaranteed globally optimal — see ADR-008 in [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md) for why a general CP solver was not chosen).

This is a **Constraint Satisfaction Problem (CSP)** for single-session validation, and a **CSP + heuristic search** problem for multi-session automatic generation (Phase 14).

## Domain Objects (decoupled from JPA/HTTP — NFR-08)

| Object | Status | Role |
|---|---|---|
| `SchedulingRequest` | **Implemented (Phase 8, extended Phase 9)** | Immutable record: `allocationType`, `targetType`, `divisionId`, `batchId?`, `subjectId`, **`facultyId` already resolved** (see below), `academicTermId`, `allocationDate`, `startTime`, `endTime`, **`actor?` (Phase 9)** — a nullable `SchedulingActor{userId, role}` resolved before the request exists, the same way `facultyId` is, for HC-11. Self-validates the target/batch structural invariant and the time interval in its compact constructor — no Spring, no database. |
| `SchedulingContext` | **Implemented (Phase 8, extended Phase 9)** | Everything needed to evaluate a request that does **not** vary per candidate lab: resolved subject/faculty/division/batch identity snapshots (Phase 9 added `academicYearId` to Subject/Division refs for HC-12, and `requiredLabTypeId` to Subject ref for HC-10), plus existing active allocations for the faculty/batch/division on the requested date (candidate-independent — HC-02/04/05's inputs). Deliberately excludes lab-specific data (existing lab allocations, lab unavailability) since that varies per candidate and is queried once per candidate instead (HC-01/06) — see docs/03-SYSTEM-ARCHITECTURE.md §16/17. Assembled by `SchedulingContextFactory` from existing Phase 4/5 services + Phase 8's `AllocationQueryService`; the class itself performs no queries. |
| `CandidateAllocation` | **Implemented (Phase 8 shape, populated Phase 9)** | `(SchedulingContext, LabRef)` - `LabRef` (Phase 9) replaced the Phase 8 bare `labId`/`labCode` with a full candidate-specific snapshot (capacity, lab type, installed software/equipment, existing lab allocations, unavailability windows), built once per candidate by the new `CandidateAllocationFactory` so HC-01/06/07/08/09/10 never independently re-query the same lab. Never persisted, never scored, never generated by this phase (candidate generation is Phase 10). |
| `ConstraintResult` | **Implemented (Phase 8 shape, extended Phase 9)** | `(HardConstraintId, ConstraintOutcome, ConstraintViolation?)` from one `SchedulingConstraint` - `ConstraintOutcome` (Phase 9: PASS/FAIL/NOT_APPLICABLE) replaced Phase 8's plain `boolean passed`, specifically for HC-11's applicability semantics. Self-validates that a FAIL result carries a violation and a PASS/NOT_APPLICABLE result never does. |
| `ConstraintViolation` | **Implemented (Phase 8), populated for real (Phase 9)** | `errorCode`, `message`, `affectedResourceType`, `affectedResourceId`, `details` — maps directly to the API error model, deliberately not an untyped `Map`-only shape. |
| `HardConstraintId` | **Implemented (Phase 8)** | Stable enum (`HC_01_LAB_CONFLICT` .. `HC_12_ACADEMIC_RELATIONSHIP`) identifying *which* constraint produced a `ConstraintResult` — kept separate from the wire-level API error codes in `ConstraintViolation`. |
| `ConstraintEngine` / `ConstraintEvaluation` / `SchedulingConstraint` | **Implemented (Phase 9)** | The engine itself, its aggregate result type, and the interface every HC-01..HC-12 class implements. See "The Constraint Engine" section above. |
| `ScoreBreakdown` | Not yet implemented | Per-factor scores + total, from the scoring engine — Phase 11. |
| `AllocationDecision` | Not yet implemented, deliberately deferred | Final outcome: selected candidate + full explanation, or failure + alternatives. Its real shape depends on scoring (Phase 11) and alternatives (Phase 13), neither of which exist yet; building it now would be speculative scaffolding rather than a tested contract. Phase 12 (Explainable Allocation) is where it belongs. |
| `AlternativeAllocation` | Not yet implemented | A ranked fallback suggestion when the originally requested slot fails — Phase 13. |
| `SchedulingMetrics` | Not yet implemented | Counters: candidates evaluated, constraints checked, backtrack count, execution time — for [16-PERFORMANCE-BENCHMARKS.md](16-PERFORMANCE-BENCHMARKS.md), Phase 14/25. |

`constraint`, `scoring`, `conflict`, and the core scheduler operate **only** on these objects — never on JPA entities or DTOs directly (verified: none of `SchedulingRequest`/`SchedulingContext`/`CandidateAllocation`/`ConstraintResult`/`ConstraintViolation`/`ConstraintEvaluation` carries a single JPA annotation). Application services in the `scheduling` package are the translation boundary (load entities → build `SchedulingContext` via `SchedulingContextFactory` → build `CandidateAllocation` via `CandidateAllocationFactory` → `ConstraintEngine` validates → Phase 15/19 will persist an `Allocation`).

**Faculty resolution happens before a `SchedulingRequest` exists (PART 15 of the Phase 8 brief):** `facultyId` is never resolved *by* the request — the preferred architecture is `external scheduling input → resolve academic/faculty context (FacultyAssignmentResolutionService, Phase 4) → SchedulingRequest`, so the future constraint engine always receives an unambiguous request and never itself has to decide "which faculty teaches this."

## Single-Request Validation Pipeline (used by both extra-lab booking and PDF-import validation)

```
SchedulingRequest
      ↓
Resolve SchedulingContext (load relevant allocations, availability, inventory)     [implemented, Phase 8]
      ↓
Generate Candidate Labs (active + capacity ≥ required + required software/equipment/type present)   [Phase 10]
      ↓
Validate Hard Constraints (HC-01..HC-12) against each candidate    [ConstraintEngine - implemented, Phase 9]
      ↓
Remove Invalid Candidates (recording ConstraintViolation per rejection)
      ↓
Score Remaining Candidates (07-ALLOCATION-SCORING.md)    [Phase 11]
      ↓
Rank Candidates
      ↓
Return AllocationDecision: ranked valid candidates + explained rejections
```

## Multi-Session Automatic Generation (Phase 14) — Pseudocode

```
function generateSchedule(requests: List<SchedulingRequest>, context: SchedulingContext) -> ScheduleResult:
    ordered = orderByMostConstrainedFirst(requests, context)
    return backtrack(ordered, index=0, partialAssignment={}, context, metrics)

function backtrack(ordered, index, partialAssignment, context, metrics) -> ScheduleResult:
    if index == len(ordered):
        return SUCCESS(partialAssignment)
    if metrics.attempts > MAX_ATTEMPTS or metrics.elapsed > TIMEOUT or depth(partialAssignment) > MAX_DEPTH:
        return PARTIAL(partialAssignment, unresolved = ordered[index:])

    request = ordered[index]
    candidates = generateAndScoreCandidates(request, context, partialAssignment)   // pipeline above, scoped to context + tentative assignments so far
    for candidate in candidates.sortedByScoreDescending():
        metrics.candidateEvaluations += 1
        tentativelyApply(partialAssignment, request, candidate)
        result = backtrack(ordered, index + 1, partialAssignment, context, metrics)
        if result is SUCCESS:
            return result
        undo(partialAssignment, request, candidate)   // backtrack
        metrics.backtrackCount += 1

    return FAILURE_AT(request)   // triggers caller to try previous request's next candidate, or report unresolved
```

### Most-Constrained-First Heuristic

```
difficulty(request) = 1 / max(1, numberOfValidCandidates(request, context))
```

Requests are sorted by descending `difficulty` before search begins — a session needing rare software, large capacity, or a faculty with a narrow availability window is scheduled first, while flexible sessions (few requirements, many valid labs) are scheduled last, since they're the easiest to still satisfy after earlier, harder sessions have consumed resources. This is recomputed once per top-level `generateSchedule` call (not re-sorted at every backtrack step, to bound cost — see complexity note below); if this proves too coarse in practice (Phase 14 implementation may reveal it), a dynamic re-ordering variant will be documented as a follow-up, not assumed to work perfectly a priori.

### Backtracking, Pruning, and Search Limits

- **Pruning:** candidates are generated already hard-constraint-filtered (never generate an invalid candidate just to reject it during backtracking) — this is the primary pruning mechanism, not a separate step.
- **`maxAttempts`:** total candidate-assignment attempts across the whole search (configurable, e.g. 5000 for a realistic term-sized problem).
- **`maxDepth`:** the search never backtracks further back than this many completed assignments before giving up on full-solution search and returning the best partial result found (protects against pathological thrashing).
- **`timeout`:** wall-clock budget (e.g. 10s) as a hard stop regardless of attempts/depth remaining — this is the safety net for demo/interactive use.
- **Failure diagnostics:** on `PARTIAL` result, `SchedulingMetrics` + the list of `unresolved` requests (each with their last-seen rejection reasons) are returned — never a bare "scheduling failed."

### Complexity

Backtracking search over a CSP is worst-case **exponential** in the number of sessions (each session has up to `|labs| × |validTimeSlots|` candidate assignments, and the search explores a tree of depth = number of sessions). This is **not** claimed to be polynomial anywhere in this project's documentation. In practice:
- Hard-constraint pre-filtering keeps the *branching factor* per session small (usually far fewer than `|labs|` valid candidates once software/capacity/faculty-availability narrow the field).
- Most-constrained-first ordering empirically reduces backtracking by resolving the tightest sessions — with the fewest valid options — while the most resource slack still remains, so later (easier) sessions rarely force a backtrack.
- The configured `maxAttempts`/`maxDepth`/`timeout` bound worst-case runtime at the cost of potentially returning a partial (not full) schedule on pathological inputs — this trade-off is deliberate and documented, not hidden.
- Real, measured numbers (not estimates) for the actual dataset size (~15 labs, tens of sessions) will be recorded in [16-PERFORMANCE-BENCHMARKS.md](16-PERFORMANCE-BENCHMARKS.md) once Phase 14/25 produce them — no number is asserted here in advance.

## Limitations (documented honestly, per project rules against overclaiming)

- No guarantee of a globally *optimal* schedule — only a valid one found within budget, ranked by local (per-session) scoring at assignment time.
- A `PARTIAL` result requires a human (Lab Assistant) to resolve remaining unscheduled sessions manually or by relaxing an input (e.g., widening a time window) — the engine does not automatically negotiate trade-offs across sessions beyond backtracking.
- Faculty/lab preference scoring factors are local heuristics, not lookahead — the engine does not attempt to foresee that a "good enough" choice now will block a much better outcome for a later session, beyond what backtracking naturally corrects when that later session fails outright.
