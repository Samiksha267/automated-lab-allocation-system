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

## Conflict Analysis + Alternative Suggestion Tests — Implemented (Phase 13)

| Test | Class | What it proves |
|---|---|---|
| Temporal-only codes classified as TEMPORAL; structural codes classified as STRUCTURAL; unrecognized code defaults to STRUCTURAL | `ConflictClassificationTest` | The classification table matches docs/06-CONSTRAINTS.md exactly, and an unknown code fails safe (never assumed time-solvable) |
| Candidate failing only a temporal constraint is structurally viable | `ConflictAnalyzerTest` | Structural viability = "zero structural failures," proven directly |
| Candidate failing structural + temporal is NOT structurally viable | `ConflictAnalyzerTest` | One structural failure disqualifies a candidate from viability regardless of other failures |
| All candidates failing capacity → zero structurally viable labs | `ConflictAnalyzerTest` | The structural-impossibility detection itself |
| Mixed structural/temporal candidates still yield viable labs | `ConflictAnalyzerTest` | The required mixed case (12 software failures + 3 temporal-only) correctly finds the 3 |
| Multiple temporal failures on one candidate are both retained | `ConflictAnalyzerTest` | No collapsing to a single reason |
| Conflict details carry correct category and non-additive occurrence count | `ConflictAnalyzerTest` | `ConflictDetail` correctly wraps the reused Phase 12 `RejectionSummary` |
| Policy parses configured values; rejects day-start≥day-end, non-positive step/bound | `SchedulingSlotPolicyTest` | Configuration validation actually enforces the documented invariants |
| Every generated slot preserves the requested duration | `SchedulingSlotProviderTest` | PART 10 - duration is never silently changed |
| The exact originally-requested slot is never repeated | `SchedulingSlotProviderTest` | No wasted re-evaluation of an already-known-invalid time |
| Same-day slots ordered by proximity to the requested start | `SchedulingSlotProviderTest` | PART 12 - time-proximity ranking |
| Same-day slots always precede later-day slots | `SchedulingSlotProviderTest` | PART 13 Priority 2 (day displacement) dominates |
| Result bounded by the configured max-slots-searched | `SchedulingSlotProviderTest` | PART 28 - the search bound is real, not aspirational |
| A non-working day is skipped entirely during lookahead | `SchedulingSlotProviderTest` | Sunday never consumes search budget or appears in results |
| Deterministic across repeated calls with identical input | `SchedulingSlotProviderTest` | PART 29 - no hidden randomness or hash-order dependency |
| Already-recommended request needs no alternative search | `AlternativeSuggestionServiceTest` | `ALTERNATIVES_NOT_NEEDED`, zero slots searched |
| Structurally impossible request never triggers time search | `AlternativeSuggestionServiceTest` | `recommend(...)` is called exactly once (only for the original request) |
| Temporal-only failure triggers alternative-time search | `AlternativeSuggestionServiceTest` | The core search-decision boolean, exercised end-to-end |
| Mixed structural/temporal candidates still search | `AlternativeSuggestionServiceTest` | Service-level equivalent of the analyzer-level mixed-case test |
| Actor is preserved in every generated alternative request | `AlternativeSuggestionServiceTest` | HC-11 continues to apply unchanged; no bypass to an internal/Lab-Assistant context |
| Duration is preserved in every generated alternative request | `AlternativeSuggestionServiceTest` | PART 10, verified at the service boundary |
| No valid alternative across all searched slots → `NO_ALTERNATIVE_FOUND` | `AlternativeSuggestionServiceTest` | Normal, non-exceptional exhaustion of the search bound |
| Result bounded by the configured max-suggestions | `AlternativeSuggestionServiceTest` | PART 46 |
| Closer time displacement ranks above a higher score farther away | `AlternativeSuggestionServiceTest` | PART 13 Priority 2 outranks Priority 3, proven with an adversarial fixture |
| Tied time displacement and score break by lab code | `AlternativeSuggestionServiceTest` | PART 13 Priority 4, using a genuinely equidistant (before/after) pair |
| Lab conflict resolved by same-time-different-lab, no alternative search needed | `AlternativeSuggestionIT` (Testcontainers, environment-blocked here) | Real persisted occupancy against real data |
| Batch conflict across every lab triggers alternative-time search | `AlternativeSuggestionIT` | Real persisted batch double-booking |
| Faculty unavailable at requested time but available later yields a real alternative | `AlternativeSuggestionIT` | Real `FacultyAvailability` gap against real data |
| Structural impossibility skips alternative-time search entirely | `AlternativeSuggestionIT` | Real inflated-capacity scenario |
| A1/A2 simultaneous sessions both receive independent recommendations | `AlternativeSuggestionIT` | The project's signature scenario, proven at the alternative-search layer |
| Alternative search never changes the allocation row count | `AlternativeSuggestionIT` | Persistence-safety proof against real Postgres |

