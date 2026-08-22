# Scheduling Engine

**Status (Phase 8):** The persisted half of this document's world now exists — `Allocation`/`ScheduleVersion` (docs/04-DATABASE-DESIGN.md §7) — and every transient domain object in the table below (`SchedulingRequest`, `SchedulingContext`, `CandidateAllocation`, `ConstraintResult`, `ConstraintViolation`) is implemented as a real, tested Java type in `com.college.laballocation.scheduling`, decoupled from JPA/HTTP exactly as NFR-08 requires. **No constraint evaluation, candidate generation, scoring, or backtracking exists yet** — Phase 8 only establishes what a request/candidate/allocation *is*; Phase 9 establishes whether a candidate is *valid* (docs/03-SYSTEM-ARCHITECTURE.md §16). `AllocationDecision`, `ScoreBreakdown`, `AlternativeAllocation`, and `SchedulingMetrics` remain design-only, deliberately deferred to the phases that actually need them (Phase 11-13) rather than built now as empty scaffolding.

## Persisted State vs. Transient Domain Objects (Phase 8)

This project's scheduling model has two genuinely different kinds of object, and Phase 8 is the phase that made the distinction concrete rather than just conceptual:

| | Persisted (JPA entity) | Transient (plain Java record/class, never an entity) |
|---|---|---|
| **What it represents** | A fact that happened (or was formally decided) | A possibility being considered, evaluated once and then discarded |
| **Examples** | `ScheduleVersion`, `Allocation` | `SchedulingRequest`, `SchedulingContext`, `CandidateAllocation`, `ConstraintResult`, `ConstraintViolation` |
| **Lifecycle** | Survives across requests, versioned, auditable | Exists only for the duration of one scheduling evaluation |
| **Why the split matters** | Every persisted row must satisfy real invariants (CHECK constraints, FKs) since it's permanent | Most candidates evaluated during a scheduling run are rejected — persisting them would be pure waste, and would blur "this was actually booked" with "this was considered and rejected" |

`CandidateAllocation` in particular is deliberately never a JPA entity (PART 22 of the Phase 8 brief, ADR-038) — it is the direct transient counterpart of `Allocation`, evaluated by the future Phase 9 constraint engine and discarded the moment a decision is made, whether or not it wins.

## Faculty Availability → Future Constraint Validation (Phase 7 data, Phase 9 constraint)

```
SubjectFacultyAssignment resolves "which faculty teaches this" (Phase 4, FacultyAssignmentResolutionService)
      ↓
FacultyAvailabilityService.isAvailable(facultyId, academicTermId, dayOfWeek, start, end)   (Phase 7 - reusable domain logic, not itself a constraint)
      ↓
FacultyAvailabilityConstraint (Phase 9+ - not yet implemented) wraps the above as a SchedulingConstraint
      ↓
Rejected candidates carry ConstraintViolation{errorCode: "FACULTY_UNAVAILABLE", ...} (docs/06-CONSTRAINTS.md HC-03)
```

`FacultyAvailabilityService` reuses `TimeIntervalUtils` (`com.college.laballocation.common`, half-open `[start, end)` semantics — `isValid`/`overlaps`/`contains`) for all interval math, specifically so the future `FacultyAvailabilityConstraint`, `FacultyConflictConstraint` (HC-02), and `LabConflictConstraint` (HC-01) can share the same overlap/containment formulas rather than each reimplementing them (PART 8 of the Phase 7 brief). This is the first concrete reuse of that shared utility — introduced now so later constraint classes have it ready rather than retrofitting a shared formula after three near-duplicate implementations already exist.

## Problem Formulation

Given a set of session requirements (subject, target batch/division, requested or flexible date/time), assign each a (lab, faculty, time) triple such that:
- every hard constraint in [06-CONSTRAINTS.md](06-CONSTRAINTS.md) holds for every assigned session, and
- as many sessions as possible are assigned within a bounded search effort (not guaranteed globally optimal — see ADR-008 in [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md) for why a general CP solver was not chosen).

This is a **Constraint Satisfaction Problem (CSP)** for single-session validation, and a **CSP + heuristic search** problem for multi-session automatic generation (Phase 14).

## Domain Objects (decoupled from JPA/HTTP — NFR-08)

