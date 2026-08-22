# Allocation Scoring (Soft Constraints)

Scoring only ever ranks candidates that have **already passed every hard constraint** in [06-CONSTRAINTS.md](06-CONSTRAINTS.md). A candidate that fails a hard constraint is never scored — it is removed before scoring runs. This separation is the single most important rule in this document: **hard constraints gate eligibility; scoring only orders what's already eligible.**

**Status (Phase 6):** the data this document's factors will read now exists and is independently verified — `subject.required_lab_type_id` (hard, HC-10) and `subject.preferred_lab_type_id` (soft, this document) are both real, distinct, mutually-exclusive columns (enforced by application code and a database CHECK constraint — see docs/04-DATABASE-DESIGN.md §5), so the "Preferred Lab Type" factor below is no longer just a design placeholder; it has a real field to read once the scoring engine itself is built (Phase 11). No scoring *code* exists yet — this remains a design document until then.

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

| Factor | Weight | What it measures |
|---|---:|---|
| **Capacity Fit** | 30 | How closely lab capacity matches required strength, without wasting an oversized lab |
| **Additional Environment Fit** | 20 | Non-required but subject-relevant extra software/equipment present (0 if none defined for the subject — never re-checks required software) |
| **Preferred Lab Type** | 15 | Whether the lab type matches a subject's *soft* type preference distinct from `required_lab_type_id` (see note below) — if the subject has a hard `required_lab_type_id`, this factor also degrades to a constant (every remaining candidate already matches it via HC-10), so in the common case this factor differentiates only when a subject has a *preferred-but-not-required* type, a concept added specifically so this scorer isn't redundant with HC-10 |
| **Balanced Utilization** | 15 | Rewards less-utilized labs to spread load across the ~15-lab pool |
| **Faculty Preference** | 10 | If a faculty has a recorded preferred lab (future-extensible field, optional), reward matching it |
| **Fewer Timetable Gaps** | 10 | Rewards a lab/time choice that minimizes idle gaps in the faculty's or batch's existing daily schedule |

Total: 100. Weights live in application configuration (e.g. `application.yml` under a `scoring:` section or a `scoring_weight_config` table if runtime-tunability by the Lab Assistant is wanted later), never as scattered magic numbers in scorer classes.

### Capacity Fit — exact formula

Given `required = strength`, `capacity = lab.capacity` (already guaranteed `capacity >= required` by HC-07):

```
fit = 1 - (capacity - required) / capacity
score = round(30 * fit)
```

This rewards the *closest-fit* lab (per PART 26's explicit example: batch of 64 → Lab A(65) should outrank Lab C(150)), never the largest.

### Balanced Utilization — exact formula

```
utilization(lab) = allocatedMinutes(lab, lookbackWindow) / availableMinutes(lab, lookbackWindow)
score = round(15 * (1 - utilization(lab)))
```

`availableMinutes` is derived from the institution's operating hours (e.g. 09:00–17:00, 6 working days) over a configurable lookback window (default: current term-to-date). Lower utilization → higher score, spreading load rather than always picking the same convenient lab.

### Fewer Timetable Gaps — exact formula

```
gap(candidate) = minutes between the end of the batch's/faculty's nearest earlier session that day and the candidate start,
                  plus minutes between candidate end and the start of the nearest later session that day
score = round(10 * (1 - normalizedGap))   where normalizedGap is clamped to [0,1] against a configurable max-relevant-gap (default 120 min)
```

## Explainability Output

Every scored candidate returns a structured breakdown, not just a number:

```json
{
  "labId": 304,
  "totalScore": 92,
  "breakdown": [
    { "factor": "CAPACITY_FIT", "score": 27, "max": 30, "explanation": "Capacity 72, required 68 — tight fit" },
    { "factor": "ADDITIONAL_ENVIRONMENT_FIT", "score": 20, "max": 20, "explanation": "Cloudera required and present; Python also available" },
    { "factor": "BALANCED_UTILIZATION", "score": 13, "max": 15, "explanation": "Utilization 24% this term, below average" }
  ]
}
```

Rejected candidates carry the hard-constraint failure reason instead (see [06-CONSTRAINTS.md](06-CONSTRAINTS.md) and [10-API-DOCUMENTATION.md](10-API-DOCUMENTATION.md#error-model)) — they are never scored, so they never appear with a partial score.

## Hard/Soft Separation Checklist (self-audit for this document)

- [x] No hard constraint (HC-01..HC-12) has a corresponding score penalty anywhere in this table.
- [x] "Software Match" was renamed/redefined specifically to avoid double-counting HC-08.
- [x] "Preferred Lab Type" was scoped to a distinct *soft* preference field so it doesn't duplicate HC-10.
- [x] Every factor here is documented as contributing 0 (not a penalty, not a rejection) when its underlying preference data doesn't exist for a given subject/faculty.