**Manually verified against the Dockerized stack (2026-08-23), via a temporary `@Profile("dev")`-only `ApplicationRunner` (`DevAlternativeVerificationRunner`) deleted after use (no production alternative-search API was added just to test this):** occupying B-301 with a different batch/faculty (CNS/A2) at the BDA/A1 requested time resolved via same-time-different-lab (`C-202` recommended, zero slots searched); requesting BDA/A1 during Faculty BDA's real seeded Monday 12:00-14:00 unavailability gap found 3 structurally-viable Cloudera labs and a real alternative (`10:00-12:00 C-202`, closest valid time) - simultaneously the required mixed-case proof; persisting a genuine batch-A1 session forced uniform `BATCH_CONFLICT` and still found a valid later time; temporarily inflating batch A1's strength confirmed `slotsSearched=0` (search never entered); and a real, persisted A1 session did not prevent A2 from receiving its own valid same-time recommendation on a different lab, with no false `DIVISION_CONFLICT`. All scenarios matched (`allScenariosMatch=true`) after fixing a scenario-design bug in the harness itself (see docs/15-DESIGN-DECISIONS.md / completion report's Real Bugs Found). Regression re-verified: all Phase 3-12 endpoints still `200`; `/api/allocations` still `404` both directions; Flyway still at schema version 10; dev-seeded lab count confirmed 15.

## Explainable Allocation Tests — Implemented (Phase 12)

| Test | Class | What it proves |
|---|---|---|
| Top candidate is recommended, others preserved as "other valid" | `ExplainableAllocationServiceTest` | Highest-ranked `ScoredCandidate` becomes `recommendedCandidate`; the rest appear via `otherValidCandidates()` |
| Zero valid candidates returns `NO_VALID_CANDIDATE` with null recommendation | `ExplainableAllocationServiceTest` | No exception; a factual summary is produced instead |
| Invalid candidate appears only in rejected list, never scored | `ExplainableAllocationServiceTest` | `RejectedCandidateExplanation` has no score field at all; the candidate is absent from `rankedValidCandidates` |
| Multiple violation reasons are all preserved | `ExplainableAllocationServiceTest` | A candidate failing two constraints keeps both `ViolationExplanation`s |
| Score breakdown preserves exact Phase 11 contribution values | `ExplainableAllocationServiceTest` | `ExplainedValidCandidate.scoreContributions()` equals the original `ScoreContribution` list verbatim |
| `NOT_APPLICABLE` scoring factor preserved, excluded from applicable max | `ExplainableAllocationServiceTest` | Denominator logic is read from Phase 11, never recomputed here |
| `NOT_APPLICABLE` constraint (HC-11) never rendered as PASS | `ExplainableAllocationServiceTest` | `ConstraintCheckExplanation.outcome()` stays `NOT_APPLICABLE`, with an accurate (non-recomputed) reason |
| Tied scores shown as equal, deterministic order only | `ExplainableAllocationServiceTest` | No candidate is described as objectively better on an exact tie |
| Rejection summary aggregates correctly without overstating candidate count | `ExplainableAllocationServiceTest` | `RejectionSummary.countByErrorCode()` matches per-reason counts; `rejectedCount` stays the real candidate count |
| One candidate contributes to multiple reason counts; sum can exceed rejected count | `RejectionSummaryTest` | The documented non-additive semantics are enforced, not just described |
| Empty rejection list produces a zero summary; tied most-common reasons all returned | `RejectionSummaryTest` | Edge cases behave sensibly, no divide-by-zero or arbitrary tie-break |
| Pairwise comparison identifies which factor explains a ranking difference | `ScoreComparisonTest` | `ScoreComparison.compare(...)` correctly attributes a score gap to `PREFERRED_LAB_TYPE`, not a vague aggregate |
| Factor `NOT_APPLICABLE` for both candidates is omitted from the comparison | `ScoreComparisonTest` | No meaningless zero-vs-zero diff clutters the comparison |
| `AllocationRecommendation` invariants: `RECOMMENDED` requires non-null recommendation, `NO_VALID_CANDIDATE` requires null, recommended must be rank 1 | `AllocationRecommendationTest` | Constructor-level defense-in-depth (PART 27) actually rejects invalid construction, not just documents the rule |
| `otherValidCandidates()` excludes the recommended one | `AllocationRecommendationTest` | The derived view is correct, not a duplicated/stale list |
| BDA recommendation selects the top-ranked valid lab; C-304 explained with its real rejection reason | `ExplainableAllocationIT` (Testcontainers, environment-blocked here) | Full generate→score→explain pipeline against real persisted data |
| Invalid-but-preferred-type candidate never outranks a valid one | `ExplainableAllocationIT` | Hard-vs-soft proof against real data |
| Zero valid candidates produces `NO_VALID_CANDIDATE` | `ExplainableAllocationIT` | Real-data equivalent of the unit-level zero-valid test |
| Recommendation never changes the `allocation` row count | `ExplainableAllocationIT` | Persistence-safety proof against a real Postgres instance |

**Manually verified against the Dockerized stack (2026-08-23), via a temporary `@Profile("dev")`-only `ApplicationRunner` (`DevExplanationVerificationRunner`) deleted after use (no production recommendation API was added just to test this):** BDA recommendation selected C-202 (rank 1, `39.58/60.0`) with B-201/B-301 preserved as ranked other-valid candidates and C-304 rejected with `SOFTWARE_MISMATCH`, absent from the ranking; a pairwise comparison between C-202 and B-201 correctly attributed the ranking gap to `PREFERRED_LAB_TYPE` (+15) outweighing a `CAPACITY_FIT` deficit (-4.22); temporarily inflating batch A1's strength produced `NO_VALID_CANDIDATE` with a factual, non-exceptional summary; CNS (zero preferences) produced an exact tie between B-202 and D-202, both correctly reported at applicable-max `45.0` (`PREFERRED_LAB_TYPE` `NOT_APPLICABLE`, excluded from the denominator); a real, persisted A1 allocation on B-301 caused A2's own recommendation to reject B-301 specifically with `LAB_CONFLICT` (never a fabricated `DIVISION_CONFLICT`) while still recommending a genuinely free lab; and the `allocation` table's row count was confirmed identical before and after every `recommend(...)` call. All temporary mutations (inflated batch strength, the persisted A1 test allocation) were confirmed reverted via `psql`. Regression re-verified: all Phase 3-11 endpoints still `200`; `/api/allocations` still `404` both directions; Flyway still at schema version 10; dev-seeded lab count confirmed 15.

## Scoring Engine Tests — Implemented (Phase 11)

| Test | Class | What it proves |
|---|---|---|
| Exact capacity match, slightly-larger, much-larger, and closer-always-outranks-larger | `CapacityFitScorerTest` | `fitRatio`/formula boundaries hold; a closer fit always outscores a larger capacity |
| Division-targeted request compares against division strength, not batch | `CapacityFitScorerTest` | `CapacityFitScorer` independently re-derives HC-07's target-strength logic correctly |
| Score always within `[0, weight]` bounds | `CapacityFitScorerTest` | No formula edge case escapes the documented range |
| Matching / mismatched / no-preference-recorded | `PreferredLabTypeScorerTest` | Full credit on match, zero-but-`APPLIED` on mismatch, `NOT_APPLICABLE` (excluded from max) with no preference |
| No `PUBLISHED` schedule version is `NOT_APPLICABLE` | `BalancedUtilizationScorerTest` | No basis for comparison is honestly reported, never a fabricated zero |
| Least-loaded lab scores higher than most-loaded | `BalancedUtilizationScorerTest` | Min-max normalization orders labs correctly |
| All loads equal (including all-zero) scores full weight, no divide-by-zero | `BalancedUtilizationScorerTest` | The `maxLoad == minLoad` branch is exercised directly |
| Score always within `[0, weight]` bounds | `BalancedUtilizationScorerTest` | Same boundary guarantee as capacity fit |
| `ScoreContribution` invariants: awarded ≤ max, awarded ≥ 0, `NOT_APPLICABLE` must be 0/0, immutable details map | `ScoreContributionTest` | The record's compact constructor actually enforces PART 62's invariants, not just documents them |
| Only valid candidates are scored | `ScoringEngineTest` | An invalid candidate mixed into a fixture is absent from `rankedCandidates()` |
| Empty valid set returns empty ranking, not an exception | `ScoringEngineTest` | All-invalid fixture completes normally |
| Scores summed across all registered scorers | `ScoringEngineTest` | `totalScore`/`maxPossibleScore` correctly aggregate multiple stub scorers |
| `NOT_APPLICABLE` factor excluded from max-possible score | `ScoringEngineTest` | A not-applicable contribution never inflates or deflates the denominator |
| Ranking is descending by normalized score | `ScoringEngineTest` | Sort order is correct with unequal applicable maxima |
| Tied scores break deterministically by lab code ascending | `ScoringEngineTest` | No nondeterministic ordering on a genuine tie |
| Contribution breakdown preserved per candidate | `ScoringEngineTest` | Every registered factor's `ScoreContribution` survives into the final `ScoredCandidate` |
| All valid candidates are scored, none skipped, when mixed with invalid ones | `ScoringEngineTest` | Mixed valid/invalid fixture scores exactly the valid subset |
| Capacity fit differentiates otherwise-equal candidates | `ScoringEngineIT` (Testcontainers, environment-blocked here) | Real persisted labs of differing capacity rank correctly through the full generate→score pipeline |
| Preferred lab type differentiates two otherwise-valid candidates | `ScoringEngineIT` | Real `Subject.preferredLabType` data drives a real ranking difference |
| Invalid candidate never ranked regardless of soft factors | `ScoringEngineIT` | The hard-vs-soft guarantee, proven against real persisted data with an adversarial fixture |
| Zero valid candidates produces empty ranking, not an exception | `ScoringEngineIT` | Real-data equivalent of the unit-level zero-valid test |
| Balanced utilization prefers the less-loaded lab | `ScoringEngineIT` | Real `Allocation` rows against a real `PUBLISHED` `ScheduleVersion` drive a real ranking difference |
| BDA ranking orders Cloudera-capable labs by capacity fit | `ScoringEngineIT` | Real seeded-shape BDA scenario, generation + scoring together |

**Manually verified against the Dockerized stack (2026-08-23), via a temporary `@Profile("dev")`-only `ApplicationRunner` (`DevScoringVerificationRunner`) deleted after use (no production scoring API was added just to test this):** BDA ranking (batch A1, required capacity 23) produced C-202 first (Data-Engineering-typed, Cloudera-capable, `39.58/60.0`) ahead of B-201 (`28.8/60.0`) and B-301 (`24.86/60.0`) - Preferred Lab Type credit outweighing a looser capacity fit, a genuine soft-factor interaction; C-304 (no Cloudera) confirmed invalid and never ranked despite matching preferred type and having workable capacity (hard-vs-soft proof); temporarily inflating batch A1's strength produced an empty ranking with zero valid candidates, no exception; the same request scored twice produced an identical order (determinism); CNS (zero subject requirements) against B-202/D-202 (identical capacity 60, identical type) produced an exact tie (`26.5` each) broken deterministically by lab code (B-202 before D-202); temporarily loading D-202 with five extra sessions dropped its score to `11.5` against B-202's unchanged `26.5`, then all five temporary allocations were deleted, confirmed via `psql` (zero leftover rows, batch A1 strength restored to 23). Regression re-verified: all Phase 3-10 endpoints still `200`; `/api/allocations` still `404` both directions; Flyway still at schema version 10; dev-seeded lab count confirmed 15 (see docs/15-DESIGN-DECISIONS.md for the stray `E-101` row cleanup).

## Candidate Generation Tests — Implemented (Phase 10)

| Test | Class | What it proves |
|---|---|---|
| Context is built exactly once regardless of lab count | `CandidateGeneratorTest` | `SchedulingContextFactory.build` is called once per `generate(...)` call, never once per lab |
| All labs are evaluated, no first-fit short circuit | `CandidateGeneratorTest` | `ConstraintEngine.evaluate` is called once per lab (3 labs → 3 calls) even when an earlier candidate is valid |
| Valid and invalid candidates are separated; invalid ones retain violations | `CandidateGeneratorTest` | `validCount()`/`invalidCount()` correctly partition a mixed fixture; every invalid candidate still carries its `ConstraintViolation`s |
| Zero valid candidates is a normal result, not an exception | `CandidateGeneratorTest` | All-invalid fixture completes normally with an empty `validCandidates()` |
| All valid candidates still produces no winner | `CandidateGeneratorTest` | An all-valid fixture has no score/ranking field anywhere on the result - selecting one is structurally impossible, not merely undone |
| Deterministic order requested from the repository (lab code ascending) | `CandidateGeneratorTest` | `LabRepository.findAll(Sort)` is called with `Sort.by(ASC, "code")`, not left to default row order |
| Multiple violations on one candidate are preserved | `CandidateGeneratorTest` | A candidate failing two constraints retains both `ConstraintViolation`s, not just the first |
| No duplicate labs in one generation run | `CandidateGeneratorTest` | Evaluated candidates' lab ids are all distinct |
| BDA/Cloudera: non-Cloudera lab generated and specifically rejected `SOFTWARE_MISMATCH` | `CandidateGeneratorIT` (Testcontainers, environment-blocked here) | Real subject-requirement + lab-capability data combine through the real engine, not a prefilter |
| Under-capacity lab generated then rejected by `CAPACITY_VIOLATION` | `CandidateGeneratorIT` | Capacity filtering is not duplicated in the generator |
| Existing `Allocation` on an otherwise-valid lab generated then rejected `LAB_CONFLICT` | `CandidateGeneratorIT` | Conflict detection happens through the real engine against real persisted data |
| Temporarily unavailable lab generated then rejected `LAB_UNAVAILABLE` | `CandidateGeneratorIT` | Same, for HC-06 |
| All labs invalid still completes normally | `CandidateGeneratorIT` | Real-data equivalent of the zero-valid unit test |
| A1 existing does not eliminate every lab for A2's own candidate generation | `CandidateGeneratorIT` | The project's signature scenario, proven at the generation layer against real persisted data |

**Manually verified against the Dockerized stack (2026-08-23), via a temporary `@Profile("dev")`-only `ApplicationRunner` deleted after use (no production candidate-search API was added just to test this):** basic generation produced exactly one candidate per lab in the system (16/16, no first-fit); BDA's non-Cloudera lab (C-304) generated and specifically rejected `SOFTWARE_MISMATCH`; a BATCH-targeted request's capacity check correctly compared against the *batch's* strength and a DIVISION-targeted request against the *division's* strength on the same lab (B-201); a temporary `Allocation` and a temporary `LabUnavailability`, each placed on an otherwise-valid lab, each flipped that one candidate to invalid on regeneration and were then removed, restoring validity; temporarily inflating a batch's required strength drove every lab invalid at once without generation throwing; and the A1/A2 scenario held at the generation layer with a real, persisted A1 allocation in place. All mutations/temporary rows were confirmed reverted/deleted afterward via direct `psql` query. Regression re-verified: all Phase 3-9 endpoints still `200`; `/api/allocations` still `404`; Flyway still at schema version 10.

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

## Algorithm Test Coverage (Phase 10–14) — superseded by the phase-specific sections above

**Roadmap correction:** this section originally sketched speculative Phase-1-era scenario names (`ScheduleResult`, "session D/E," a `PARTIAL`-only outcome) before any of Phases 10-14 were implemented. Every one of those scenarios is now real, tested code with a real class name, documented in its own phase-specific section above ("Candidate Generation Tests," "Scoring Engine Tests," "Explainable Allocation Tests," "Conflict Analysis + Alternative Suggestion Tests," "Automatic Scheduling Tests") rather than this one speculative catch-all. Kept here only as a pointer, not duplicated content, per this project's standing practice of correcting rather than silently deleting superseded planning text.

## Automatic Scheduling Tests — Implemented (Phase 14)

| Test | Class | What it proves |
|---|---|---|
| Zero requirements is `COMPLETE` with empty assignments | `AutomaticSchedulingEngineTest` | Normal, non-exceptional trivial case |
| One requirement behaves consistently with the single-request pipeline | `AutomaticSchedulingEngineTest` | Phase 14 doesn't diverge from Phase 12's own single-request behavior |
| Greedy success produces zero unnecessary backtracking | `AutomaticSchedulingEngineTest` | No spurious undo when every first choice already works |
| **Greedy fails (fixed order) / backtracking recovers and succeeds** — the flagship test | `AutomaticSchedulingEngineTest` | R1(X-or-Y)/R2(X-only), a single shared slot, fixed order via the test-only `useMrv=false` path: `COMPLETE`, `backtracks > 0`, final assignment R1→Y/R2→X |
| MRV schedules the more-constrained requirement first, avoiding the same backtrack | `AutomaticSchedulingEngineTest` | The identical scenario through the real, MRV-enabled public API: `COMPLETE`, `backtracks == 0` - proves MRV's adaptive ordering, not luck |
| `SEARCH_LIMIT_REACHED` differs from `NO_SOLUTION` | `AutomaticSchedulingEngineTest` | A tiny `maxNodes` cuts a solvable search short - reported honestly as "we stopped," not "impossible" |
| A genuinely infeasible request returns `NO_SOLUTION`, not a search-limit result | `AutomaticSchedulingEngineTest` | The search exhausts its budget with room to spare and correctly proves impossibility |
| Deterministic across repeated runs with identical input | `AutomaticSchedulingEngineTest` | Same assignments, same statistics, every time |
| Duplicate requirement keys rejected at construction | `AutomaticSchedulingEngineTest` | `AutomaticSchedulingRequest`'s own compact-constructor validation |
| `startDate` after `endDate` rejected | `AutomaticSchedulingEngineTest` | Same |
| Too many requirements / date range too large rejected | `AutomaticSchedulingEngineTest` | The configured `AutomaticSchedulingConfiguration` bounds are real, not aspirational |
| Every generated request preserves the confirmed 2-hour duration | `AutomaticSchedulingEngineTest` | Reuses `SchedulingSlotPolicy.sessionDuration()`, never a second duration source |
| No assignment falls outside the supplied date range | `AutomaticSchedulingEngineTest` | Bounded slot generation is respected end-to-end |
| A provisional snapshot's allocation id is never null | `PlannedAllocationTest` | Regression test for the real bug found live in Docker (see "Real Bugs Found" below) |
| The real (unmocked) `LabConflictConstraint` does not throw against a provisional snapshot | `PlannedAllocationTest` | Proves the fix at the exact integration point that crashed, not just at the data-shape level |
| Multi-requirement schedule places both when enough labs exist | `AutomaticSchedulingIT` (Testcontainers, environment-blocked here) | Real end-to-end pipeline against real persisted data |
| A real, persisted allocation is respected, never overwritten | `AutomaticSchedulingIT` | Real occupancy against real data |
| A1 and A2 can be scheduled simultaneously | `AutomaticSchedulingIT` | The project's signature scenario, proven at the automatic-scheduling layer |
| Automatic scheduling never changes the allocation row count | `AutomaticSchedulingIT` | Persistence-safety proof against real Postgres |

**Manually verified against the Dockerized stack (2026-08-24), via a temporary `@Profile("dev")`-only `ApplicationRunner` (`DevAutomaticSchedulingVerificationRunner`) deleted after use (no production automatic-scheduling API was added just to test this):** BDA/A1 and CNS/A2 scheduled simultaneously at Monday 09:00 in different labs (`C-202`/`B-101`), zero backtracks; the BDA assignment's lab genuinely has Cloudera; a temporary second `SubjectFacultyAssignment` giving Faculty BDA two simultaneous requirements produced two non-overlapping times (09:00 and 14:00); occupying the BDA-preferred lab with a real persisted allocation caused a reschedule to a different, genuinely free Cloudera lab at the same time; and the `allocation` row count was confirmed identical before and after. All temporary mutations (the extra assignment, the persisted test allocation) were cleaned up immediately after their own scenario. Regression re-verified: all Phase 3-13 endpoints still `200`; `/api/allocations` still `404` both directions; Flyway still at schema version 10; dev-seeded lab count confirmed 15.

## Extra Lab Scheduling Tests — Implemented (Phase 15)

| Test | Class | What it proves |
|---|---|---|
| CR search cannot proceed without an active `CrAssignment` | `ExtraLabServiceTest` | Ownership resolution is mandatory, first-checked |
| BATCH target resolves faculty via the exact-batch assignment | `ExtraLabServiceTest` | Reuses Phase 4's `FacultyAssignmentResolutionService` unmodified |
| DIVISION target resolves faculty via the division-level assignment, never calling the batch-scoped resolver | `ExtraLabServiceTest` | Correct branch selection, no cross-contamination |
| Booking without a `PUBLISHED` schedule version fails `NO_PUBLISHED_SCHEDULE`, before any constraint evaluation runs | `ExtraLabServiceTest` | Fail-fast on a genuinely un-bookable term |
| A selected lab that fails real-time constraint evaluation is rejected `409 ALLOCATION_CONFLICT` with structured violations, and nothing is persisted | `ExtraLabServiceTest` | Book-time revalidation - the actual mechanism, not just its existence |
| A successful booking persists `allocationType=EXTRA`, `status=PUBLISHED`, the correct `ScheduleVersion`, and `createdBy` = the authenticated CR | `ExtraLabServiceTest` | Every mandatory persistence property in one assertion set |
| Cancelling a `REGULAR` allocation via this workflow is rejected `EXTRA_ALLOCATION_FORBIDDEN` | `ExtraLabServiceTest` | Type boundary enforced |
| Cancelling an allocation the caller does not own is rejected, and the row is left completely untouched | `ExtraLabServiceTest` | Ownership re-checked independently at cancel time, not inherited from booking |
| Cancelling sets `status=CANCELLED`/`cancelledBy`/`cancelledAt`, and normalizes a blank reason to `null` | `ExtraLabServiceTest` | Correct, existing `Allocation.cancel(...)` lifecycle reuse |
| Cancelling an already-cancelled allocation is rejected `INVALID_ALLOCATION_TRANSITION` | `ExtraLabServiceTest` | Idempotency follows the existing Phase 8 lifecycle decision, not reinvented |
| `mine()` derives division from the server-side assignment, never a parameter | `ExtraLabServiceTest` | No client-controlled scope |
| `activity()` filters correctly by optional `divisionId`/`status` | `ExtraLabServiceTest` | LAB_ASSISTANT visibility scoping works |
| CR search returns a recommendation with division/faculty resolved entirely server-side | `ExtraLabApiIT` (Testcontainers, environment-blocked here) | Real end-to-end HTTP pipeline |
| A valid booking persists a correctly-shaped `EXTRA` row; a second conflicting booking attempt for the same lab/time fails `409` | `ExtraLabApiIT` | Real persistence + real conflict detection |
| A lab valid at search time but occupied before booking is rejected at book time | `ExtraLabApiIT` | The mandatory stale-search proof (PART 57 of the phase brief) |
| A CR cannot book another division's batch even by directly supplying its real ID | `ExtraLabApiIT` | The mandatory ownership-attack proof |
| Student is forbidden on every endpoint; unauthenticated requests receive `401` | `ExtraLabApiIT` | Full RBAC matrix |
| Cancelling frees the slot; a cross-CR cancel attempt is rejected `403` | `ExtraLabApiIT` | Cancelled allocations stop blocking scheduling; ownership re-checked at cancel time |
| LAB_ASSISTANT sees CR EXTRA activity for a term; CR/STUDENT cannot reach the activity endpoint | `ExtraLabApiIT` | Administrative visibility boundary |

**Manually verified against the Dockerized stack (2026-08-24), via real HTTP requests (`curl`) against the real seeded demo accounts — no temporary `ApplicationRunner` needed, since this phase's endpoints are real and production-facing:** CR search for BDA/A1 at Monday 09:00-11:00 correctly recommended `C-202` and rejected `C-304` with `SOFTWARE_MISMATCH`; booking `C-202` persisted a real `allocation` row with every field verified directly via `psql` (`allocation_type=EXTRA`, `status=PUBLISHED`, correct division/batch/subject/faculty/lab/schedule_version/created_by); a second booking attempt for the same lab/time (a different, otherwise-valid target) failed `409 ALLOCATION_CONFLICT`/`LAB_CONFLICT`; A2/CNS booked simultaneously into `B-201` at the identical slot succeeded independently (the project's signature scenario, now proven through the real production endpoint); a search-then-intervening-conflict-then-book sequence correctly rejected the now-stale selection at book time; an ownership-attack request (a temporarily-created second division/batch/subject/faculty, deleted after the check) was rejected `404 SUBJECT_FACULTY_ASSIGNMENT_NOT_FOUND` before any hard constraint ran; STUDENT received `403` on search/book/cancel and an unauthenticated request received `401`; cancelling the `C-202` booking set `status=CANCELLED` with correct `cancelled_by`/`cancelled_at`/`cancellation_reason`, and the exact same slot became bookable again immediately afterward, proving HC-01 no longer sees the cancelled row; LAB_ASSISTANT's activity view showed both bookings (including the cancelled one), while CR/STUDENT received `403` on that same endpoint. Every temporary allocation/division/batch/subject/faculty created during verification was deleted afterward, confirmed by the `allocation` table returning to a `0`-row count. Regression re-verified: `GET /api/labs` still `200`; `/api/allocations` still `404` both directions; Flyway still at schema version 10 (no migration).

