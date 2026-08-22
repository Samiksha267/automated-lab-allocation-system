# Requirements

Requirement IDs are stable identifiers for traceability to tests (unit/integration test names should reference the FR/NFR/HC ID they cover where practical). Hard scheduling constraints (HC-01..HC-12) are specified separately in [06-CONSTRAINTS.md](06-CONSTRAINTS.md) once that file is created (Phase 9); they are cross-referenced here.

**Status:** Requirements marked ✅ are implemented and verified as of Phase 6. Everything else remains planned.

## Functional Requirements

### Identity & Access
- **FR-01** ✅ Lab Assistant can create CR accounts. *(Phase 3: account creation via `AppUser`; Phase 4: `POST /api/cr-assignments` links an existing CR-role account to a division — "creating a CR **account**" specifically, distinct from assignment, remains the existing user-management surface, since Phase 4 explicitly used existing demo CR users rather than adding a public/admin CR-account-creation endpoint — see docs/15-DESIGN-DECISIONS.md.)*
- **FR-02** ✅ Lab Assistant can assign a CR to exactly one division, and can change/deactivate that assignment. *(`CrAssignmentService.create`/`.end`, Phase 4 — reassignment ends the prior active row and preserves it as history.)*
- **FR-03** ✅ A CR can act only within their currently assigned division; the backend resolves division from the authenticated user's identity, never from a client-supplied value. *(`CrOwnershipService.requireOwnsDivision`, Phase 4 — verified in `CrOwnershipServiceTest` and end-to-end in `AcademicApiIT`.)*
- **FR-04** Student accounts are read-only; students can never mutate scheduling data. *(Enforced generally by RBAC since Phase 3/4 — no student-mutable endpoint exists anywhere; no scheduling data exists yet to more specifically test against.)*
- **FR-05** ✅ Faculty have no login identity; they exist purely as a scheduling/domain entity maintained by the Lab Assistant. *(`Faculty` entity, Phase 4 — no `app_user` row, no password field, ADR-006.)*

### Academic Structure
- **FR-06** ✅ Lab Assistant can manage the academic hierarchy (Program, Stream, Academic Year, Division, Batch) with a configurable number of divisions per year and batches per division. *(Phase 4 — full CRUD via `/api/programs`, `/api/streams`, `/api/academic-years`, `/api/divisions`, `/api/batches`; no hardcoded counts anywhere, verified with the seeded 4-stream/4-year B.Tech + 2-stream/3-year MBA Tech hierarchy.)*
- **FR-07** ✅ Lab Assistant can manage Subjects *(Phase 4 — `/api/subjects`)* and their software/equipment/lab-type requirements *(Phase 6 — `/api/subjects/{id}/requirements`, `/software-requirements`, `/equipment-requirements`, `/lab-type-requirement`; verified with BDA requiring Cloudera + preferring the Data Engineering lab type, and CNS carrying zero requirements to prove the optional path)*. Matching these requirements against lab capabilities is not yet implemented — that is the Constraint Engine, Phase 9.
- **FR-08 (partial)** ✅ Lab Assistant can manage Faculty records *(Phase 4 — `/api/faculty`)*. Faculty Availability windows are **not yet implemented** (Phase 7).

### Lab Inventory
- **FR-09** ✅ Lab Assistant can manage Labs (code, name, capacity, wing, floor, room number, lab type, active flag). *(Phase 5 — `/api/labs`, `/api/lab-types`; verified with 15 seeded labs across wings B/C/D.)*
- **FR-10** ✅ Lab Assistant can manage Software and Equipment catalogs and which labs have which software/equipment installed. *(Phase 5 — `/api/software`, `/api/equipment`, `/api/labs/{id}/software`, `/api/labs/{id}/equipment`; static ALL-match capability filtering verified against seeded Cloudera data.)*
- **FR-11** ✅ Lab Assistant can mark a lab unavailable for a period (maintenance) via `LabUnavailability`. *(Phase 5 — `/api/labs/{id}/unavailability`; interval validation verified, no scheduling-conflict combination yet — that's HC-06, Phase 9.)*

### Regular Timetable (PDF Import)
- **FR-12** Lab Assistant can upload a timetable PDF.
- **FR-13** The system extracts and normalizes timetable entries from the PDF into a reviewable, editable form (`TimetableImport` / `TimetableImportEntry`) — extraction never writes directly to live allocations.
- **FR-14** Lab Assistant must review, and may correct, every imported entry before approval.
- **FR-15** Only after Lab Assistant approval does an imported timetable become a set of real `Allocation` records; approval then requires an explicit publish step before students can see it.
- **FR-16** The system detects conflicts (per HC-01..HC-12) among imported entries and against existing allocations during review, before approval is possible.

### Extra / Makeup Lab Booking (CR Workflow)
- **FR-17** A CR can search for candidate labs for a subject/batch/date/time, restricted to their own division.
- **FR-18** The system filters candidate labs by the subject's software/equipment/lab-type requirements before further validation.
- **FR-19** The system prevents faculty conflicts: a faculty member cannot be assigned two overlapping sessions.
- **FR-20** The system prevents lab conflicts: a lab cannot host two overlapping sessions.
- **FR-21** The system considers faculty availability windows; a session outside a faculty's declared availability is rejected.
- **FR-22** The system distinguishes batch-level occupancy from division-wide occupancy: two different batches of the same division may have simultaneous sessions if their labs and faculty differ.
- **FR-23** When a requested slot is invalid, the system provides ranked alternative allocations (different lab, different time, or both) rather than a bare rejection.
- **FR-24** Extra-lab requests are served First-Come-First-Served among valid requests; FCFS ordering never overrides a hard constraint.
- **FR-25** A CR can cancel only extra allocations belonging to their own division; cancellation sets status to `CANCELLED` and records who/when/why — it never deletes the row.
- **FR-26** A CR cannot modify, schedule, or cancel allocations for a division other than their own, even if they supply a different division ID in the request (enforced server-side).

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
- **NFR-03 (Performance)** Single-allocation candidate generation + scoring for a realistic dataset (≈15 labs) should complete fast enough for interactive use (target documented with real measurements in [16-PERFORMANCE-BENCHMARKS.md](16-PERFORMANCE-BENCHMARKS.md) once benchmarked — no number is asserted here until measured).
- **NFR-04 (Scalability)** The academic hierarchy, lab count, and batch count must be data-driven; no code change should be required to add a program, stream, division, batch, or lab.
- **NFR-05 (Maintainability)** Each hard constraint is an independently implemented, independently testable unit (`SchedulingConstraint` implementation) rather than embedded in a monolithic conditional.
- **NFR-06 (Reliability)** A cancelled or rejected allocation is never physically deleted; history is preserved via status transitions.
- **NFR-07 (Auditability)** Every consequential mutation produces an audit record sufficient to answer "who did what, when, to which resource."
- **NFR-08 (Testability)** Scheduling domain objects (candidate, context, constraint result, score breakdown) are decoupled from JPA entities and HTTP controllers so they can be unit tested without a database or web layer.
- **NFR-09 (Usability)** Every automatic or rejected allocation decision is explainable in plain language, not just a status code.
- **NFR-10 (Data Integrity)** Time-overlap logic uses proper half-open interval comparison (`startA < endB AND startB < endA`), never naive equality checks.

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
