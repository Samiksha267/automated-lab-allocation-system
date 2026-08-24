# Allocation Scoring (Soft Constraints)

Scoring only ever ranks candidates that have **already passed every hard constraint** in [06-CONSTRAINTS.md](06-CONSTRAINTS.md). A candidate that fails a hard constraint is never scored — it is removed before scoring runs. This separation is the single most important rule in this document: **hard constraints gate eligibility; scoring only orders what's already eligible.**

**Status (Phase 11):** A real, tested `ScoringEngine` now exists (`com.college.laballocation.scheduling.scoring`). Before writing any scorer, Phase 11 re-inspected every factor below against the actual repository (not this document's aspirations) and produced the readiness matrix in the next section. Three factors turned out to be genuinely implementable with real data — **Capacity Fit**, **Preferred Lab Type**, **Balanced Utilization** — and are implemented, tested (unit + environment-blocked IT + manual Docker), and documented below with their *actual* formulas. Three were deferred because no underlying data exists anywhere in the schema - **Additional Environment Fit**, **Faculty Preference**, **Fewer Timetable Gaps** - and no table/column was invented to manufacture them (see docs/15-DESIGN-DECISIONS.md ADR-049/050/051).

## Readiness Matrix (Phase 11)

| Factor | Original Weight | Readiness | Implemented? | Reason |
|---|---:|---|---|---|
| Capacity Fit | 30 | READY | ✅ `CapacityFitScorer` | `lab.capacity` and the request's target strength (`batch.strength`/`division.strength`) are both real, already-validated fields. |
| Additional Environment Fit | 20 | NOT_READY | ❌ deferred | No "preferred/recommended software" concept exists anywhere in the schema - `SubjectSoftwareRequirement`/`SubjectEquipmentRequirement` are all-required, all-or-nothing joins. Awarding points for a lab simply having *more* installed software (unrelated to the subject) would be meaningless, not a real quality signal - explicitly prohibited (PART 15/16 of the Phase 11 brief). |
| Preferred Lab Type | 15 | READY | ✅ `PreferredLabTypeScorer` | `subject.preferredLabTypeId` is a real, distinct column from `subject.requiredLabTypeId` (HC-10), enforced mutually exclusive since Phase 6. |
| Balanced Utilization | 15 | READY (relative, not a percentage) | ✅ `BalancedUtilizationScorer` | Real `Allocation` rows exist and can be summed per lab. No working-days/daily-operating-hours concept exists to compute a true utilization *percentage* against, so the factor compares candidate labs' scheduled minutes *to each other* (min-max normalized), never claims an absolute utilization figure. |
| Faculty Preference | 10 | NOT_READY | ❌ deferred | Only `FacultyAvailability` (allowed windows) exists - there is no persisted `FacultyPreference`/preferred-lab concept. Treating availability as preference would be a fabricated proxy, explicitly prohibited (PART 26 of the Phase 11 brief). |
| Fewer Timetable Gaps | 10 | NOT_READY / NOT_APPLICABLE at this architecture stage | ❌ deferred | Every candidate inside one `CandidateGenerationResult` shares the exact same `date`/`startTime`/`endTime` (Phase 10) - only the lab varies. Changing the lab cannot change a timetable gap, so this factor is structurally meaningless until Phase 13/14 introduces alternative time slots. |

**Effective enabled weight today: 60** (30 + 15 + 15), not 100 - `ScoringEngine` never assumes a fixed denominator; see "Score Semantics" below.

## Why the original starting weights needed critical revision

The originally proposed starting point was:

```
Capacity Fit          30
Software Match        20
Preferred Lab Type    15
Balanced Utilization  15
Faculty Preference    10
Fewer Gaps            10
```

**Problem identified:** "Software Match" as a *scoring* factor is ambiguous once required software (HC-08) is already a hard constraint — every candidate reaching the scorer already has 100% of the subject's *required* software, by construction (HC-08 rejected anyone missing it). Scoring "software match" again as if partial/absent required software were tolerable would double-count a hard constraint as if it were soft, and would give a meaningless score contribution (everyone ties at maximum, since it's not discriminating between valid candidates on anything real).

**Resolution:** redefine this factor as **Additional Environment Fit** — it rewards a lab that has *extra, non-required-but-useful* software/tooling relevant to the subject (e.g., a lab with both Cloudera and a compatible Python/Jupyter setup, when the subject only strictly requires Cloudera), rather than re-testing the requirement that HC-08 already guarantees. If a subject has no notion of "nice-to-have" software beyond its hard requirements (the common case for most subjects in the initial dataset), this factor simply contributes 0 for every candidate — it does not need to be removed, it degrades gracefully to a no-op.

## Finalized Scoring Model