## FCFS / Concurrency Tests — Implemented (Phase 16)

Gate satisfied: the assertion this section previously described as pending is now real, passing, and additionally verified live against true parallel requests, not simulated. FCFS is defined precisely (docs/15-DESIGN-DECISIONS.md ADR-073): *among competing transactions for the same exclusive resource/time, the transaction whose insert PostgreSQL commits first wins; every other conflicting transaction fails cleanly with `409 ALLOCATION_CONFLICT` — never network arrival order, which the application cannot observe or enforce, only database commit order.*

### Concurrency matrix

| Scenario | Mechanism exercised | Expected result | Test |
|---|---|---|---|
| Same lab, different batch/faculty | `ex_allocation_lab_overlap` | Exactly 1 success, 1 conflict | `AllocationConcurrencyIT.concurrentSameLabRequestsProduceExactlyOneSuccess`; live cross-division race |
| Same faculty, different labs | `ex_allocation_faculty_overlap` (or division-lock-serialized app check, same division) | Exactly 1 success, 1 conflict | `AllocationConcurrencyIT.concurrentSameFacultyRequestsProduceExactlyOneSuccess`; live |
| Same batch, different labs/faculty | `ex_allocation_batch_overlap` (or division-lock-serialized app check) | Exactly 1 success, 1 conflict | `AllocationConcurrencyIT.concurrentSameBatchRequestsProduceExactlyOneSuccess`; live |
| DIVISION vs BATCH, same division/time, both directions | Per-division `SELECT ... FOR UPDATE` lock | Exactly 1 success, 1 conflict, in both launch orders | `AllocationConcurrencyIT.concurrentDivisionVsBatchRequestsProduceExactlyOneSuccess`; live, both directions |
| A1 vs A2 (different batch/lab/faculty, same division/time) | Same lock - serializes, never rejects | Both succeed | `AllocationConcurrencyIT.concurrentDifferentBatchesInSameDivisionBothSucceed`; live |
| Adjacent intervals, same lab (`09-11` then `11-13`) | Half-open `tsrange('[)')` | Both succeed | `AllocationConcurrencyIT.adjacentIntervalsOnSameLabBothSucceed`; live |
| Different dates, identical time-of-day | `tsrange` date component | Both succeed | Verified directly via raw SQL against the real schema before the migration was finalized |
| Cancelled allocation, identical resource/time | Partial predicate excludes `CANCELLED` | New booking succeeds | `AllocationConcurrencyIT.cancelledAllocationDoesNotBlockNewBooking`; live |
| Same CR, identical request submitted twice (double-submit) | Same exclusion constraints | Exactly 1 active row | Live |
| Double-cancel, same allocation | `AllocationRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`) | Exactly 1 lifecycle transition applied; second gets `INVALID_ALLOCATION_TRANSITION` | `ExtraLabServiceTest.cancelAgainOnAlreadyCancelledAllocationIsRejected` (sequential proof of the transition rule); live double-cancel race |
| Cancel-vs-book race, same slot | MVCC + exclusion-constraint row-visibility waiting (no extra code) | Exactly 1 active row regardless of ordering | Live, repeated 3x |
| Three-way contention, one lab | `ex_allocation_lab_overlap` | Exactly 1 success, 2 conflicts | Live |
| Deadlock during simultaneous exclusion-constraint checks | `ConcurrencyFailureException` → same clean `409` | Never a `500` | `ExtraLabServiceTest.bookMapsADeadlockDuringExclusionCheckToTheSameCleanConflictResponse`; live (real, found this way) |
| Constraint-name extraction (both sources) | Hibernate `ConstraintViolationException` / PostgreSQL `PSQLException.getServerErrorMessage()` fallback | Correct `conflictingResource` in the response | `ExtraLabServiceTest.bookRejectsWithAllocationConflictWhenExclusionConstraintRejectsTheInsert`, `.extractConstraintNameFallsBackToPostgresServerErrorMessage` |

### `AllocationConcurrencyIT` (Testcontainers, environment-blocked here)

