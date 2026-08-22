# Project Overview

## The Real Problem

Every semester, a college with multiple programs (B.Tech, MBA Tech), multiple streams (CE, CS, IT, AIML, DS), multiple years, and multiple divisions per year needs to allocate laboratory practical sessions across a small, shared pool of labs (~15 rooms across three wings). Three independent resources have to line up at once for every session: a **lab** (with the right software/equipment/capacity), a **faculty member** (who is not already teaching elsewhere and is within their declared availability), and a **student batch or division** (which is not already occupied).

On top of the official semester timetable, Class Representatives (CRs) constantly need to book **extra / makeup practicals** on short notice — and those bookings must obey the exact same constraints as the official timetable, checked against everything already scheduled, including other CRs' pending requests submitted moments earlier.

Manually done (spreadsheets, WhatsApp, physical registers), this breaks down in predictable ways:
- Two CRs book the same lab for overlapping times because neither could see the other's in-flight request.
- A faculty member ends up double-booked because their teaching load is tracked in one place and lab bookings in another.
- A batch is scheduled into a lab lacking the required software (e.g. a Big Data Analytics practical placed in a lab without Cloudera installed) and the mistake surfaces only when students arrive.
- Correcting the manually-transcribed official timetable (usually distributed as a PDF) after every audit is tedious and error-prone, and there is no record of who changed what.
- Nobody can answer "which labs are underused this semester?" without a manual room-by-room audit.

## Objective

Build a system that treats lab allocation as what it actually is: a **constraint satisfaction and optimization problem**, not a booking form. Every allocation — whether it comes from an imported official timetable or a CR's extra-lab request — passes through the same constraint engine, gets scored against real trade-offs (capacity fit, software match, utilization balance), and is guaranteed correct at the database level even under concurrent requests.

## Users

| Role | Identity | Scope |
|---|---|---|
| **Lab Assistant** | Logs in | Full administrative control: labs, software, equipment, faculty, faculty availability, academic hierarchy, CR accounts, timetable import review/approval, conflict visibility, audit logs, analytics |
| **CR (Class Representative)** | Logs in | Tied to exactly one division. Can view their division's schedule and book/cancel *extra* practicals for their own division only |
| **Student** | Logs in | Read-only. Views the currently *published* timetable, filterable by program/stream/year/division/batch |
| **Faculty** | No login (domain entity only) | Referenced by scheduling; availability is maintained by the Lab Assistant |

## Major Features

- Role-based dashboards (Lab Assistant / CR / Student) with backend-enforced authorization, not just hidden UI
- Configurable academic hierarchy (Program → Stream → Year → Division → Batch) with no hardcoded counts
- A dedicated **constraint engine** enforcing 12 hard constraints (lab conflict, faculty conflict, faculty availability, batch conflict, division-wide conflict, capacity, software, equipment, lab type, CR authorization, academic relationship validity, lab availability)
- A **scoring/ranking engine** that picks the best of several *valid* candidate labs, with a full human-readable explanation of the score
- A **backtracking scheduler** capable of generating a full multi-session timetable automatically, using a most-constrained-first heuristic, with pruning and a search budget
- **Conflict resolution** that returns structured alternatives (same time/different lab, same lab/nearest time, etc.) instead of a bare "conflict" error
- **Concurrency-safe** extra-lab booking: two simultaneous CR requests for the same lab/time can never both succeed
- A **PDF import pipeline** for the official timetable that never auto-publishes — every import goes through Lab Assistant review and correction before approval
- Immutable **audit logging** of every consequential action
- **Schedule versioning** — students only ever see the currently published version; prior versions are preserved, never overwritten

## How This Differs From a Lab-Booking CRUD App

A CRUD booking app checks "is this row already taken?" and stops there. This system:
1. Distinguishes **batch-level** occupancy from **division-wide** occupancy — two different batches of the *same* division can legitimately have simultaneous practicals, as long as their labs and faculty don't collide. A naive "division busy" flag would wrongly reject perfectly valid schedules.
2. Generates and **ranks multiple valid candidates** rather than accepting/rejecting a single proposed slot.
3. Solves **multi-session scheduling with backtracking** — not just single-booking validation.
4. Treats concurrency as a first-class correctness problem, not an edge case.
5. Keeps a full audit trail and versioned history instead of overwriting state.
6. Requires human review of any machine-extracted data (PDF import) before it can affect real schedules.

## Constraint-Based Allocation, Concretely

Given a request ("Division CS Year 3, Batch A1, needs a BDA practical Monday 09:00–11:00"), the system:
1. Resolves the subject's software/equipment/lab-type requirements (BDA → Cloudera).
2. Generates every lab that is active, has sufficient capacity, and has the required software.
3. Eliminates any lab/faculty/batch combination that would violate a hard constraint (already booked, faculty unavailable, faculty double-booked elsewhere, etc.).
4. Scores the remaining valid candidates on capacity fit, software match, utilization balance, and more.
5. Returns a ranked list with a plain-language explanation for both the winner and the rejected alternatives.

## Expected Benefits

- Zero double-bookings of labs or faculty, enforced at the database transaction level, not just in application code.
- Correct handling of the "different batches, same division, simultaneous" case that trips up naive booking systems.
- A defensible, explainable allocation decision for every session — useful for both day-to-day operation and for auditing.
- A full history of who changed what and when.
- Data to answer "how well are we using our labs?" instead of guessing.

## Résumé / Viva / Interview Summary

> Designed and built a constraint-based lab scheduling system for a college's multi-program, multi-division academic structure. Implemented a custom scheduling engine in Java/Spring Boot combining hard-constraint validation (lab/faculty/batch conflict detection using interval-overlap logic), a weighted scoring model for candidate ranking, and a most-constrained-first backtracking search for full-timetable generation. Solved database-level double-booking under concurrent requests using PostgreSQL transaction isolation. Built a PDF-import-with-human-approval pipeline for the official timetable and a role-based (JWT/Spring Security) React/TypeScript frontend for three distinct user roles.