| Factor | Weight | Status | What it measures |
|---|---:|---|---|
| **Capacity Fit** | 30 | ✅ Implemented | How closely lab capacity matches required strength, without wasting an oversized lab |
| **Additional Environment Fit** | 20 | ❌ Deferred (no data) | Non-required but subject-relevant extra software/equipment present — deferred, no "preferred software" concept exists |
| **Preferred Lab Type** | 15 | ✅ Implemented | Whether the lab type matches a subject's *soft* type preference distinct from `required_lab_type_id` — `NOT_APPLICABLE` (excluded from the applicable max) when the subject records no preference at all |
| **Balanced Utilization** | 15 | ✅ Implemented (relative) | Rewards a less-scheduled lab relative to the other candidate labs this run — never an absolute percentage |
| **Faculty Preference** | 10 | ❌ Deferred (no data) | Only `FacultyAvailability` (allowed windows) exists — no persisted lab/time preference |
| **Fewer Timetable Gaps** | 10 | ❌ Deferred (not applicable yet) | Structurally meaningless while every candidate shares one fixed date/time — becomes real once Phase 13/14 offers alternative slots |

**Enabled weight today: 60** (30 + 15 + 15) — not 100. Weights live in application configuration (`application.yml`'s `app.scoring.*`, injected via `ScoringConfiguration`, following this project's existing constructor-`@Value` convention rather than `@ConfigurationProperties`), never as scattered magic numbers in scorer classes. Only the three implemented factors have a configured weight — no weight was manufactured for a deferred factor.

### Capacity Fit — actual implemented formula (`CapacityFitScorer`)

Given `required` = the request's target strength (`batch.strength` for a `BATCH` request, `division.strength` for `DIVISION` - the same target HC-07 already validated `capacity >= required` against) and `capacity = lab.capacity`:

```
fitRatio = required / capacity
score = capacityFitWeight * fitRatio
```

Since every valid candidate satisfies `capacity >= required > 0`, `fitRatio` is always in `(0, 1]`, so `0 < score <= weight` - no rounding-to-zero edge case, no division by zero. This rewards the *closest-fit* lab, never the largest: required 68, capacity 70 scores far higher than required 68, capacity 150.

### Preferred Lab Type — actual implemented formula (`PreferredLabTypeScorer`)

```
if subject.preferredLabTypeId == null:
    NOT_APPLICABLE (0 of 0 - excluded from the applicable maximum entirely)
elif candidate.lab.labTypeId == subject.preferredLabTypeId:
    score = preferredLabTypeWeight   (full credit)
else:
    score = 0   (still APPLIED - counted toward the applicable maximum, candidate remains valid)
```

### Balanced Utilization — actual implemented formula (`BalancedUtilizationScorer`, backed by `LabUtilizationService`)

`LabUtilizationService` sums each candidate lab's scheduled minutes (`REGULAR` and `EXTRA` allocations alike, `AllocationStatus.blocksScheduling()` statuses only) within the requesting term's currently `PUBLISHED` `ScheduleVersion` - one grouped SQL query for the whole candidate set, not one query per lab. If the term has no `PUBLISHED` version at all, the factor is `NOT_APPLICABLE` for every candidate (no basis for comparison). Otherwise, min-max normalized across the candidate set actually being scored this run:

```
minLoad = min(scheduledMinutes(lab) for lab in candidateLabs)
maxLoad = max(scheduledMinutes(lab) for lab in candidateLabs)

if maxLoad == minLoad:
    score = balancedUtilizationWeight   (every candidate equally loaded - full credit for all, never a divide-by-zero)
else:
    score = balancedUtilizationWeight * (maxLoad - candidateLoad) / (maxLoad - minLoad)
```

This was chosen over a simpler "ratio against the single most-loaded lab" formula specifically because that alternative forces the most-loaded candidate to exactly zero regardless of how close the rest of the field is - min-max normalization stays bounded `[0, weight]` and degrades gracefully when every candidate is equally (or zero) loaded. Deliberately a *relative* comparison among candidate labs, never an absolute utilization percentage - no working-days/daily-operating-hours concept exists anywhere in this project to compute one against.

### Additional Environment Fit / Faculty Preference / Fewer Timetable Gaps — deferred, no formula implemented

No scorer bean exists for these three (see the Readiness Matrix above) - registering a fake scorer that always returns a constant, or fabricating a formula against data that doesn't exist, was explicitly prohibited (PART 45/16/26 of the Phase 11 brief). Their `ScoringFactorId` enum constants remain reserved for whichever future phase gives them real, non-fabricated data to read.

## Consumed by `ExplainableAllocationService` (Phase 12), never recomputed

`ScoredCandidate.contributions()` (the exact `List<ScoreContribution>` this document describes above) is read verbatim into `ExplainedValidCandidate.scoreContributions()` (`com.college.laballocation.scheduling.explanation`) - no formula on this page is re-executed, no utilization query re-run, no capacity ratio recalculated. The explanation layer adds exactly one thing on top: a short display label per `ScoringFactorId` (`ScoringFactorLabels`, e.g. `PREFERRED_LAB_TYPE` → "Preferred lab type") for presentation, while the raw enum and every numeric field are preserved unchanged. A pairwise `ScoreComparison.compare(a, b)` helper diffs two candidates' contributions factor-by-factor (structured, deterministic - never natural-language generation) to answer "why did the winner outrank the runner-up," using only these same already-computed numbers.