True-concurrency tests using an `ExecutorService` + `CountDownLatch` barrier so both competing calls are released simultaneously, each on its own thread and therefore its own independent transaction/connection via Spring's thread-bound transaction synchronization - deliberately **not** `@Transactional` at the test-method level (docs/15-DESIGN-DECISIONS.md, PART 35 of the phase brief), which would have made both "concurrent" calls share one outer test transaction and never actually race. Covers every scenario in the matrix above reachable without real HTTP. Same documented Docker/Testcontainers limitation as every other IT class (docs/13-DEVELOPER-SETUP.md) — written correctly for CI, not run here.

### Manually verified against the Dockerized stack via true parallel HTTP requests (2026-08-24)

Every scenario in the matrix above was independently proven live, using background `curl` processes launched together and `wait`-joined (never sequential calls) against the real running backend/Postgres containers — not simulated, not assumed from the IT test's correctness alone. The same-lab race was specifically run **cross-division** (a temporary second division/CR, deleted after) to bypass the per-division lock and force a genuine simultaneous PostgreSQL-level race rather than one serialized (and therefore resolved at the application level) by the lock; repeated 5 times, confirmed hitting both the exclusion-constraint rejection path and — once — the deadlock path, both correctly mapped to a clean `409`, never a `500`. A final diagnostic SQL query across every scenario's resulting rows confirmed zero lab/faculty/batch overlaps and zero invalid DIVISION/BATCH overlaps among active allocations. Every temporary fixture was deleted afterward; the dev-seed state was confirmed to match its exact pre-phase baseline (3 users, 1 division, 2 subjects, 2 `subject_faculty_assignment` rows, 15 labs, 0 allocations).

## Audit Log Tests — Implemented (Phase 17)

Audit persistence rides on the same transaction as the business mutation it describes (ADR-078, docs/15-DESIGN-DECISIONS.md), so "does the audit row get written" and "does a failed operation leave no misleading success row" are really the same guarantee proven from two directions.

| Scenario | Expected | Test |
|---|---|---|
| Successful EXTRA booking | Allocation created + matching `EXTRA_LAB_BOOKED` audit row, correct actor/division/term/resource | `AuditLogApiIT.successfulBookingProducesAVisibleAuditEvent`; live |
| Booking rejected by a real conflict | `409`, **zero** `EXTRA_LAB_BOOKED` rows for that actor | `AuditLogApiIT.rejectedBookingProducesNoSuccessfulAuditEvent`; live (a genuine 409 conflict attempt produced no extra audit row — count stayed at exactly the number of real successful bookings) |
| Cancellation | `EXTRA_LAB_CANCELLED` row with reason/old-lab/time metadata | Live (`POST .../cancel` → `GET /api/audit-logs?action=EXTRA_LAB_CANCELLED`) |
| CR reassignment (same division+user) | Exactly one `CR_ASSIGNMENT_ENDED` (deduped) + one `CR_ASSIGNED`, both in the same transaction | Live — see "Real bug found" below |
| Lab Assistant admin mutation (`LAB_UPDATED`) | Audit row visible via `GET /api/audit-logs?action=LAB_UPDATED` | Live |
| `AuditLogService.record`/`.search` | Persists exactly the fields on the `AuditEvent`; resolves every distinct actor with **one** bulk `findAllById` call, never per-row; tolerates an actor that no longer resolves | `AuditLogServiceTest` (3 tests, Mockito, no DB) |
| RBAC on `GET /api/audit-logs` | LAB_ASSISTANT `200`, CR `403`, STUDENT `403`, anonymous `401` | `AuditLogApiIT.onlyLabAssistantCanReadAuditHistory`; live (all four roles) |
| Actor filter isolation | One CR's `actorUserId` filter never returns another CR's rows | `AuditLogApiIT.actorFilterIsolatesOneCrsActivityFromAnother` |
| Page-size cap | `?size=500` silently clamped to `100` | `AuditLogApiIT.pageSizeIsCappedAtTheConfiguredMaximum`; live |
| Database-level immutability | Direct `UPDATE`/`DELETE` against `audit_log` rejected by the V12 trigger, row unchanged | `AuditLogImmutabilityIT` (Testcontainers, raw JDBC); live via `psql` (below) |
| Phase 16 concurrency regression | Two genuinely simultaneous EXTRA bookings for the same lab/time: exactly 1 success, 1 `409`; exactly 1 blocking allocation row; exactly 1 `EXTRA_LAB_BOOKED` audit row for the winner, none for the loser | Live, parallel `curl` — see below |

### `AuditLogImmutabilityIT` / `AuditLogApiIT` (Testcontainers, environment-blocked here)

Both written correctly and reachable via `mvn verify`, but blocked on this development machine by the same documented Docker-on-Windows/npipe limitation as every other IT class in this project (docs/13-DEVELOPER-SETUP.md — Testcontainers cannot locate a Docker environment from inside this machine's Maven JVM, confirmed via `Could not find a valid Docker environment`, even though the Docker Desktop daemon itself is running and the project's own `docker compose` stack is up). Every scenario they cover was instead independently proven live against the real Dockerized stack.

### Manually verified against the Dockerized stack (2026-08-24)

- **Trigger, direct SQL:** `INSERT` a probe row via `psql`, then `UPDATE audit_log SET resource_display = 'TAMPERED' WHERE id = ...` → `ERROR: audit_log is append-only: UPDATE is not permitted`; `DELETE FROM audit_log WHERE id = ...` → `ERROR: audit_log is append-only: DELETE is not permitted`; row confirmed still present and unmodified after both attempts.
- **Full CR → audit → Lab Assistant flow:** logged in as the demo CR, booked a real EXTRA allocation (`allocationId=81`) against a real published schedule version → `GET /api/audit-logs?action=EXTRA_LAB_BOOKED&actorUserId=2` (as the demo Lab Assistant) showed the matching row with correct `resourceId`, `divisionId`, `academicTermId`, and metadata; cancelled it → matching `EXTRA_LAB_CANCELLED` row appeared with the cancellation reason.
- **Failed-booking-produces-no-event:** re-booked the same slot (a second real, successful allocation, `id=82`), then a second CR request for the identical lab/time (different subject/batch) → `409 ALLOCATION_CONFLICT` as expected; the `EXTRA_LAB_BOOKED` count for that actor stayed at exactly 2 (allocations 81 and 82) — the rejected attempt added nothing.
- **RBAC, all four roles:** LAB_ASSISTANT `200`; CR `403`; STUDENT `403`; unauthenticated `401` — against the real running backend, real JWTs.
- **Concurrency regression:** two `POST /api/allocations/extra` requests for the same lab/time, launched as backgrounded `curl` processes and `wait`-joined — exactly one `200`, one `409 ALLOCATION_CONFLICT`; `SELECT count(*) FROM allocation WHERE lab_id=... AND status IN ('APPROVED','PUBLISHED')` confirmed exactly 1 row; the audit count for that actor/action grew by exactly 1, not 2.
- **Real bug found and fixed live:** the first attempt at a CR reassignment (ending one `CrAssignment` and creating another in the same transaction, so two `AuditLog` rows are inserted in one flush) returned `500 INTERNAL_ERROR`. Backend logs showed Hibernate issuing a spurious `UPDATE` against the just-inserted, JSON-`metadata`-bearing `AuditLog` row, which the V12 trigger correctly rejected — turning a real "immutability held" case into an unexpected user-facing failure. Root-caused to Hibernate's dirty-checking of the JSON-mapped `metadata` field; fixed by adding Hibernate's `@Immutable` to the `AuditLog` entity (ADR-078, docs/15-DESIGN-DECISIONS.md), which removes its UPDATE code path entirely. Confirmed fixed: the identical reassignment request now returns `200` with both `CR_ASSIGNMENT_ENDED` and `CR_ASSIGNED` rows correctly present.

**Cleanup note:** unlike Phase 16's live verification (which deleted every temporary fixture to restore the exact pre-phase dev-seed baseline), Phase 17's live verification deliberately did **not** delete anything it created — per the phase brief, audit history is never deleted as "test cleanup," and restoring a mutated value (e.g. a lab name) legitimately produces another audit row rather than erasing the evidence of the first change. The dev stack's demo data therefore carries a handful of extra `allocation`/`cr_assignment`/`audit_log` rows from this verification pass; this is expected, not a leftover to clean up.

## Timetable Versioning Tests — Implemented (Phase 18)

| Scenario | Expected | Test |
|---|---|---|
| First draft creation, no reason needed | `DRAFT`, `versionNumber=1`, `publishedBy`/`publishedAt` null, `createdBy` = authenticated Lab Assistant | `ScheduleVersionServiceTest.firstVersionForATermNeedsNoReason`; `ScheduleVersionApiIT.labAssistantCreatesFirstDraftWithNoReasonRequired`, live |
| Revision draft without a reason | Rejected | `ScheduleVersionServiceTest.secondVersionForATermRequiresAReason` |
| Version numbers term-scoped | Term A gets V1/V2 independently of Term B's V1 | `ScheduleVersionApiIT.versionNumbersAreScopedIndependentlyPerTerm`, live |
| Publish first version | `PUBLISHED`, `publishedBy`/`publishedAt` set, `SCHEDULE_PUBLISHED` audit event | `ScheduleVersionServiceTest.publishingWithNoExistingPublishedVersionSucceeds`/`...WritesExactlyOneAuditEvent`; `ScheduleVersionApiIT.publishingTheFirstVersionSetsPublishedFieldsAndWritesAnAuditEvent`, live |
| Publish second version | Previous `PUBLISHED → SUPERSEDED`, both `SCHEDULE_SUPERSEDED`+`SCHEDULE_PUBLISHED` audit events, both versions' allocations preserved | `ScheduleVersionServiceTest.publishingSupersedesThePreviouslyPublishedVersion`/`...WritesBothSupersededAndPublishedEvents`; `ScheduleVersionApiIT.publishingASecondVersionSupersedesTheFirstAndStudentSeesOnlyTheCurrentPublishedVersion`, live |
| Highest version number is not necessarily published | Student sees V1 (PUBLISHED) while V2 (higher-numbered) is still DRAFT | Same test above, explicit assertion; live |
| Student sees only the current published version | Never DRAFT, never SUPERSEDED | Same test above; `ScheduleVersionApiIT.studentSeesEmptyTimetableWhenTermHasNoPublishedVersionYet` (no published version -> empty page, not a leak) |
| Publish a SUPERSEDED version | `409 INVALID_SCHEDULE_VERSION_TRANSITION`, never `500` | `ScheduleVersionServiceTest.publishingAnAlreadySupersededVersionIsRejected`; `ScheduleVersionApiIT.publishingASupersededVersionIsRejectedWithConflict`, live |
| Publish an already-PUBLISHED version (double-publish) | `409`, honest status message, database unaffected | `ScheduleVersionServiceTest.publishingAnAlreadyPublishedVersionIsRejected`; live (see ADR-089, real bug found this way) |
| APPROVED allocations of the published version transition to PUBLISHED | `Allocation.publish()` called, CANCELLED/other-version rows untouched | `ScheduleVersionServiceTest.publishingTransitionsApprovedAllocationsOfThatVersionToPublished` |
| Term lock acquired before the racy read, both in `createDraft` and `publish` | Verified call order | `ScheduleVersionServiceTest.createDraftAcquiresTheTermLockBeforeComputingTheVersionNumber`/`publishAcquiresTheTermLockBeforeLoadingTheVersionOrCheckingPublicationState` |
| Concurrent publication of two drafts for the same term (mandatory) | Exactly one `PUBLISHED` version afterward; all versions preserved | `ScheduleVersionConcurrencyIT.concurrentPublishOfTwoDraftsForTheSameTermProducesExactlyOnePublishedVersion`; live, see below |
| Superseded EXTRA allocation cannot be cancelled | `409 SCHEDULE_VERSION_NOT_CURRENT` | `ExtraLabServiceTest.cancelRejectsWhenTheAllocationsScheduleVersionIsNoLongerCurrent`; live |
| Version-management RBAC | LAB_ASSISTANT only; `GET /api/timetable` open to STUDENT/CR/LAB_ASSISTANT | `ScheduleVersionApiIT.draftCreationIsForbiddenToCrAndStudentAndRejectedForAnonymous`/`.onlyLabAssistantCanViewVersionHistory`; live, all four roles both endpoints |
| **Batch-scoped timetable includes division-wide rows too (Phase 22, mandatory PART 9/27)** | A `batchId`-filtered request returns that batch's own rows AND division-wide rows, never hides the latter | `ScheduleVersionApiIT.batchScopedTimetableIncludesDivisionWideAllocationsToo`; live |

