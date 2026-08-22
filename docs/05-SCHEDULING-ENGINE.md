# Scheduling Engine

## Problem Formulation

Given a set of session requirements (subject, target batch/division, requested or flexible date/time), assign each a (lab, faculty, time) triple such that:
- every hard constraint in [06-CONSTRAINTS.md](06-CONSTRAINTS.md) holds for every assigned session, and
- as many sessions as possible are assigned within a bounded search effort (not guaranteed globally optimal — see ADR-008 in [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md) for why a general CP solver was not chosen).

This is a **Constraint Satisfaction Problem (CSP)** for single-session validation, and a **CSP + heuristic search** problem for multi-session automatic generation (Phase 14).

## Domain Objects (decoupled from JPA/HTTP — NFR-08)

| Object | Role |
|---|---|
| `SchedulingRequest` | Immutable input: subject, targetType, divisionId, batchId?, requested date/time (or a flexible window for auto-generation) |
| `SchedulingContext` | Everything needed to evaluate: existing active allocations in the relevant window, faculty availability rows, lab inventory + software/equipment, subject requirements — loaded once per scheduling run, passed by reference to avoid repeated queries |
| `CandidateAllocation` | One (lab, faculty, time) combination under evaluation; never persisted directly |
| `ConstraintResult` | Pass/fail + `ConstraintViolation?` from one `SchedulingConstraint` |
| `ConstraintViolation` | `errorCode`, `message`, `details` — maps directly to the API error model |
| `ScoreBreakdown` | Per-factor scores + total, from the scoring engine |
| `AllocationDecision` | Final outcome: selected candidate + full explanation, or failure + alternatives |
| `AlternativeAllocation` | A ranked fallback suggestion when the originally requested slot fails |
| `SchedulingMetrics` | Counters: candidates evaluated, constraints checked, backtrack count, execution time — for [16-PERFORMANCE-BENCHMARKS.md](16-PERFORMANCE-BENCHMARKS.md) |

`constraint`, `scoring`, `conflict`, and the core scheduler operate **only** on these objects — never on JPA entities or DTOs directly. Application services in `allocation`/`schedule` are the translation boundary (load entities → build `SchedulingContext` → run engine → persist `AllocationDecision`).

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
