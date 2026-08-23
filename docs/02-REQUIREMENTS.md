# Requirements

Requirement IDs are stable identifiers for traceability to tests (unit/integration test names should reference the FR/NFR/HC ID they cover where practical). Hard scheduling constraints (HC-01..HC-12) are specified and, as of Phase 9, fully implemented in [06-CONSTRAINTS.md](06-CONSTRAINTS.md); they are cross-referenced here.

**Status:** Requirements marked ✅ are implemented and verified as of Phase 12. Requirements marked **(partial)** have their underlying hard-constraint logic, candidate generation, scoring, and/or explanation implemented and verified (the `ConstraintEngine`, Phase 9; `CandidateGenerator`, Phase 10; `ScoringEngine`, Phase 11; `ExplainableAllocationService`, Phase 12) but are not yet reachable through any end-user workflow — the CR-facing booking flow (Phase 15) and PDF import (Phase 19) that will actually call it don't exist yet. Everything else remains planned. Conflict *alternative* search (different lab and/or different time when nothing valid exists at the requested time) is explicitly **not** part of Phase 12 — see FR-23, still Phase 13.

## Functional Requirements

### Identity & Access
- **FR-01** ✅ Lab Assistant can create CR accounts. *(Phase 3: account creation via `AppUser`; Phase 4: `POST /api/cr-assignments` links an existing CR-role account to a division — "creating a CR **account**" specifically, distinct from assignment, remains the existing user-management surface, since Phase 4 explicitly used existing demo CR users rather than adding a public/admin CR-account-creation endpoint — see docs/15-DESIGN-DECISIONS.md.)*
- **FR-02** ✅ Lab Assistant can assign a CR to exactly one division, and can change/deactivate that assignment. *(`CrAssignmentService.create`/`.end`, Phase 4 — reassignment ends the prior active row and preserves it as history.)*
- **FR-03** ✅ A CR can act only within their currently assigned division; the backend resolves division from the authenticated user's identity, never from a client-supplied value. *(`CrOwnershipService.requireOwnsDivision`, Phase 4 — verified in `CrOwnershipServiceTest` and end-to-end in `AcademicApiIT`.)*
- **FR-04** Student accounts are read-only; students can never mutate scheduling data. *(Enforced generally by RBAC since Phase 3/4 — no student-mutable endpoint exists anywhere; no scheduling data exists yet to more specifically test against.)*
- **FR-05** ✅ Faculty have no login identity; they exist purely as a scheduling/domain entity maintained by the Lab Assistant. *(`Faculty` entity, Phase 4 — no `app_user` row, no password field, ADR-006.)*

### Academic Structure
- **FR-06** ✅ Lab Assistant can manage the academic hierarchy (Program, Stream, Academic Year, Division, Batch) with a configurable number of divisions per year and batches per division. *(Phase 4 — full CRUD via `/api/programs`, `/api/streams`, `/api/academic-years`, `/api/divisions`, `/api/batches`; no hardcoded counts anywhere, verified with the seeded 4-stream/4-year B.Tech + 2-stream/3-year MBA Tech hierarchy.)*
- **FR-07** ✅ Lab Assistant can manage Subjects *(Phase 4 — `/api/subjects`)* and their software/equipment/lab-type requirements *(Phase 6 — `/api/subjects/{id}/requirements`, `/software-requirements`, `/equipment-requirements`, `/lab-type-requirement`; verified with BDA requiring Cloudera + preferring the Data Engineering lab type, and CNS carrying zero requirements to prove the optional path)*. Matching these requirements against lab capabilities is now implemented and verified — `RequiredSoftwareConstraint`/`RequiredEquipmentConstraint`/`RequiredLabTypeConstraint` (HC-08/09/10, Phase 9) — but not yet reachable through any end-user workflow.
- **FR-08** ✅ Lab Assistant can manage Faculty records *(Phase 4 — `/api/faculty`)* and their weekly, term-scoped availability windows *(Phase 7 — `/api/faculty/{id}/availability*`; verified with Faculty BDA/CNS's seeded Monday/Tuesday/Wednesday windows, including a deliberate Monday 12:00-14:00 gap in BDA's schedule proving "not available" is correctly computed, not just "available"). Availability is a hard boundary (HC-03), now actually enforced by `FacultyAvailabilityConstraint` (Phase 9, verified live in Docker) — again not yet reachable through any end-user workflow.

### Lab Inventory
- **FR-09** ✅ Lab Assistant can manage Labs (code, name, capacity, wing, floor, room number, lab type, active flag). *(Phase 5 — `/api/labs`, `/api/lab-types`; verified with 15 seeded labs across wings B/C/D.)*
- **FR-10** ✅ Lab Assistant can manage Software and Equipment catalogs and which labs have which software/equipment installed. *(Phase 5 — `/api/software`, `/api/equipment`, `/api/labs/{id}/software`, `/api/labs/{id}/equipment`; static ALL-match capability filtering verified against seeded Cloudera data.)*
- **FR-11** ✅ Lab Assistant can mark a lab unavailable for a period (maintenance) via `LabUnavailability`. *(Phase 5 — `/api/labs/{id}/unavailability`; interval validation verified. Scheduling-conflict combination now implemented — `LabAvailabilityConstraint` (HC-06, Phase 9), verified live with a real unavailability window.)*