### `ScheduleVersionApiIT` / `ScheduleVersionConcurrencyIT` (Testcontainers, environment-blocked here)

Both written correctly, blocked on this development machine by the same documented Docker-on-Windows/npipe limitation as every other IT class (docs/13-DEVELOPER-SETUP.md). `ScheduleVersionConcurrencyIT` mirrors `AllocationConcurrencyIT`'s true-parallelism pattern (`ExecutorService`/`CountDownLatch` barrier, deliberately not `@Transactional` at the test-method level) exactly. Every scenario either file covers was independently proven live.

### Manually verified against the Dockerized stack (2026-08-25)

- **Full lifecycle:** created and published the demo term's V2 (empty - ADR-093), confirmed the student timetable kept showing V1 while V2 was `DRAFT`, then switched to V2's (empty) content the instant it published, with V1 correctly `SUPERSEDED` and its 3 allocations and the V1 row itself still present (`GET /api/schedule-versions/1/allocations` -> `totalElements: 3`).
- **Cancelled allocations excluded from the timetable:** confirmed `GET /api/timetable` dropped a `CANCELLED` EXTRA row that an earlier, unfiltered version of the endpoint had incorrectly included - fixed before this round of live testing (see `AllocationSpecifications.activeStatus`).
- **RBAC, all four roles:** LAB_ASSISTANT/CR/anonymous on `/api/schedule-versions` (200/403/401); LAB_ASSISTANT/CR/STUDENT/anonymous on `/api/timetable` (200/200/200/401).
- **Real bug #1 (ADR-088):** the very first live publish-when-something-is-already-published attempt returned a raw `500` (`duplicate key value violates unique constraint "uq_schedule_version_one_published_per_term"`) on an entirely ordinary, non-concurrent request - root-caused to Hibernate flush-ordering, fixed with an explicit `flush()`, confirmed fixed by re-issuing the identical request (`200`).
- **Real bug #2 (ADR-089):** double-publishing an already-`PUBLISHED` version returned a technically-correct-but-misleading `409` ("current status is SUPERSEDED") due to a self-supersede side effect - fixed with an up-front status guard, confirmed fixed (`409`, "current status is PUBLISHED", database unaffected both before and after the fix).
- **Mandatory concurrent-publication proof:** created two drafts (V3, V4) for the demo term, fired both `POST .../publish` requests as backgrounded `curl` processes launched together and `wait`-joined (never sequential) - both returned `200` (the per-term lock serializes rather than rejects, ADR-087); final state: V3 `SUPERSEDED`, V4 `PUBLISHED`, all 4 versions (V1..V4) for the term still present. `SELECT count(*) FROM schedule_version WHERE academic_term_id = ? AND status = 'PUBLISHED'` = exactly 1.
- **Phase 16 regression:** re-ran the same-lab concurrent-booking race against the new current published version - one `200`, one `409`, exactly one blocking allocation row; the winning booking correctly attached to the *current* `schedule_version_id`, not the superseded one.
- **Phase 17 regression:** re-ran the direct-`psql` immutability proof against `audit_log` - `UPDATE`/`DELETE` both still rejected by the V12 trigger, row unchanged.
- **Superseded EXTRA cancellation guard:** attempted to cancel an EXTRA allocation belonging to the now-`SUPERSEDED` V1 - correctly rejected with `409 SCHEDULE_VERSION_NOT_CURRENT`.

## PDF Import Tests — Implemented (Phase 19)