## Consumed by `AutomaticSchedulingEngine` (Phase 14) for choice ordering, never recomputed

Phase 14's `computeChoices(requirement, slots, state)` calls `ExplainableAllocationService.recommend(request, searchState)` once per candidate slot - the exact same call Phase 12 already makes, just carrying the search's provisional occupancy. Every valid lab's `ExplainedValidCandidate.normalizedScore()` (this document's formulas, completely unmodified) becomes one `SchedulingChoice`, and choices are ordered score descending (then date, then time, then lab code) before the backtracking search tries them. No score is recalculated for search purposes - a lab's score is identical whether it's being shown in a Phase 12 recommendation or being tried as a Phase 14 backtracking choice.

**Balanced Utilization sees only persisted utilization, not this search's own provisional load - a deliberate, documented heuristic simplification.** `LabUtilizationService` (Phase 11) queries `PUBLISHED`-schedule-version `Allocation` rows directly from PostgreSQL; it has no `SchedulingSearchState` parameter and was not extended to accept one. This means if a Phase 14 search provisionally assigns several sessions to the same lab, Balanced Utilization does not "notice" that growing load when scoring the next requirement's candidates during the same search - it only ever reflects what's actually published in the database. This was a deliberate choice (the phase brief's PART 22 explicitly permits it): the hard-conflict constraints (HC-01/02/04/05, extended to see provisional occupancy) are what actually prevent double-booking within one search, which matters far more than a soft-scoring factor's relative freshness. Extending `LabUtilizationService` to also merge provisional load was judged unnecessary complexity for a factor whose entire purpose is "prefer the less-busy lab, all else equal," not correctness.

## Explainability Output — actual `ScoredCandidate` shape (Phase 11)

Every scored candidate carries a structured breakdown, not just a number - this is `ScoredCandidate`/`ScoreContribution` as actually implemented, a real BDA scenario observed live in Docker (2026-08-23; Division A strength 68, Batch A1 strength 23, subject BDA prefers `DATA_ENGINEERING`):

```json
{
  "labCode": "C-202",
  "totalScore": 39.58,
  "maxPossibleScore": 60.0,
  "normalizedScore": 0.6597,
  "contributions": [
    { "factor": "CAPACITY_FIT", "applicability": "APPLIED", "pointsAwarded": 9.58, "maxPoints": 30.0, "explanation": "Lab capacity 72 for required capacity 23 (fit ratio 0.3194)." },
    { "factor": "PREFERRED_LAB_TYPE", "applicability": "APPLIED", "pointsAwarded": 15.0, "maxPoints": 15.0, "explanation": "Lab type (DATA_ENGINEERING) matches the subject's preferred lab type." },
    { "factor": "BALANCED_UTILIZATION", "applicability": "APPLIED", "pointsAwarded": 15.0, "maxPoints": 15.0, "explanation": "All candidate labs are equally scheduled (0 min); full credit." }
  ]
}
```

`ADDITIONAL_ENVIRONMENT_FIT`/`FACULTY_PREFERENCE`/`TIMETABLE_GAP` never appear in `contributions` at all - there is no `NOT_APPLICABLE` placeholder entry for a factor with no registered scorer bean; only `PREFERRED_LAB_TYPE` can be `NOT_APPLICABLE`, and only when a specific subject records no preference.

Rejected (invalid) candidates are never wrapped in a `ScoredCandidate` at all - they carry only their hard-constraint failure reason from `CandidateGenerationResult.invalidCandidates()` (see [06-CONSTRAINTS.md](06-CONSTRAINTS.md)); `ScoringEngine` never touches them.

## Hard/Soft Separation Checklist (self-audit for this document)

- [x] No hard constraint (HC-01..HC-12) has a corresponding score penalty anywhere in this table.
- [x] "Software Match" was renamed/redefined specifically to avoid double-counting HC-08 — and remains deferred (Additional Environment Fit) since no real data exists to back it.
- [x] "Preferred Lab Type" was scoped to a distinct *soft* preference field so it doesn't duplicate HC-10.
- [x] Every implemented factor is documented as contributing 0 (not a penalty, not a rejection) when its underlying preference data doesn't exist for a given subject/faculty — and excluded from the applicable maximum entirely via `NOT_APPLICABLE`, not a fabricated score.
- [x] `ScoringEngine.score(...)` reads `CandidateGenerationResult.validCandidates()` only — verified live in Docker with an adversarial invalid-but-soft-favorable candidate that never appears in the ranking (Phase 11 completion report, "Hard-vs-Soft Scenario").
