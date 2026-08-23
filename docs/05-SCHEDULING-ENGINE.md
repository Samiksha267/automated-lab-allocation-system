# Scheduling Engine

**Status (Phase 13):** Candidate Generation (Phase 10), Scoring (Phase 11), Explainable Allocation (Phase 12), and now Conflict Analysis + Alternative Suggestions (Phase 13) all exist as tested code. `CandidateGenerator` (`com.college.laballocation.scheduling.generation`) takes one `SchedulingRequest`, builds its `SchedulingContext` once, and produces an `EvaluatedCandidate` for every lab via the unmodified Phase 9 `ConstraintEngine`. `ScoringEngine` (`com.college.laballocation.scheduling.scoring`) ranks only the *valid* candidates using Capacity Fit, Preferred Lab Type, and Balanced Utilization. `ExplainableAllocationService` (`com.college.laballocation.scheduling.explanation`, Phase 12) combines both into one structured `AllocationRecommendation`. `ConflictAnalyzer` (`com.college.laballocation.scheduling.conflict`) and `AlternativeSuggestionService` (`com.college.laballocation.scheduling.alternative`, Phase 13) sit one layer above: when the requested time has no valid candidate, they classify why (structural vs. temporal) and, only when genuinely worthwhile, search a small, bounded set of alternative times using the exact same generate→score→explain pipeline. **No candidate is ever automatically selected/persisted, no backtracking, no moving of existing sessions, no FCFS/CR-booking** — every result remains advisory, describing a snapshot, never a booking. `SchedulingMetrics` remains design-only, deferred to Phase 25.

## Candidate Generation (Phase 10)

```
SchedulingRequest
        ↓
SchedulingContextFactory.build            (once per request)
        ↓
SchedulingContext
        ↓
CandidateGenerator: for every lab, in code order (lab.code ascending)
        ↓
CandidateAllocationFactory.build(context, labId)   (once per lab)
        ↓
CandidateAllocation
        ↓
ConstraintEngine.evaluate(context, candidate)      (once per lab - the real, unmodified Phase 9 engine)
        ↓
EvaluatedCandidate{candidate, constraintEvaluation}
        ↓
CandidateGenerationResult{request, evaluatedCandidates}
```