| Object | Status | Role |
|---|---|---|
| `SchedulingRequest` | **Implemented (Phase 8)** | Immutable record: `allocationType`, `targetType`, `divisionId`, `batchId?`, `subjectId`, **`facultyId` already resolved** (see below), `academicTermId`, `allocationDate`, `startTime`, `endTime`. Self-validates the target/batch structural invariant and the time interval in its compact constructor — no Spring, no database. |
| `SchedulingContext` | **Implemented (Phase 8)** | Everything needed to evaluate a request that does **not** vary per candidate lab: resolved subject/faculty/division/batch identity snapshots, plus existing active allocations for the faculty/batch/division on the requested date (candidate-independent — HC-02/04/05's inputs). Deliberately excludes lab-specific data (existing lab allocations, lab unavailability) since that varies per candidate and is queried once per candidate instead (HC-01/06) — see docs/03-SYSTEM-ARCHITECTURE.md §16. Assembled by `SchedulingContextFactory` from existing Phase 4/5 services + this phase's `AllocationQueryService`; the class itself performs no queries. |
| `CandidateAllocation` | **Implemented (Phase 8), shape only** | One `(SchedulingContext, labId)` combination under evaluation - never persisted, never scored, never generated by this phase (candidate generation is Phase 10). |
| `ConstraintResult` | **Implemented (Phase 8), shape only** | `(HardConstraintId, passed, ConstraintViolation?)` from one future `SchedulingConstraint` (Phase 9) - self-validates that a passing result carries no violation and a failing one always does. |
| `ConstraintViolation` | **Implemented (Phase 8)** | `errorCode`, `message`, `affectedResourceType`, `affectedResourceId`, `details` — maps directly to the API error model, deliberately not an untyped `Map`-only shape. |
| `HardConstraintId` | **Implemented (Phase 8)** | Stable enum (`HC_01_LAB_CONFLICT` .. `HC_12_ACADEMIC_RELATIONSHIP`) identifying *which* constraint produced a `ConstraintResult` — kept separate from the wire-level API error codes in `ConstraintViolation`. |
| `ScoreBreakdown` | Not yet implemented | Per-factor scores + total, from the scoring engine — Phase 11. |
| `AllocationDecision` | Not yet implemented, deliberately deferred | Final outcome: selected candidate + full explanation, or failure + alternatives. Considered for Phase 8 and deferred (ADR discussion, docs/15-DESIGN-DECISIONS.md) — its real shape depends on scoring (Phase 11) and alternatives (Phase 13), neither of which exist yet; building it now would be speculative scaffolding rather than a tested contract. Phase 12 (Explainable Allocation) is where it belongs. |
| `AlternativeAllocation` | Not yet implemented | A ranked fallback suggestion when the originally requested slot fails — Phase 13. |
| `SchedulingMetrics` | Not yet implemented | Counters: candidates evaluated, constraints checked, backtrack count, execution time — for [16-PERFORMANCE-BENCHMARKS.md](16-PERFORMANCE-BENCHMARKS.md), Phase 14/25. |

`constraint`, `scoring`, `conflict`, and the core scheduler will operate **only** on these objects — never on JPA entities or DTOs directly (verified now: none of `SchedulingRequest`/`SchedulingContext`/`CandidateAllocation`/`ConstraintResult`/`ConstraintViolation` carries a single JPA annotation). Application services in the `scheduling` package are the translation boundary (load entities → build `SchedulingContext` via `SchedulingContextFactory` → Phase 9+ will run the engine → Phase 15/19 will persist an `Allocation`).

**Faculty resolution happens before a `SchedulingRequest` exists (PART 15 of the Phase 8 brief):** `facultyId` is never resolved *by* the request — the preferred architecture is `external scheduling input → resolve academic/faculty context (FacultyAssignmentResolutionService, Phase 4) → SchedulingRequest`, so the future constraint engine always receives an unambiguous request and never itself has to decide "which faculty teaches this."

## Single-Request Validation Pipeline (used by both extra-lab booking and PDF-import validation)

```
SchedulingRequest
      ↓
Resolve SchedulingContext (load relevant allocations, availability, inventory)
      ↓
Generate Candidate Labs (active + capacity ≥ required + required software/equipment/type present)
      ↓
Validate Hard Constraints (HC-01..HC-12) against each candidate
      ↓
Remove Invalid Candidates (recording ConstraintViolation per rejection)
      ↓
Score Remaining Candidates (07-ALLOCATION-SCORING.md)
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