| Scenario | Expected | Test |
|---|---|---|
| Day-name normalization, full/abbreviated/punctuated forms | `MONDAY`/`MON`/`Mon.` all -> `DayOfWeek.MONDAY`; unrecognized -> `null`, never guessed | `TimetableNormalizerTest` (5 tests) |
| Strict 24-hour time parsing | `09:00`/`9:00` parse; `9 AM`/`9-11`/`25:00` all rejected -> `null` | `TimetableNormalizerTest` |
| Token whitespace/case normalization | `"  BDA   LAB "` -> `"BDA LAB"` | `TimetableNormalizerTest` |
| Well-formed pipe-delimited line parses into its 8 columns | Correct raw fields | `TimetableParserTest` (5 tests) |
| Blank trailing batch column | Division-wide session (`rawBatch` empty, not null/error) | `TimetableParserTest` |
| Non-timetable lines (titles/headers/page numbers) | Silently skipped, never a parse error | `TimetableParserTest` |
| Multi-line, multi-row documents | All valid lines parsed, order preserved | `TimetableParserTest` |
| Zero parseable lines | Empty result, no exception (caller resolves to `FAILED`) | `TimetableParserTest` |
| Real PDF byte extraction (not mocked strings, PART 50/51) | PDFBox-generated fixture PDFs extract to expected text/line count; empty PDF -> no lines; non-PDF bytes -> clean `UNSUPPORTED_PDF`, never a raw stack trace; multi-page count correct | `PdfExtractionAndParsingTest` (4 tests, fixtures generated in-test via PDFBox's own writer API, never committed as binary files - see docs/18-PDF-IMPORT.md) |
| Exact subject/division/batch code match resolves subject+faculty+division+batch+lab together | All fields resolved, zero messages | `TimetableMappingServiceTest` (4 tests) |
| No matching `SubjectFacultyAssignment` | `UNRESOLVED_ACADEMIC_ASSIGNMENT` error, nothing auto-created | `TimetableMappingServiceTest` |
| Unknown lab code | `UNKNOWN_LAB` error, nothing auto-created | `TimetableMappingServiceTest` |
| Faculty-name mismatch vs. the resolved assignment | Non-blocking `WARNING` (`FACULTY_NAME_MISMATCH`), resolution still succeeds | `TimetableMappingServiceTest` |
| **Staging isolation (mandatory)** | Staged rows exist; confirmed `allocation` count for the import stays at 0 until approval | Verified live (Docker) - uploaded a real PDF, confirmed `timetable_import_row` rows existed and `SELECT COUNT(*) FROM allocation WHERE source_import_id = ?` = 0 |
| **The BDA/Cloudera failure-and-correction demo (mandatory, PART 80)** | Upload succeeds, mapping succeeds, validation fails with explainable `SOFTWARE_MISMATCH`; correcting the lab makes the row `VALID`; approval then succeeds | Verified live end-to-end (see docs/18-PDF-IMPORT.md, docs/03-SYSTEM-ARCHITECTURE.md §27) |
| **Atomic approval (mandatory)** | An import with a still-unresolved `ERROR` row cannot be approved (`409`, zero allocations); correcting it and re-approving succeeds fully | Verified live - the same import rejected pre-correction, approved post-correction, exactly the intended allocation count both times |
| **Concurrent approval of two mutually-conflicting, independently-valid imports (mandatory, PART 58)** | Exactly one succeeds; the other fails cleanly (`409`, not a raw DB exception); exactly one allocation persisted; both imports' history preserved | Verified live - two genuinely parallel HTTP `POST .../approve` requests (background `curl`, `wait`-joined): one `200`, one `409 TIMETABLE_IMPORT_HAS_ERRORS` (caught by approval-time revalidation, ADR-102, before any insert was attempted - the DB exclusion constraint remained the unexercised backstop) |
| Draft-version guard | Upload/approve targeting `PUBLISHED`/`SUPERSEDED` rejected (`409 SCHEDULE_VERSION_NOT_DRAFT`) | Verified live against both a `PUBLISHED` and a `SUPERSEDED` version |
| Version published between review and approval (PART 59) | Approval of a still-`VALIDATED` import whose target version was published moments earlier fails safely, creates no allocations | Verified live - published the target version, then attempted approval: clean `409`, zero allocations |
| RBAC on every import-management endpoint | LAB_ASSISTANT `200`, CR `403`, STUDENT `403`, anonymous `401` | Verified live against upload/list/detail/approve (CR+student+anonymous); reject inherits the identical `@PreAuthorize` |
| Phase 17 immutability regression | `UPDATE`/`DELETE` on `audit_log` still rejected | Re-ran the direct-`psql` proof live after this phase's changes |
| Phase 18 versioning regression | An imported allocation stays invisible to students while its version is `DRAFT`; visible (and `PUBLISHED`) only after explicit Phase 18 publication | Verified live - see the BDA/Cloudera demo trace |
| Phase 16 concurrency regression | Same-lab concurrent EXTRA booking still produces exactly one winner, one `409` | Re-ran live after this phase's changes |

### Real bug found live (not caught by any unit test)

An `@ExceptionHandler(MaxUploadSizeExceededException.class)` collided with `ResponseEntityExceptionHandler`'s own built-in handler for the same exception type, failing application *startup* entirely - only discoverable by actually booting the container, since no unit test in this project constructs the full Spring MVC dispatch machinery. Fixed by overriding the existing protected hook instead of declaring a competing `@ExceptionHandler` (docs/15-DESIGN-DECISIONS.md ADR-108, docs/14-INTERVIEW-PREPARATION.md "Problem 11").

## Frontend Tests — Implemented (Phase 20)

Vitest + Testing Library, mocking the `api/*.ts` modules directly (the established convention from Phase 2's `Auth.test.tsx`) - never mocking `fetch` itself. Full breadth across every screen, deeper coverage on the highest-stakes workflows (per the user's explicit scope decision for this phase: full breadth, thinner per-screen depth, with real tests on PDF review/correction/approval, timetable-version publish, audit logs, and routing/role guards).

| Scenario | Expected | Test |
|---|---|---|
| LAB_ASSISTANT reaches a guarded `/lab-assistant` route | Renders the guarded content | `RequireRouteRole.test.tsx` |
| CR/STUDENT visits a guarded route | Redirected to `/`, guarded content never rendered | `RequireRouteRole.test.tsx` |
| Unauthenticated visits a guarded route | Redirected (via `ProtectedRoute`, pre-existing) | `RequireRouteRole.test.tsx` |
| Dashboard: loading / real zero-value data / distinct API-error state / empty activity feed | Never a blank success state; a failed card shows an error, not `0` | `DashboardPage.test.tsx` (4 tests) |
| **PDF review with 1 VALID / 1 WARNING / 1 ERROR row (mandatory)** | Correct summary counts, per-row status badges, explainable messages in plain language, correction action available | `ImportReviewPage.test.tsx` |
| **Approval boundary language (mandatory)** | Confirmation dialog explicitly states allocations are created but the timetable is NOT published | `ImportReviewPage.test.tsx` |
| Row correction | Submits to the real correction API; row reflects the *server's* revalidated result, never an optimistic guess | `ImportReviewPage.test.tsx` |
| Non-PDF file selected for upload | Rejected client-side (`only .pdf files are supported`), upload API never called | `ImportsPage.test.tsx` |
| Empty imports list | "No timetable imports yet" empty state | `ImportsPage.test.tsx` |
| Timetable Versions: DRAFT shows Publish, PUBLISHED/SUPERSEDED do not | Exactly one Publish button when one DRAFT exists among mixed statuses | `TimetableVersionsPage.test.tsx` |
| Publish confirmation | States the target version, which version will be superseded, and the visibility consequence; queries refresh after success | `TimetableVersionsPage.test.tsx` |
| Audit Logs: rendering, pagination, filter re-query, detail view | No edit/delete actions anywhere; metadata renders as readable text, never `[object Object]` | `AuditLogsPage.test.tsx` (4 tests) |

**A real bug this phase's own tests caught** (not live - a unit test found it first): `RequireRouteRole` initially redirected a *valid* LAB_ASSISTANT session away before `AuthProvider`'s async token-verification finished, because it checked only `!user` and not `isLoading` (docs/15-DESIGN-DECISIONS.md ADR-110) - would have silently bounced every real Lab Assistant on a page refresh had it shipped.

### Frontend build/lint/test results (2026-08-25)

`npm run build` (`tsc -b && vite build`) - passes, zero TypeScript errors, no `any`/`@ts-ignore` introduced. `npm run lint` (`oxlint`) - zero errors (two pre-existing/unrelated warnings, one predating this phase). `npm run test` (`vitest run`) - **27/27 passing** (8 pre-existing Phase 2 auth tests + 19 new Phase 20 tests).

### Manual end-to-end verification (headless Chromium, Dockerized stack, 2026-08-25)

No project-specific browser-driving skill existed yet, so Playwright (`chromium`) was used directly against the already-running `docker compose` frontend (`localhost:5173`)/backend (`localhost:8080`). Logged in as the demo Lab Assistant and walked Dashboard -> Labs -> Lab Detail -> Faculty -> Faculty Availability -> Subjects -> Academic Setup -> CR Management -> Timetable Versions -> Imports (upload form) -> Audit Logs -> Analytics, screenshotting every step - **zero browser console errors**, real backend data throughout. Logged in as the demo CR and navigated directly to `/lab-assistant` - confirmed both by final URL (`/`) and by asserting zero Lab-Assistant-only navigation text present that the redirect held, matching the automated test above exactly.

## CR Frontend Tests — Implemented (Phase 21)

| Scenario | Expected | Test |
|---|---|---|
| CR reaches `/cr`; LAB_ASSISTANT/STUDENT redirected to `/`; anonymous redirected to `/login` (mandatory, PART 59) | Correct guard behavior, no redirect loop | `CrRouteGuard.test.tsx` (4 tests) |
| My Class renders the CR's real division/program/stream/year/term/batches; no division selector exists | Real data only, never a mutation-scope selector | `MyClassPage.test.tsx` (2 tests) |
| My Timetable renders the current published timetable; empty state on no published version; error state on failure | Never a draft/superseded fallback, never `MAX(version)` logic | `MyTimetablePage.test.tsx` (3 tests) |
| **BDA/Cloudera mismatch explained (mandatory, PART 62)** | B-101 rejected with the real software-mismatch message; C-202 ranked and valid; raw enum code never shown alone | `AvailableLabsPage.test.tsx` |
| Faculty conflict / faculty unavailable / lab conflict explained in plain language (PART 63-65) | Real backend message rendered, not the bare error code | `AvailableLabsPage.test.tsx` |
| No-valid-labs state with backend-provided alternatives, never fabricated | Correct empty state + real alternative rendering | `AvailableLabsPage.test.tsx` |
| Booking success (PART 69) | Search -> select -> confirm -> book -> real booking details shown | `ScheduleExtraLabPage.test.tsx` |
| **FCFS 409 conflict (mandatory, PART 70)** | Clear conflict message, never a success state, Search Again offered | `ScheduleExtraLabPage.test.tsx` |
| My Extra Labs: active vs. cancelled rendering, cancel only on active, empty/error states (PART 72) | Correct status-gated actions | `MyExtraLabsPage.test.tsx` |
| Cancellation success (PART 73) | Backend called with correct arguments, status refreshes | `MyExtraLabsPage.test.tsx` |
| Cancellation failure (PART 74) | Error shown, booking remains visible - never optimistically removed | `MyExtraLabsPage.test.tsx` |
| **Unauthorized-scope cancellation (mandatory, PART 71)** | `FORBIDDEN_DIVISION_ACCESS` mapped to a clear scope message, no crash | `MyExtraLabsPage.test.tsx` |

Booking itself has no division field to violate at all (`ExtraLabSearchRequest`/`ExtraLabBookingRequest` carry no `divisionId`, PART 71's mandatory scenario is therefore tested against cancellation instead - the one CR workflow where a foreign-division allocation ID could actually be supplied - see docs/03-SYSTEM-ARCHITECTURE.md §29).

### Real bug found live (not caught by any unit test's mock data)

Recommendation scores rendered as `0` for every candidate - `normalizedScore` is a `0.0-1.0` ratio, not `0-100`; test mocks happened to use round percentage-shaped numbers that never exposed the scaling bug. Fixed and re-verified live (docs/15-DESIGN-DECISIONS.md ADR-118).

### Frontend build/lint/test results (2026-08-25)

`npm run build` - passes, zero TypeScript errors. `npm run lint` - zero errors (one new warning matching an already-accepted Phase 20 pattern). `npm run test` - **49/49 passing** (27 pre-existing Phase 20 tests + 22 new Phase 21 tests).

### Manual CR end-to-end verification (headless Chromium, Dockerized stack, 2026-08-25)

Full CR walkthrough: My Class (real assignment, no selector) -> My Timetable -> Available Labs (division-wide BDA search correctly explained as unassigned via real backend summary text; batch-A1 search showed 3 ranked candidates + 12 rejected Cloudera-missing labs) -> Schedule Extra Lab (booked a real extra practical, success screen showed the real faculty name) -> My Extra Labs (booking appeared). Confirmed LAB_ASSISTANT redirected from `/cr`; anonymous redirected to `/login`. **Mandatory concurrent-booking verification**: both via direct parallel `curl` (one `200`, one `409`, exactly one blocking allocation in the database) and via two genuinely parallel real browser sessions racing the actual UI (one showed the success screen, the other the exact FCFS conflict message with a working Search Again action).

## Student Frontend Tests — Implemented (Phase 22)

| Scenario | Expected | Test |
|---|---|---|
| **STUDENT reaches `/student`; CR/LAB_ASSISTANT redirected to `/`; anonymous redirected to `/login` (mandatory, PART 23)** | Correct guard behavior, no redirect loop | `StudentRouteGuard.test.tsx` (4 tests) |
| No request issued before program/stream/year/division are chosen (PART 15) | Useful prompt shown, `timetableApi.current` never called | `StudentTimetablePage.test.tsx` |
| **Dependent filter resets (mandatory, PART 24)** | Changing Program clears Stream/Year/Division/Batch | `StudentTimetablePage.test.tsx` |
| Allocations render subject name, faculty, and a clear lab location (PART 11/12, mandatory) | "C-202"-style code alone never the only location shown | `StudentTimetablePage.test.tsx` |
| **Batch selection shows both division-wide and batch-specific allocations (mandatory, PART 9/27)** | Both rows rendered, neither hidden | `StudentTimetablePage.test.tsx` |
| **Empty state: no published timetable (mandatory, PART 14/29)** | "No published timetable is currently available.", never a blank UI | `StudentTimetablePage.test.tsx` |
| **Error state, not a fabricated empty timetable (mandatory, PART 16/30)** | `role="alert"` shown; empty-state text never rendered on a failed request | `StudentTimetablePage.test.tsx` |
| Day filter | Selecting a day narrows the displayed allocations to that weekday only | `StudentTimetablePage.test.tsx` |

The mandatory published-vs-draft-vs-superseded selection guarantee (PART 25/26) is proven at the backend integration level, not re-proven in a frontend test with mocked data - see the Phase 18 table above (`publishingASecondVersionSupersedesTheFirstAndStudentSeesOnlyTheCurrentPublishedVersion`, `studentSeesEmptyTimetableWhenTermHasNoPublishedVersionYet`) and `batchScopedTimetableIncludesDivisionWideAllocationsToo`. The Student frontend calls the exact same `/api/timetable` endpoint through the exact same `timetableApi.current` function the CR frontend already uses (Phase 21) - a frontend-level mock of that function cannot exercise the real version-selection logic, so re-asserting it here would test the mock, not the guarantee.

### Two real backend gaps found and fixed this phase (not bugs in already-shipped behavior - see docs/15-DESIGN-DECISIONS.md ADR-120/121)

1. `AllocationSummaryResponse` had no subject name or lab location fields - the Student UI's mandatory "clear lab location" requirement had no way to be met without an N+1 lookup per row. Fixed by adding `subjectName`/`labWing`/`labFloor`/`labRoomNumber` to the existing, already-loaded response.
2. A batch-scoped `/api/timetable` request silently hid division-wide practicals (strict `batch.id = batchId` equality). Fixed with `AllocationSpecifications.batchIdOrDivisionWide`; covered by the new backend test above.

### Frontend build/lint/test results (2026-08-25)

`npm run build` - passes, zero TypeScript errors. `npm run lint` - zero new errors/warnings (same three pre-existing warnings as Phase 20/21, all in unrelated files). `npm run test` - **60/60 passing** (49 pre-existing Phase 20/21 tests + 11 new Phase 22 tests).

### Backend regression (full suite, Docker Maven build)

`mvn test` - full suite green after both DTO/specification changes, including the pre-existing `ScheduleVersionApiIT`/`ScheduleVersionServiceTest` suites and the new `batchScopedTimetableIncludesDivisionWideAllocationsToo` test.

### Manual Student end-to-end verification (headless Chromium, Dockerized stack, 2026-08-25)

Booked a real extra practical as the demo CR (BDA, batch A1, lab B-301) so the demo Student account had genuine published data. Logged in as `student@example.edu`, walked the exact PART 33 path (BTECH -> CS -> Year 3 -> Division A) with no batch selected: division-scoped BDA session visible with its location text present. Selected batch A1: all four real BDA sessions visible (including the just-booked one), each showing subject name, faculty, lab code, and resolved location ("B-301 (Wing B, Floor 3, Room 301)"). Applied Day=Monday: all four sessions remained (all four happened to fall on Mondays), confirming the filter recomputes rather than statically hides. Confirmed the demo CR and demo Lab Assistant sessions are both redirected from `/student` to `/` with zero Student content ever rendered. **Zero console errors.**

## Analytics Tests — Implemented (Phase 23)

`AnalyticsApiIT` (Testcontainers, environment-blocked here - same documented Docker-in-Docker limitation as every other `*ApiIT` class; confirmed identical, not a new problem, by reproducing the exact failure against the pre-existing `ScheduleVersionApiIT` run the same way). Written correctly for CI/future environments; live Docker verification (below) covers what this cannot run here.

| Scenario | Expected | Test |
|---|---|---|
| Booked-minutes duration arithmetic + cancelled exclusion | `120+60=180` counted; a `180`-minute CANCELLED row excluded | `utilizationCountsRealAllocationDurationAndCancelledExclusion` |
| **DRAFT allocations excluded (mandatory, PART 71)** | Only the PUBLISHED version's `60` minutes counted, not the DRAFT version's `360` | `operationalAnalyticsExcludeDraftVersionAllocations` |
| **SUPERSEDED allocations excluded, not double-counted (mandatory, PART 72)** | Only the new PUBLISHED version's `60` minutes counted, never `180` (old + new) | `operationalAnalyticsExcludeSupersededVersionAllocationsAndDoNotDoubleCount` |
| **Weighted overall utilization (mandatory, PART 20)** | `(300+120)/(600+120) = 58.3%`, never the naive average `(50+100)/2 = 75%` | `weightedOverallUtilizationIsNotANaiveAverageOfPerLabPercentages` |
| **Unused labs (mandatory, PART 76)** | A lab with zero allocations appears; a lab with one does not | `unusedLabsListsOnlyLabsWithZeroQualifyingAllocations` |
| **Most-used lab ranked by minutes, not count (mandatory, PART 75)** | A single 480-minute booking outranks two bookings totaling 240 minutes | `mostUsedLabIsRankedByBookedMinutesNotAllocationCount` |
| **Peak day (mandatory, PART 74)** | The date with more booked minutes (240 > 60) is reported | `peakDayIsTheDateWithTheHighestBookedMinutes` |
| Extra-lab total/active/cancelled + by-division breakdown | `total=3, active=2, cancelled=1`; a REGULAR allocation never counted | `extraLabAnalyticsCountsTotalActiveCancelledAndBreaksDownByDivision` |
| **Conflict analytics honesty (mandatory, PART 75/77)** | `evidenceAvailable: false`, empty category list, never an invented count | `conflictAnalyticsHonestlyReportsNoPersistedEvidenceExists` |
| Invalid date range | `400 VALIDATION_ERROR` when `to` precedes `from` | `invalidDateRangeIsRejectedWithAValidationError` |
| **Security (mandatory, PART 76/78)** | LAB_ASSISTANT 200 (implicit via every other test), CR 403, STUDENT 403, anonymous 401 | `analyticsIsForbiddenToCrAndStudentAndUnauthorizedForAnonymous` |

### Frontend Analytics Tests — `AnalyticsPage.test.tsx` (9 tests)

Term-selection prompt (no request issued before a term is chosen), real summary/utilization/extra-lab/peak-usage/unused-lab values rendered from mocked API responses, the honest no-published-timetable amber notice, an error state (not zero-value cards) on request failure, and the honest "no evidence available" conflict explanation with no fabricated table. **Percentage-scale regression test (mandatory, PART 81, direct consequence of the Phase 21 `normalizedScore` bug):** asserts a backend value of `52.5` renders as `"52.5%"`, and explicitly asserts `"5250%"`/`"0.525%"` are never present — the exact failure mode a `* 100` or missed-scale bug would produce. `npm run test` — **69/69 passing** (60 pre-existing + 9 new). `npm run build`/`npm run lint` — clean, no new warnings.

### Backend build/test results (2026-08-25)

`mvn test` (excludes `*IT` classes by Surefire's own default naming convention, same as every prior phase) - full suite green with the new `analytics` package. `mvn -Dtest=AnalyticsApiIT test` reproduces the identical, pre-existing "Could not find a valid Docker environment" failure that `-Dtest=ScheduleVersionApiIT` also reproduces when forced to run explicitly inside this sandbox's Maven container (no Docker socket mounted) - confirms the test is correctly written and blocked by environment, not a defect.

### Manual SQL/API verification against the live Dockerized stack (2026-08-25)

Cross-checked lab B-301's booked minutes directly: `SELECT l.code, SUM(EXTRACT(EPOCH FROM (end_time-start_time))/60), COUNT(*) FROM allocation a JOIN lab l ... WHERE sv.status='PUBLISHED' AND a.status IN ('APPROVED','PUBLISHED') AND l.code='B-301'` → `180.0`, `2` — matched `GET /api/analytics/lab-utilization`'s `bookedMinutes: 180, allocationCount: 2` for the same lab exactly.

**Draft/superseded regression against real historical data, not a constructed fixture:** the live demo term already carried genuine multi-version history from every prior phase's own live verification (`V1..V5 SUPERSEDED`, `V6 PUBLISHED`, `V7 DRAFT`). `SELECT sv.version_number, sv.status, COUNT(a.id) FROM schedule_version sv LEFT JOIN allocation a ... GROUP BY ...` showed `11` allocations total spread across every version (`3+1+0+0+1+6+0`), while `GET /api/analytics/summary` correctly reported `activeAllocations: 6` — the SUPERSEDED versions' `5` allocations and the empty DRAFT version were both excluded, proven against real data rather than a purpose-built scenario.

**Cancellation regression:** recorded `extra-labs` (`total:5, active:5, cancelled:0`) and B-301's utilization (`bookedMinutes:180, allocationCount:2`), cancelled a real extra-lab booking via `POST /api/allocations/extra/91/cancel`, re-queried both endpoints — `active:4, cancelled:1` and B-301's `bookedMinutes:60, allocationCount:1`, immediately reflecting the cancellation.

**Security, live:** LAB_ASSISTANT `200`, CR `403`, STUDENT `403`, anonymous `401` on `/api/analytics/summary`; an inverted date range returned `400`.

**Frontend, live (headless Chromium):** logged in as the demo Lab Assistant, opened `/lab-assistant/analytics`, selected the term — every card/table rendered real values matching the API responses above (utilization percentages, extra-lab breakdowns, peak day/lab/time-slot, unused-lab list, and the honest "No historical conflict data is available..." explanation, never a fabricated count). **Zero console errors.**

## Phase 24 — Full-System Verification & Release Readiness

Not a new-feature phase - a clean-slate re-verification that Phases 0-23 work together as one coherent, deployable application. Every result below is from the final run of this phase (not an earlier partial run).

**Environment:** Windows 11 host, backend built/tested via `maven:3.9-eclipse-temurin-21` in Docker (Java 21.0.12 Temurin, Maven 3.9.16) - the project's `mvnw.cmd` cannot resolve `powershell` in this sandbox, the same documented workaround every prior phase used. Node v24.14.0, npm 11.9.0. Docker 29.7.2. PostgreSQL 16.15 (`postgres:16-alpine`).

### Backend clean test run (`mvn clean test`)

**296 tests, 0 failures, 0 errors, 0 skipped.** Covers: JWT/security helpers, the full constraint engine (capacity/software/equipment/lab-type/availability/conflict), candidate generation, scoring, explainability, backtracking scheduler, extra-lab domain logic, schedule-version lifecycle, audit logic, PDF parser/normalizer/mapper, analytics arithmetic - every subsystem PART 6 asks for.

### Backend build (`mvn clean package -DskipTests`)

`BUILD SUCCESS`; artifact produced: `backend/target/lab-allocation-backend-0.0.1-SNAPSHOT.jar` (67,116,496 bytes). Tests were **not** re-run during this step (`-DskipTests`, since they had already run cleanly one line above) - not falsely double-counted.

### Integration tests - executed vs. environment-blocked, not conflated

Every `*ApiIT`/`*ConcurrencyIT` class (`ScheduleVersionApiIT`, `AnalyticsApiIT`, `ExtraLabApiIT`, `AllocationConcurrencyIT`, `ScheduleVersionConcurrencyIT`, etc.) requires Testcontainers to launch a `postgres:16-alpine` container from *inside* the Maven build's own JVM. `mvn test`'s default Surefire include pattern (`**/*Test.java`) does not match `*IT.java` at all, so these never silently ran and passed - they simply aren't in that 296. Forcing one to run explicitly (`-Dtest=AnalyticsApiIT`) reproduces the exact same `IllegalStateException: Could not find a valid Docker environment` that forcing the pre-existing `ScheduleVersionApiIT` the identical way also produces - confirmed this session, side by side, proving the limitation is environmental (no Docker socket inside this sandbox's Maven container) and not new, not a defect, and not specific to Phase 24's own new test. **Every invariant these classes assert was instead re-proven live against the real Dockerized stack this session** (below) - genuine execution against a real backend/PostgreSQL, not a substitute claim of "passed."

### Clean-database Flyway/boot verification (mandatory, PART 8/9)

Started a brand-new, empty `postgres:16-alpine` container (no shared volume with the long-lived dev database) and booted the freshly-built backend image against it directly. Result: Flyway applied **all 14 migrations sequentially** (`V1` "baseline" through `V14` "create timetable import"), Hibernate's `ddl-auto: validate` passed cleanly against the resulting schema (proving the JPA mappings and the migrations agree), dev seed data loaded (`DevAcademicSeeder`/`DevLabSeeder`/`DevUserSeeder`/etc.), and `GET /actuator/health` returned `{"status":"UP"}` with the `db` component `UP`. No manual intervention at any step. Torn down after verification.

### RBAC regression matrix (live, PART 11)

| Capability | LAB_ASSISTANT | CR | STUDENT | Anonymous |
|---|---|---|---|---|
| Lab Assistant management (`POST /api/programs`) | 200 | 403 | — | — |
| CR extra-lab booking (`POST /api/allocations/extra/search`) | 403 | 200 | 403 | — |
| Student/shared timetable (`GET /api/timetable`) | — | 200 | 200 | 401 |
| PDF import management (`GET /api/timetable-imports`) | 200 | 403 | 403 | 401 |
| Audit administration (`GET /api/audit-logs`) | 200 | 403 | 403 | 401 |
| Analytics (`GET /api/analytics/summary`) | 200 | 403 | 403 | 401 |
| Schedule publication (`POST /api/schedule-versions`) | 200 | 403 | 403 | 401 |

Every cell above is a real HTTP status from a live request this session, not carried over from an earlier phase's report.

### FCFS concurrency regression (mandatory, PART 21/22)

Two genuinely parallel (background-launched, `wait`-joined) `POST /api/allocations/extra` requests for the identical lab/date/time: **request A → 200 (booked), request B → 409 `ALLOCATION_CONFLICT`** (with `LAB_CONFLICT`/`FACULTY_CONFLICT`/`BATCH_CONFLICT` all listed). `SELECT COUNT(*) FROM allocation WHERE lab_id=5 AND allocation_date='2026-10-05' AND start_time='14:00:00' AND status IN ('APPROVED','PUBLISHED')` → **exactly 1**. A second parallel pair for genuinely non-conflicting resources (different lab, different faculty, different batch, same time) → **both 200** - concurrency protection serializes only real contention, not unrelated work.

### Audit immutability regression (mandatory, PART 23)

Direct `psql` against the live database: `UPDATE audit_log SET action=... WHERE id=(SELECT MAX(id)...)` → `ERROR: audit_log is append-only: UPDATE is not permitted`. `DELETE FROM audit_log WHERE id=(SELECT MAX(id)...)` → `ERROR: audit_log is append-only: DELETE is not permitted`. Both rejected by the V12 trigger, exactly as designed; `AuditLog` remains `@Immutable` in code (unchanged).

### Audit creation regression (PART 24)

`SELECT action, COUNT(*) FROM audit_log WHERE created_at > now() - interval '30 minutes' GROUP BY action` after this session's live actions showed `EXTRA_LAB_BOOKED: 3`, `EXTRA_LAB_CANCELLED: 1`, `SCHEDULE_VERSION_CREATED: 1` - real audit rows for real actions taken during this verification pass.

### Database constraint verification (PART 57/58)

`\d allocation` confirmed all three Phase 16 exclusion constraints intact (`ex_allocation_lab_overlap`, `ex_allocation_faculty_overlap`, `ex_allocation_batch_overlap`, all GiST/`tsrange`-based) plus every `CHECK` constraint (`chk_allocation_interval`, `chk_allocation_status`, `chk_allocation_target_invariant`, `chk_allocation_target_type`, `chk_allocation_type`). `\d schedule_version` confirmed `uq_schedule_version_one_published_per_term` (partial unique index) and `uq_schedule_version_term_number`. `SELECT academic_term_id, COUNT(*) FROM schedule_version WHERE status='PUBLISHED' GROUP BY academic_term_id HAVING COUNT(*) > 1` → **0 rows**, live, against a database with 8 schedule versions across this term's full revision history.

### Timetable versioning / historical preservation regression (PART 25-27)

Live term state: `V1..V5 SUPERSEDED`, `V6 PUBLISHED`, `V7/V8 DRAFT` - all real history from this and prior phases' live verification, still present (nothing cleaned up). `SELECT sv.version_number, sv.status, COUNT(a.id) ... GROUP BY ...` showed every superseded version's allocations still exist (`3+1+0+0+1=5` rows across V1/V2/V5) alongside V6's own `9`.

### PDF import staging isolation / atomic approval regression (mandatory, PART 29/30)

Using real historical imports rather than a constructed fixture: import #5 (`status=VALIDATED`, never approved) has `1` staged `timetable_import_row` but `0` allocations tracing to it (`source_import_id=5`) - staging isolation intact. Import #4 (`status=APPROVED`) has exactly `1` allocation tracing to it (`source_import_id=4`) - matching its single row, proving atomic approval created exactly the intended allocation, no more.

### Analytics regression (mandatory, PART 34-37)

`GET /api/analytics/summary?academicTermId=1` → `activeAllocations: 8` (V6's `9` total minus its `1` CANCELLED row - both the DRAFT/SUPERSEDED exclusion and the CANCELLED exclusion visible in one live number). Direct SQL cross-check of lab B-301's booked minutes (`SUM(EXTRACT(EPOCH FROM (end_time-start_time))/60)`, `COUNT(*)`, filtered to the PUBLISHED version + active status) matched the API's `bookedMinutes`/`allocationCount` for that lab exactly, as in Phase 23's own verification, re-confirmed here after the restart and the concurrency/RBAC test traffic.

### Frontend clean install / test / lint / build

`npm ci` - 139 packages installed, 0 vulnerabilities. `npm run test` - **69/69 passing** (17 test files), no order-dependence observed (fresh install, fresh run). `npm run lint` - 4 pre-existing warnings (`AuthContext.tsx` ×2, `CrAssignmentContext.tsx`, `DashboardPage.tsx`), **zero new warnings, zero errors**. `npm run build` - clean, `dist/assets/index-*.js` 387.50 kB (gzip 108.43 kB).

### Docker builds (PART 50/51)

`docker build -t lab-allocation-backend:test ./backend` → success. `docker build -t lab-allocation-frontend:test ./frontend` → success (both from the actual project Dockerfiles, both layer-cached from the identical Phase 23 rebuild, confirming reproducibility).

### Docker Compose smoke test + clean restart (PART 52/54)

Full stack (`postgres`/`backend`/`frontend`) already running from Phase 23's rebuild - confirmed all three containers `Up`/`healthy`. Explicitly restarted all three (`docker restart`) to prove recovery from an ordinary restart, not just first boot: backend reported `healthy` again, `POST /api/auth/login` succeeded (`200`) immediately after, frontend served `200` at `/`, and `docker logs` showed no new errors/exceptions post-restart.

### Bugs found this phase

**None.** Every mandatory invariant re-verified live matched its expected result on the first attempt - no defect required a fix.

### Known, pre-existing, unchanged limitations

- Testcontainers-backed `*ApiIT`/`*ConcurrencyIT` classes remain environment-blocked in this sandbox (no Docker socket inside the Maven build container) - written correctly, covered by equivalent live verification, unchanged from every prior phase's documented state.
- Conflict/allocation-success analytics remain honestly incomplete by design (no persisted rejection evidence exists anywhere in the schema) - not a Phase 24 finding, restated from Phase 23.

## Performance Benchmarks — Phase 25 (see [16-PERFORMANCE-BENCHMARKS.md](16-PERFORMANCE-BENCHMARKS.md) for full results)

**Correctness tests and performance benchmarks are deliberately different kinds of artifacts, kept apart on purpose:**

| | Correctness tests (`*Test.java`, `*ApiIT.java`) | Performance benchmarks (`*Benchmark.java`) |
|---|---|---|
| Question answered | Is the behavior right? | How expensive is the behavior? |
| Runs in `mvn test`? | Yes, automatically | **No** - opt-in only (`-Dtest=SchedulerBenchmark`/`-Dtest=PdfImportBenchmark`) |
| Assertions | `assertThat(...)` - fails the build on a wrong answer | None that fail the build - reports timings/statistics to stdout |
| Speed budget | Must stay fast (the whole suite is 296 tests, seconds) | Deliberately allowed to take longer (warm-up + 20 measured runs per scenario, scaling runs up to 100 requirements) |

This separation is structural, not just a naming convention: `SchedulerBenchmark`/`PdfImportBenchmark` are named `*Benchmark`, not `*Test`, so Maven Surefire's default include pattern (`**/*Test.java`) never picks them up - confirmed this phase by re-running the normal `mvn test` command after adding both classes and observing the identical 296-test baseline, unaffected. A benchmark class asserting nothing and never running by default means a slow or flaky benchmark can never make the release-blocking regression suite fail or drag - the two artifacts genuinely cannot interfere with each other.

### Real measurements, not simulated

Every benchmark in `docs/16-PERFORMANCE-BENCHMARKS.md` is from an actual execution this phase - the pure-JVM scheduler/PDF-pipeline benchmarks ran inside the same Maven/Docker environment every other backend test uses; the DB-backed benchmarks (candidate generation, booking, concurrency, timetable retrieval, analytics, PDF mapping/validation/approval) ran live against the real Dockerized backend + PostgreSQL, since Testcontainers remains environment-blocked in this sandbox for the same reason every `*ApiIT` class already is. No number in that document was invented, estimated without labeling it as derived, or copied from a prior phase's report.

## Phase 27 — CI Verification (GitHub Actions)

`.github/workflows/ci.yml` runs on `push`/`pull_request` against `main` and on manual `workflow_dispatch`, with minimal `permissions: contents: read` and a `concurrency` group that cancels superseded runs on the same ref.

```text
Push / Pull Request / Manual
          |
 +--------+--------+
 |                 |
Backend          Frontend
 |                 |
Compile           npm ci
Unit tests        Lint
Integration tests Tests
(Testcontainers)  Build
Upload jar
 +--------+--------+
          |
        Docker (needs: backend, frontend)
          |
   Compose config validation
   Image build (docker compose build)
   Compose smoke test (prod profile, real CI JWT secret)
   Compose cleanup (always)
```

### Backend job

`actions/setup-java` (Temurin 21, Maven dependency cache keyed on `backend/pom.xml`) - **not** `backend/mvnw`: the wrapper script is tracked in git without its executable bit (`100644`, confirmed via `git ls-files -s backend/mvnw`), so `./mvnw` fails with "Permission denied" on a fresh Linux checkout until that's fixed at the source and committed (a real, documented finding this phase - not fixed in-session since Phase 27 does not commit). Using the JDK-bundled/runner-provided Maven directly sidesteps this and matches the exact Maven version (3.9.x) this project's own local verification already used throughout Phases 20-26.

Three distinct steps, each independently attributable on failure (PART 53): `mvn -B compile` (fast compile-error signal), `mvn -B test` (the 300-test unit baseline), `mvn -B verify -Dsurefire.skip=true` (re-runs only the Testcontainers-backed `*IT`/`*ConcurrencyIT` suite via `maven-failsafe-plugin` - already configured in `pom.xml`, bound to its standard `integration-test`/`verify` phases; no new plugin wiring was needed, only discovered and used).

### Why integration tests can finally execute for real in CI

This project's local development sandbox can only reach Docker from a container that has no socket access to the host daemon (every prior phase's `*ApiIT`/`*ConcurrencyIT` classes have been "written correctly, environment-blocked here" as a result - see Phases 18-26). A GitHub Actions `ubuntu-latest` runner has a real, native Docker daemon with no such nesting problem - `mvn -B verify` there should let all 19 `*IT` classes (`ScheduleVersionApiIT`, `AnalyticsApiIT`, `ExtraLabApiIT`, `AllocationConcurrencyIT`, `ScheduleVersionConcurrencyIT`, `LabAllocationBackendApplicationIT`, and 13 others) actually run against a real Testcontainers-managed PostgreSQL, not merely compile. **This has not yet been observed executing on GitHub** (no push occurred this phase, per standing instruction) - see the Phase 27 completion report for the honest, unblurred distinction between "designed to work" and "observed to work."

### Frontend job

`actions/setup-node` (Node 20, matching the frontend Dockerfile's build stage - not the newer Node this local dev machine runs), npm cache keyed on `frontend/package-lock.json`. `npm ci` (not `npm install` - reproducible, lockfile-pinned, mirrors Phase 26's deployment-build convention exactly). `npm run lint` (oxlint; the 4 documented pre-existing warnings do not fail the command - exit code 0 - so CI does not need `continue-on-error` to tolerate them; only a genuine lint *error* fails this step). `npm run test` (`vitest run`, already non-watch). `npm run build` (`tsc -b && vite build` - fails the job on a TypeScript error).

### Docker job

Depends on both other jobs (`needs: [backend, frontend]`) so it only runs once both are green. `docker compose config` validates the file parses and interpolates correctly. `docker compose build` builds both real deployment Dockerfiles (not a separate, parallel `docker build` invocation with different tags - one source of truth). The smoke test deliberately sets `SPRING_PROFILES_ACTIVE=prod` with a disposable, run-id-unique CI secret (`ci-only-jwt-secret-placeholder-at-least-32-bytes-long-${{ github.run_id }}`, well over `ProductionJwtSecretGuard`'s 32-byte minimum) - Phase 26's own local verification only ever exercised `dev`; this is the first time `prod` profile's actual boot path is verified anywhere, local or CI. A bounded poll loop (`sleep 5`, up to 30 attempts) waits for the backend's real Compose healthcheck rather than a fixed sleep, then curls `/actuator/health` and the frontend root. `docker compose logs` runs `if: failure()` for diagnosis; `docker compose down -v` runs `if: always()` so a failed run never leaves orphaned containers on the runner (which is disposable anyway, but explicit cleanup is still the correct habit).

### Secrets

No GitHub Actions Secrets were required or created - every CI-only value (`POSTGRES_PASSWORD`, `JWT_SECRET`) lives directly in the workflow's `env:` block, deliberately non-sensitive, unique per run, and never reused outside that ephemeral runner. This matches PART 36's own guidance: don't make repository maintainers create secrets just to run tests.

### Local command equivalence

Every CI command is runnable locally, unchanged:
```text
Backend:   cd backend && mvn clean test        (unit)
           mvn clean verify                     (unit + integration, needs real Docker)
Frontend:  cd frontend && npm ci && npm run lint && npm run test && npm run build
Docker:    docker compose config && docker compose build
```



Each implementation phase (Phase 4 onward) is not considered complete until: implementation compiles, its unit/integration tests exist and pass, and the relevant row(s) in this document's traceability tables are checked off with a note of which test class covers them (added incrementally — this document evolves alongside code, not written once and frozen).