### Regular Timetable (PDF Import)
- **FR-12** Lab Assistant can upload a timetable PDF.
- **FR-13** The system extracts and normalizes timetable entries from the PDF into a reviewable, editable form (`TimetableImport` / `TimetableImportEntry`) — extraction never writes directly to live allocations.
- **FR-14** Lab Assistant must review, and may correct, every imported entry before approval.
- **FR-15** Only after Lab Assistant approval does an imported timetable become a set of real `Allocation` records; approval then requires an explicit publish step before students can see it.
- **FR-16** The system detects conflicts (per HC-01..HC-12) among imported entries and against existing allocations during review, before approval is possible. *(The underlying HC-01..HC-12 evaluation itself is now fully implemented and verified — `ConstraintEngine`, Phase 9 — ready for this workflow to call once PDF import exists; the import workflow itself is Phase 19.)*

### Extra / Makeup Lab Booking (CR Workflow)
- **FR-17** *(partial)* ✅ A CR can search for candidate labs for a subject/batch/date/time, restricted to their own division, ranked by preference and with a structured explanation of why each ranked or was rejected. *(Candidate generation — `CandidateGenerator`, Phase 10 — is implemented and verified: every lab in the system is generated and evaluated for a given `SchedulingRequest`. Ranking valid candidates — `ScoringEngine`, Phase 11 — is implemented and verified: Capacity Fit, Preferred Lab Type, and Balanced Utilization (docs/07-ALLOCATION-SCORING.md). Explaining the recommendation and every rejection — `ExplainableAllocationService`, Phase 12 — is now also implemented and verified, producing a structured `AllocationRecommendation`. Division-restriction to "their own division" is HC-11's job (Phase 9, applicability-gated on the actor), already implemented; only the CR-facing search endpoint itself does not yet exist — Phase 15.)*
- **FR-18** *(partial)* ✅ The system filters candidate labs by the subject's software/equipment/lab-type requirements before further validation. *(The underlying logic — HC-08/09/10, Phase 9 — is now demonstrably wired into candidate generation: `CandidateGenerator` (Phase 10) evaluates every lab through the real `ConstraintEngine`, verified live with the actual BDA/Cloudera demo. "Filters" here means "correctly rejects with an explainable violation," not "silently excludes" — see docs/05-SCHEDULING-ENGINE.md for why prefiltering was deliberately rejected.)*
- **FR-19** *(partial)* ✅ The system prevents faculty conflicts: a faculty member cannot be assigned two overlapping sessions. *(`FacultyConflictConstraint`, HC-02, Phase 9 — implemented and verified, both unit and live in Docker; not yet reachable through the booking workflow itself.)*
- **FR-20** *(partial)* ✅ The system prevents lab conflicts: a lab cannot host two overlapping sessions. *(`LabConflictConstraint`, HC-01, Phase 9 — same status as FR-19.)*
- **FR-21** *(partial)* ✅ The system considers faculty availability windows; a session outside a faculty's declared availability is rejected. *(`FacultyAvailabilityConstraint`, HC-03, Phase 9.)*
- **FR-22** *(partial)* ✅ The system distinguishes batch-level occupancy from division-wide occupancy: two different batches of the same division may have simultaneous sessions if their labs and faculty differ. *(`BatchConflictConstraint`/`DivisionWideConflictConstraint`, HC-04/05, Phase 9 — the project's signature scenario, verified three ways: unit test, engine-level test with all five relevant HC results individually asserted, and live against the real seeded BDA/CNS demo in Docker.)*
- **FR-23** When a requested slot is invalid, the system provides ranked alternative allocations (different lab, different time, or both) rather than a bare rejection. *(Alternative search is Phase 13 — not yet implemented; `ConstraintEngine` (Phase 9) already reports every reason a candidate is invalid, which Phase 13 will build on, but it does not itself search for alternatives.)*
- **FR-24** Extra-lab requests are served First-Come-First-Served among valid requests; FCFS ordering never overrides a hard constraint. *(Phase 16 — not yet implemented.)*
- **FR-25** A CR can cancel only extra allocations belonging to their own division; cancellation sets status to `CANCELLED` and records who/when/why — it never deletes the row. *(The `Allocation.cancel(...)` lifecycle method exists (Phase 8) and is unit-tested; the CR-facing cancellation workflow itself is Phase 15 — not yet implemented.)*
- **FR-26** A CR cannot modify, schedule, or cancel allocations for a division other than their own, even if they supply a different division ID in the request (enforced server-side). *(partial)* ✅ The underlying check — `CrAuthorizationConstraint` (HC-11, Phase 9), applicable only when a `SchedulingActor` with role CR originates the request — is implemented and verified, both unit and live in Docker; not yet reachable through any end-user workflow.

### Viewing
- **FR-27** Student can view only the currently *published* schedule version, filterable by program, stream, year, division, batch.
- **FR-28** CR can view their division's full schedule (regular + extra) and cancelled-session history.
- **FR-29** Lab Assistant can view all schedules, all detected conflicts, suggested alternatives, and CR activity across all divisions.

### Automatic Scheduling
- **FR-30** The system can generate a full multi-session schedule automatically from a set of unscheduled session requirements, using most-constrained-first ordering and backtracking when a session has no valid candidate under the current partial assignment.
- **FR-31** The system reports which sessions could not be scheduled when no full solution is found within the configured search budget.

### Auditability
- **FR-32** The system records an immutable audit log entry for every consequential action (CR created/assigned, extra lab created/cancelled, allocation approved/rejected, lab/faculty-availability/software-requirement changes, PDF import/approval).
- **FR-33** Lab Assistant can view CR activity and the full audit log.

### Analytics
- **FR-34** Lab Assistant can view lab utilization and scheduling analytics computed from real allocation data (never fabricated/placeholder numbers).

## Non-Functional Requirements

- **NFR-01 (Security)** All state-changing endpoints require authentication; authorization (role + ownership) is enforced server-side regardless of what the frontend sends or hides.
- **NFR-02 (Concurrency)** Two concurrent requests for the same lab/time slot must never both succeed; the database is the final arbiter, not application-level pre-checks.
- **NFR-03 (Performance)** Single-allocation candidate generation + scoring for a realistic dataset (≈15 labs) should complete fast enough for interactive use (target documented with real measurements in [16-PERFORMANCE-BENCHMARKS.md](16-PERFORMANCE-BENCHMARKS.md) once formally benchmarked — no target number is asserted here until measured; Phase 11 observed, informally, a full generate-then-score run completing well under a second for the 15-lab dev dataset).
- **NFR-04 (Scalability)** The academic hierarchy, lab count, and batch count must be data-driven; no code change should be required to add a program, stream, division, batch, or lab.
- **NFR-05 (Maintainability)** ✅ Each hard constraint is an independently implemented, independently testable unit (`SchedulingConstraint` implementation) rather than embedded in a monolithic conditional. *(Phase 9 — twelve `@Component` classes, one test class each.)*
- **NFR-06 (Reliability)** A cancelled or rejected allocation is never physically deleted; history is preserved via status transitions.
- **NFR-07 (Auditability)** Every consequential mutation produces an audit record sufficient to answer "who did what, when, to which resource."
- **NFR-08 (Testability)** ✅ Scheduling domain objects (candidate, context, constraint result, score breakdown, recommendation/explanation) are decoupled from JPA entities and HTTP controllers so they can be unit tested without a database or web layer. *(Phase 8/9/11/12 — verified: none of `SchedulingRequest`/`SchedulingContext`/`CandidateAllocation`/`ConstraintResult`/`ConstraintViolation`/`ConstraintEvaluation`/`ScoreContribution`/`ScoredCandidate`/`AllocationRecommendation`/`ExplainedValidCandidate`/`RejectedCandidateExplanation` carries a JPA annotation; the large majority of this project's unit tests construct these directly with no Spring context.)*
- **NFR-09 (Usability)** ✅ Every recommended or rejected allocation candidate is explainable in structured, non-recomputed detail, not just a status code. *(`ExplainableAllocationService`, Phase 12 — implemented and verified: a recommended candidate carries its exact Phase 11 score breakdown plus a display-labeled constraint-check summary; a rejected candidate carries every `ConstraintViolation` it failed, converted to a display-labeled `ViolationExplanation`, never collapsed to the first reason. "Plain language" here means structured explanation objects with both a machine code and a human display label — not free-form NLG/LLM text, deliberately, for determinism (docs/05-SCHEDULING-ENGINE.md). Not yet reachable through any end-user UI — Phase 15/18.)*
- **NFR-10 (Data Integrity)** ✅ Time-overlap logic uses proper half-open interval comparison (`startA < endB AND startB < endA`), never naive equality checks. *(`TimeIntervalUtils`, Phase 7, extended Phase 9 with an `Instant` overload for HC-06 — reused by every conflict-related constraint, never reimplemented per-constraint.)*

## Role Summary (detail in [09-AUTHORIZATION-RBAC.md](09-AUTHORIZATION-RBAC.md), created in Phase 3)

| Capability | Lab Assistant | CR | Student |
|---|:---:|:---:|:---:|
| Manage labs/software/equipment/subjects/faculty | ✅ | ❌ | ❌ |
| Manage faculty availability | ✅ | ❌ | ❌ |
| Manage academic hierarchy | ✅ | ❌ | ❌ |
| Create/assign CR accounts | ✅ | ❌ | ❌ |
| Upload/review/approve PDF imports | ✅ | ❌ | ❌ |
| View all schedules & conflicts | ✅ | own division only | published only |
| Schedule extra lab | ❌ | own division only | ❌ |
| Cancel extra lab | ❌ | own division's, extra only | ❌ |
| View audit logs / CR activity | ✅ | ❌ | ❌ |
| View analytics | ✅ | ❌ | ❌ |