**All labs considered, no first-fit:** every lab in the system becomes exactly one candidate, evaluated through the real `ConstraintEngine` - there is no early return the moment a valid lab is found (PART 19 of the Phase 10 brief; this is precisely the naive "return the first available lab" behavior this project's whole premise argues against). Verified live in Docker: the 15-lab dev seed produces exactly 15 evaluated candidates for a single request, regardless of how many are valid. (Phase 10's original completion report observed 16 labs; Phase 11's pre-phase investigation traced the 16th to a manually-created `E-101` row left over in the persistent Docker volume from an earlier manual-verification session - not a `DevLabSeeder` defect. `DevLabSeeder` itself was already fully idempotent and seeds exactly 15; the stray row was deleted, see docs/15-DESIGN-DECISIONS.md.)

**Both valid and invalid candidates are preserved:** `CandidateGenerationResult` retains every `EvaluatedCandidate`, not just the valid ones - `validCandidates()`/`invalidCandidates()` are filtered views over the same underlying list, computed on demand, never a second generation pass. Rejected candidates keep their full `ConstraintViolation` list attached, which Phase 12 (explainability) and Phase 13 (alternatives) will read directly rather than re-running the engine.

**Zero valid candidates is a normal result, never an exception:** if every lab fails some constraint, `CandidateGenerationResult.validCandidates()` is simply empty - verified live by temporarily inflating a batch's required strength so every lab fails HC-07, confirming generation still completes and returns a well-formed (all-invalid) result rather than throwing.

**No constraint duplication in the generator:** `CandidateGenerator` contains no capacity/software/availability conditionals of its own - the *only* filtering decision it makes is which labs exist to iterate over (every lab in the system) and in what order to return them (deterministic, lab code ascending - never a preference ranking). All validity decisions still belong exclusively to the Phase 9 `ConstraintEngine`, so Phase 9 and Phase 10 can never disagree about whether a candidate is valid.

**Candidate universe includes inactive/unavailable labs (Design B, ADR in docs/15-DESIGN-DECISIONS.md):** the generator does not prefilter out `active = false` labs or labs with an overlapping `LabUnavailability` window before generating a candidate - it queries every lab and lets HC-06 (`LabAvailabilityConstraint`) reject them structurally, so a rejected candidate carries a real, explainable violation rather than silently vanishing from the considered set. At the current ~15-16 lab scale this costs nothing meaningful.

**Context built once, reused for every candidate:** `SchedulingContextFactory.build(request)` runs exactly once per `generate(...)` call (verified with a dedicated Mockito test asserting exactly one invocation regardless of lab count) - the resulting `SchedulingContext` (subject/faculty/division/batch identity, existing faculty/batch/division allocations) is passed unchanged into every one of the N `CandidateAllocationFactory.build(...)` and `ConstraintEngine.evaluate(...)` calls that follow, so candidate-independent data is never reloaded per lab.

**Query strategy, honestly documented:** for N labs, generation issues one `SchedulingContextFactory` read (≈7 queries, once) plus, per lab, `CandidateAllocationFactory`'s existing Phase 9 loading (lab entity, installed software, installed equipment, existing lab allocations, unavailability windows - 5 queries). At the current ~15-16 lab scale this is roughly 75-85 total queries for one full generation run - a real, bounded N+1 shape, not eliminated in this phase. `CandidateAllocationFactory` (Phase 9) was deliberately not rewritten into a bulk loader here: Phase 9's behavior and tests were left untouched (PART 4/75 of the Phase 10 brief - do not rebuild Phase 9 without evidence of a real problem), and 75-85 queries for one interactive candidate-search request is not a demonstrated bottleneck at this project's scale. If a future phase's usage pattern makes this a real cost, the documented optimization path is a bulk variant (e.g. `LabSoftwareRepository.findByLabIdIn(labIds)` grouped by lab) rather than N separate per-lab queries - not built now because no evidence currently justifies it (Phase 25 benchmarks formally).

**Read-only, advisory, and stateless between calls:** the whole `generate(...)` call runs inside one `@Transactional(readOnly = true)` boundary (the default propagation joins `SchedulingContextFactory`/`CandidateAllocationFactory`/`ConstraintEngine`'s own read-only transactions into the same one) - no write occurs, no lab is reserved, no result is cached. A candidate valid *at generation time* is not a booking; another request could occupy the same lab before any future booking commit actually happens. Phase 16 is responsible for revalidating and committing safely under concurrency - Phase 10 deliberately does not attempt to solve that here.

## Scoring Engine (Phase 11)

```
CandidateGenerationResult
        ↓
validCandidates()                                  (invalid candidates are never scored)
        ↓
LabUtilizationService.scheduledMinutesByLab(...)   (once per scoring run, not once per candidate)
        ↓
ScoringContext{schedulingContext, loadByLab, minLoad, maxLoad}
        ↓
for every valid candidate: run every registered AllocationScorer
        ↓
ScoreContribution[] (per candidate, per factor)
        ↓
ScoredCandidate{evaluatedCandidate, contributions, totalScore, maxPossibleScore}
        ↓
sort by normalizedScore descending, then lab.code ascending (tie-break)
        ↓
ScoringResult{request, rankedCandidates, validCandidateCount, enabledFactors}
```

**Readiness analysis came before any scoring code (docs/07-ALLOCATION-SCORING.md's readiness matrix):** of the six originally-proposed factors, three had real, non-fabricated data behind them - Capacity Fit (already-validated `lab.capacity` vs. target strength), Preferred Lab Type (`subject.preferredLabTypeId`, distinct from HC-10's `requiredLabTypeId`), and Balanced Utilization (real `Allocation` rows, scoped to a schedule version). The other three were deferred, not faked: Additional Environment Fit has no "preferred/recommended software" concept anywhere in the schema (only all-required software/equipment joins); Faculty Preference has only `FacultyAvailability` (allowed windows), never a persisted lab/time *preference*; Fewer Timetable Gaps is structurally meaningless at this phase's architecture, since every candidate for one `CandidateGenerationResult` shares the exact same `date`/`startTime`/`endTime` - only the lab varies, so no lab choice can change a timetable gap. No `FacultyPreference` table, "preferred software" column, or working-hours concept was invented to manufacture these factors (PART 66 of the Phase 11 brief).

**Hard constraints always override soft scoring:** `ScoringEngine.score(CandidateGenerationResult)` reads `generationResult.validCandidates()` only - an invalid candidate is structurally unreachable by any scorer, regardless of how favorable its soft factors would be. Verified live in Docker with a deliberately adversarial candidate (undersized capacity, but matching the subject's preferred lab type) - it never appears in the ranking.

**Applicable maximum, not a fixed denominator:** a candidate's `maxPossibleScore` sums only the `maxPoints` of factors that actually applied to it (`ScoreApplicability.APPLIED`) - a subject with no preferred lab type gets a `PREFERRED_LAB_TYPE` contribution of `ScoreApplicability.NOT_APPLICABLE` (0 of 0), excluded entirely from both numerator and denominator, never a fabricated `0/15` penalty or a dishonest `15/15` freebie. Scores are reported as `raw/applicableMax` (e.g. `39.58/60.0`) with a separately-computed `normalizedScore()` percentage for ranking - never assumed to be "out of 100."

**Capacity Fit formula:** `fitRatio = required / labCapacity; score = weight * fitRatio` - since every valid candidate already satisfies `labCapacity >= required` (HC-07), `fitRatio` is always in `(0, 1]`, rewarding the closest fit rather than the largest lab, with no division-by-zero risk.

**Preferred Lab Type:** `NOT_APPLICABLE` (weight excluded entirely) when the subject records no preference; otherwise full weight on a lab-type match, zero (but still `APPLIED`, still counted toward the applicable max) on a mismatch - the candidate remains valid either way, only its score changes.

**Balanced Utilization:** `LabUtilizationService` sums each candidate lab's scheduled minutes (`REGULAR` and `EXTRA` alike) within the requesting term's currently `PUBLISHED` `ScheduleVersion` only - never an absolute percentage, since no working-days/daily-operating-hours concept exists anywhere in this project to divide by. Min-max normalized across the candidate set: `score = weight * (maxLoad - candidateLoad) / (maxLoad - minLoad)`, or full weight for every candidate when every load is equal (including all-zero) - `NOT_APPLICABLE` only when the term has no `PUBLISHED` version at all, since there is then no basis for comparison whatsoever. One grouped SQL aggregation query loads every candidate lab's load in a single call, not one query per lab.

**Read-only, stateless, no selection:** `ScoringEngine` runs inside `@Transactional(readOnly = true)`, never writes, never caches between calls, and never selects/persists a "winner" - `rankedCandidates()` is already ordered best-first, but nothing beyond that list exists yet. That decision layer is Phase 12 (Explainable Allocation).

## Explainable Allocation (Phase 12)

```
SchedulingRequest
        ↓
CandidateGenerator.generate(request)               (Phase 10, unmodified)
        ↓
CandidateGenerationResult
        ↓
ScoringEngine.score(generationResult)               (Phase 11, unmodified)
        ↓
ScoringResult
        ↓
ExplainableAllocationService.recommend(request)     (Phase 12 - the only new orchestration)
        ↓
AllocationRecommendation{status, recommendedCandidate, rankedValidCandidates, rejectedCandidates, rejectionSummary, summary}
```

**The first orchestration layer, not a third validation/scoring path:** `ExplainableAllocationService` (`com.college.laballocation.scheduling.explanation`) calls `CandidateGenerator` then `ScoringEngine` exactly once each and transforms their already-produced results - it recomputes no constraint, no score formula, and issues no additional database query beyond what those two already ran. Every `ConstraintResult`/`ConstraintViolation`/`ScoreContribution` surfaced in an `AllocationRecommendation` is the literal Phase 9/11 object (or a thin, non-recomputing display wrapper around it), never a re-derived value.

**Advisory, not a booking:** `AllocationRecommendation` describes "the best candidate according to this snapshot," never "successfully booked." No `Allocation` row is created, no lab is reserved, no schedule is published, no row is locked - `recommend(...)` runs read-only inside one `@Transactional(readOnly = true)` boundary joining the read-only transactions `CandidateGenerator`/`ScoringEngine` already open. The result can become stale the instant the transaction ends (another request could occupy the same lab before any future booking commits) - Phase 16 owns commit-time revalidation, not this phase. Verified live in Docker: the `allocation` table's row count is identical before and after calling `recommend(...)`.

**Terminology - `AllocationRecommendation`, never `AllocationDecision`:** Phase 8 deliberately deferred naming this type until scoring and explanation both existed to give it a real shape. "Decision" was rejected specifically because it implies something was committed; nothing is. See ADR in docs/15-DESIGN-DECISIONS.md.

**Two result shapes, never a shared nullable-score type:** a valid candidate becomes an `ExplainedValidCandidate` (rank, score, applicable max, normalized score, the exact Phase 11 `ScoreContribution` list, plus a display-labeled constraint-check summary) - an invalid candidate becomes a `RejectedCandidateExplanation` (every `ViolationExplanation` it failed, never collapsed to the first reason). `RejectedCandidateExplanation` has **no score field at all**, not even a nullable one - a type that cannot hold a score cannot accidentally display `score = 0` for a hard rejection, which would misleadingly suggest scoring happened.

**PASS/FAIL/NOT_APPLICABLE preserved exactly:** `ConstraintCheckExplanation` wraps one already-computed `ConstraintResult` with a display label - `NOT_APPLICABLE` (HC-11 with no CR actor, or a Lab Assistant actor) is never rendered as "passed"; callers must branch on the real three-way outcome. Reading the request's already-known `actor` (already on `SchedulingRequest`, not re-derived) supplies an accurate, non-fabricated reason string for a `NOT_APPLICABLE` result without re-evaluating HC-11.

**No fake deferred factors:** `AllocationRecommendation` never displays "Faculty Preference: 10/10" or similar - only the three factors actually backed by a registered `AllocationScorer` bean (Phase 11) ever appear in a `ScoreContribution` list, because that list is read verbatim from `ScoredCandidate`.

**Pairwise comparison, not natural-language generation:** `ScoreComparison.compare(a, b)` diffs two `ExplainedValidCandidate`s' contributions factor-by-factor into `ContributionDifference`s - a small, deterministic, reusable helper (PART 17 of the Phase 12 brief), never an LLM/NLG call (PART 37: explainability must be deterministic and derived only from actual algorithm output).

**Rejection aggregation semantics, documented precisely:** `RejectionSummary.countByErrorCode()` counts how many *rejected candidates* carried each error code at least once - one candidate failing both `CAPACITY_VIOLATION` and `SOFTWARE_MISMATCH` increments both counts, so `sum(countByErrorCode.values())` is generally **not** equal to `rejectedCount`. Documented explicitly on the type so a future UI never mistakenly sums per-reason counts and displays that as "labs rejected."

**Zero-valid and single-valid are both normal results:** `status = NO_VALID_CANDIDATE` with `recommendedCandidate = null` when every candidate failed some hard constraint - never an exception, never a search for a different time (that remains Phase 13). A single valid candidate is recommended outright, with the summary noting "only one candidate satisfied all hard constraints" rather than implying a meaningful multi-way comparison occurred.

**"Other valid candidates," never "alternatives":** `AllocationRecommendation.otherValidCandidates()` (a derived view over `rankedValidCandidates`, not a second stored list - same pattern as `CandidateGenerationResult.validCandidates()`) holds every ranked valid candidate below the recommended one, all using the *same requested date/time*. The term "alternative scheduling suggestions" is deliberately reserved for Phase 13, which will search other times and/or labs - a materially different capability this phase does not implement.

## Conflict Analysis + Alternative Suggestions (Phase 13)

```
SchedulingRequest
        ↓
ExplainableAllocationService.recommend(request)          (Phase 12, unmodified)
        ↓
AllocationRecommendation
        ↓
ConflictAnalyzer.analyze(recommendation)                 (pure transformation - no DB, no re-evaluation)
        ↓
ConflictAnalysis{structurallyViableLabIds, temporalFailuresByLabId, conflicts, rejectionSummary}
        ↓
alternativeTimeSearchWorthwhile()?  ── false ──▶ NO_ALTERNATIVE_FOUND (search never attempted)
        │ true
        ↓
SchedulingSlotProvider.generateCandidateSlots(request)    (bounded, deterministic, ordered)
        ↓
for each candidate slot (up to the search bound):
    build a new SchedulingRequest (same subject/faculty/division/batch/actor/term/duration, new date/time)
        ↓
    ExplainableAllocationService.recommend(alternativeRequest)   (the exact same Phase 12 pipeline, no shortcuts)
        ↓
    RECOMMENDED? → keep its top-ranked ExplainedValidCandidate as one AlternativeSuggestion
        ↓
rank collected suggestions, cap to the suggestion bound
        ↓
AlternativeSearchResult{originalRecommendation, conflictAnalysis, suggestions, status, slotsSearched}
```

**No duplicate validation logic, ever** (PART 2 of the Phase 13 brief): `ConflictAnalyzer` reads only `AllocationRecommendation.rejectedCandidates()`/`rejectionSummary()` - both already-computed Phase 12 objects - and never queries a repository or calls `ConstraintEngine` a second time. Every alternative slot's validity is decided by one more real call to `ExplainableAllocationService.recommend(...)`, which itself calls the unmodified Phase 10/11 pipeline - `AlternativeSuggestionService` never itself contains an `if (labOccupied)`-style check. There remains exactly one source of truth for validity across all thirteen phases.

**Acyclic dependency, not recursion** (PART 31/32): `AlternativeSuggestionService` depends on `ExplainableAllocationService`; the reverse is never true - `ExplainableAllocationService` has no knowledge `AlternativeSuggestionService` exists. No refactor of Phase 12 was needed to achieve this; the dependency graph was already a clean layered stack.

**Structural vs. temporal conflict classification** (PART 4/5): every existing `ConstraintViolation.errorCode()` (Phase 9, unchanged) is classified by `ConflictClassification.categoryOf(...)` - `TEMPORAL` (`LAB_CONFLICT`, `FACULTY_CONFLICT`, `FACULTY_UNAVAILABLE`, `BATCH_CONFLICT`, `DIVISION_CONFLICT`, `LAB_UNAVAILABLE`) can plausibly be solved by changing the time; `STRUCTURAL` (`CAPACITY_VIOLATION`, `SOFTWARE_MISMATCH`, `EQUIPMENT_MISMATCH`, `LAB_TYPE_MISMATCH`, `INVALID_ACADEMIC_RELATIONSHIP`, `FORBIDDEN_DIVISION_ACCESS`, `CR_ASSIGNMENT_NOT_FOUND`) is true at every time of day, so changing the time can never fix it. No new error codes were introduced - this is purely a categorization of Phase 9's existing thirteen.

**Structural viability - the whole search decision in one boolean** (PART 35/36): a rejected candidate is "structurally viable" iff *none* of its violations are `STRUCTURAL` - even if it fails one or more `TEMPORAL` constraints. `ConflictAnalysis.alternativeTimeSearchWorthwhile()` is simply "at least one candidate is structurally viable." This correctly handles the brief's required mixed case: if 12 labs fail `SOFTWARE_MISMATCH` and 3 Cloudera-capable labs fail only `FACULTY_UNAVAILABLE`, those 3 are structurally viable, so search proceeds - verified live (see docs/11-TESTING-STRATEGY.md).

**Same-time-different-lab needs no new search logic at all** (PART 7/8/38): Phase 10's `CandidateGenerator` already evaluates *every* lab at the exact requested time. If any lab were valid there, `ExplainableAllocationService.recommend(...)` would already return `RECOMMENDED`, and `AlternativeSuggestionService` returns `ALTERNATIVES_NOT_NEEDED` immediately without ever calling `SchedulingSlotProvider` - "same time, different lab" is not a separate capability Phase 13 had to build; it already existed by construction. This is also why `AlternativeType` has no `SAME_TIME_DIFFERENT_LAB` constant - it can never actually be produced by this architecture, and PART 14 explicitly prohibits enum values for unreachable behavior.

**Slot policy - collected from the user, not invented** (PART 75/76): no authoritative college scheduling-slot rules (working days, daily hours, session duration, break windows) existed anywhere in this repository before this phase. Rather than guess, the missing rules were requested directly and are now centralized in `SchedulingSlotPolicy` (`app.scheduling.*`): fixed 2-hour sessions starting on the hour, 09:00-19:00 (valid start hours 09-17), Monday-Saturday, up to 3 lookahead days, 6 slots searched, 3 suggestions returned. See docs/ASSUMPTIONS.md A-35 and ADR-056.

**Ordering - lexicographic, never one merged score** (PART 13/29): `AlternativeSuggestionService`'s ranking comparator is, in order: (1) day displacement ascending, (2) time-of-day displacement from the original request ascending, (3) the exact Phase 11 normalized score descending, (4) lab code ascending as the final deterministic tie-break. A closer time with a lower score can and does outrank a farther time with a higher score - verified live and by a dedicated unit test.

**Search bounds - exact, configured, reported** (PART 28/30): at most 6 (day, time) slot combinations are ever run through the full generate→score→explain pipeline per search (`SchedulingSlotProvider` truncates deterministically, closest-first); at most 3 suggestions are ever returned. `AlternativeSearchResult.slotsSearched()` reports the real number actually attempted, for transparency. Complexity: `O(min(H*D, maxSlotsSearched) * labsPerRequest)` full pipeline runs, where `H` (9) is valid start hours/day and `D` (4) is candidate days - a small, bounded multiple of Phase 10/11's own already-documented per-request cost, never a combinatorial search.

**Determinism** (PART 29): `SchedulingSlotProvider` produces a plain, explicitly-sorted `List`, never iterates a `HashSet`/`HashMap` or relies on incidental database row order - identical input and database state always produce an identical alternative ordering.

**Advisory, never a reservation** (PART 79): exactly like Phase 12's `AllocationRecommendation`, an `AlternativeSuggestion` describes a snapshot - no `Allocation` row is created, nothing is locked, and nothing is guaranteed to remain available. Verified live: the `allocation` table's row count is identical before and after every `findAlternatives(...)` call. Phase 16 owns commit-time revalidation.

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
| `CandidateAllocation` | **Implemented (Phase 8 shape, populated Phase 9, generated Phase 10)** | `(SchedulingContext, LabRef)` - `LabRef` (Phase 9) replaced the Phase 8 bare `labId`/`labCode` with a full candidate-specific snapshot (capacity, lab type, installed software/equipment, existing lab allocations, unavailability windows), built once per candidate by `CandidateAllocationFactory` so HC-01/06/07/08/09/10 never independently re-query the same lab. `CandidateGenerator` (Phase 10) is what actually builds one per lab, for every lab in the system. Never persisted, never scored. |
| `ConstraintResult` | **Implemented (Phase 8 shape, extended Phase 9)** | `(HardConstraintId, ConstraintOutcome, ConstraintViolation?)` from one `SchedulingConstraint` - `ConstraintOutcome` (Phase 9: PASS/FAIL/NOT_APPLICABLE) replaced Phase 8's plain `boolean passed`, specifically for HC-11's applicability semantics. Self-validates that a FAIL result carries a violation and a PASS/NOT_APPLICABLE result never does. |
| `ConstraintViolation` | **Implemented (Phase 8), populated for real (Phase 9)** | `errorCode`, `message`, `affectedResourceType`, `affectedResourceId`, `details` — maps directly to the API error model, deliberately not an untyped `Map`-only shape. |
| `HardConstraintId` | **Implemented (Phase 8)** | Stable enum (`HC_01_LAB_CONFLICT` .. `HC_12_ACADEMIC_RELATIONSHIP`) identifying *which* constraint produced a `ConstraintResult` — kept separate from the wire-level API error codes in `ConstraintViolation`. |
| `ConstraintEngine` / `ConstraintEvaluation` / `SchedulingConstraint` | **Implemented (Phase 9)** | The engine itself, its aggregate result type, and the interface every HC-01..HC-12 class implements. See "The Constraint Engine" section above. |
| `CandidateGenerator` / `EvaluatedCandidate` / `CandidateGenerationResult` | **Implemented (Phase 10)** | `CandidateGenerator.generate(request)` (a `@Service`, not a pure record - it orchestrates real repository/factory/engine calls) returns a `CandidateGenerationResult`, which holds every `EvaluatedCandidate{candidate, constraintEvaluation}` for the request, valid and invalid alike. See "Candidate Generation" section above. |
| `ScoringEngine` / `AllocationScorer` / `ScoreContribution` / `ScoredCandidate` / `ScoringResult` | **Implemented (Phase 11)** | `ScoringEngine.score(generationResult)` (a `@Service`) ranks only the valid candidates using Spring-discovered `AllocationScorer` beans (`CapacityFitScorer`, `PreferredLabTypeScorer`, `BalancedUtilizationScorer`); each returns a `ScoreContribution` (points/max/explanation/details) per candidate, summed into a `ScoredCandidate`, ranked into a `ScoringResult`. See "Scoring Engine" section above. |
| `ExplainableAllocationService` / `AllocationRecommendation` / `ExplainedValidCandidate` / `RejectedCandidateExplanation` / `RejectionSummary` | **Implemented (Phase 12)** | `ExplainableAllocationService.recommend(request)` (a `@Service`) orchestrates `CandidateGenerator` + `ScoringEngine`, then converts the results into one structured `AllocationRecommendation` — advisory only, named deliberately not `AllocationDecision`. See "Explainable Allocation" section above. |
| `ConflictAnalyzer` / `ConflictAnalysis` / `ConflictDetail` | **Implemented (Phase 13)** | Pure transformation of an already-computed `AllocationRecommendation` into structural-vs-temporal classified conflict data - never queries a repository or re-evaluates a constraint. See "Conflict Analysis + Alternative Suggestions" section above. |
| `AlternativeSuggestionService` / `AlternativeSuggestion` / `AlternativeSearchResult` / `SchedulingSlotProvider` / `SchedulingSlotPolicy` | **Implemented (Phase 13)** | The Phase 13 fallback-suggestion layer - a ranked fallback suggestion (different lab and/or different time) when the originally requested slot fails. Distinct from Phase 12's `otherValidCandidates()`, which uses only the same requested time. |
| `SchedulingMetrics` | Not yet implemented | Counters: candidates evaluated, constraints checked, backtrack count, execution time — for [16-PERFORMANCE-BENCHMARKS.md](16-PERFORMANCE-BENCHMARKS.md), Phase 14/25. |

`constraint`, `scoring`, `conflict`, and the core scheduler operate **only** on these objects — never on JPA entities or DTOs directly (verified: none of `SchedulingRequest`/`SchedulingContext`/`CandidateAllocation`/`ConstraintResult`/`ConstraintViolation`/`ConstraintEvaluation` carries a single JPA annotation). Application services in the `scheduling` package are the translation boundary (load entities → build `SchedulingContext` via `SchedulingContextFactory` → build `CandidateAllocation` via `CandidateAllocationFactory` → `ConstraintEngine` validates → Phase 15/19 will persist an `Allocation`).

**Faculty resolution happens before a `SchedulingRequest` exists (PART 15 of the Phase 8 brief):** `facultyId` is never resolved *by* the request — the preferred architecture is `external scheduling input → resolve academic/faculty context (FacultyAssignmentResolutionService, Phase 4) → SchedulingRequest`, so the future constraint engine always receives an unambiguous request and never itself has to decide "which faculty teaches this."

## Single-Request Validation Pipeline (used by both extra-lab booking and PDF-import validation)

```
SchedulingRequest
      ↓
Resolve SchedulingContext (load relevant allocations, availability, inventory)     [implemented, Phase 8]
      ↓
Generate Candidate Labs - every lab, no prefiltering    [CandidateGenerator - implemented, Phase 10]
      ↓
Validate Hard Constraints (HC-01..HC-12) against each candidate    [ConstraintEngine - implemented, Phase 9]
      ↓
CandidateGenerationResult retains BOTH valid and invalid candidates    [implemented, Phase 10]
      ↓
Score Remaining (Valid) Candidates Only (07-ALLOCATION-SCORING.md)    [ScoringEngine - implemented, Phase 11]
      ↓
Rank Candidates (normalized score descending, lab.code ascending tie-break)    [implemented, Phase 11]
      ↓
Build AllocationRecommendation: recommended candidate + ranked valid alternatives + explained rejections    [ExplainableAllocationService - implemented, Phase 12]
      ↓
(advisory only - no persistence, no reservation; Phase 16 revalidates at commit time)
```

**Correction to the original Phase 1 sketch:** the line above originally read "Generate Candidate Labs (active + capacity ≥ required + required software/equipment/type present)" - i.e. prefiltering by the same conditions the hard constraints check. Phase 10 deliberately did **not** implement it that way (PART 9/10/24 of the Phase 10 brief): prefiltering by capacity/software/type would duplicate HC-07/08/10's own logic in a second place, risking Phase 9 and Phase 10 silently disagreeing, and would make a rejected lab's reason unrecoverable (a prefiltered-out lab never becomes a candidate at all, so there is no `ConstraintViolation` to show a CR later explaining "why isn't C-304 in the list"). `CandidateGenerator` generates from every lab and lets the real `ConstraintEngine` be the sole source of truth for validity - see "Candidate Generation" above and ADR in docs/15-DESIGN-DECISIONS.md. "Remove Invalid Candidates" (the original sketch's next step) is also not a separate step in the real implementation - `CandidateGenerationResult` keeps both `validCandidates()` and `invalidCandidates()` as filtered views, never physically discarding the rejected ones, since Phase 12/13 need them.

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
