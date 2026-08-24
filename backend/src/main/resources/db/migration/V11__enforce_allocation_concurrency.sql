-- Phase 16: FCFS / Concurrency Finalization.
--
-- Phase 15's book-time ConstraintEngine revalidation is correct against a
-- single request's view of the data, but does not by itself prevent two
-- genuinely concurrent transactions from each seeing a resource as free and
-- both committing an overlapping row (classic write skew: T1 validates free,
-- T2 validates free, T1 inserts, T2 inserts - see docs/15-DESIGN-DECISIONS.md
-- ADR-010 finalization and docs/14-INTERVIEW-PREPARATION.md). This migration
-- makes PostgreSQL itself the final, authoritative concurrency boundary for
-- the three resource-conflict invariants that can be expressed as a clean,
-- symmetric per-row exclusion: same lab, same faculty, same batch (HC-01,
-- HC-02, HC-04). The fourth (HC-05, DIVISION-vs-BATCH cross-type conflict) is
-- deliberately NOT expressed here - see ADR-073 for why a single exclusion
-- constraint cannot correctly express it without either missing real
-- conflicts or wrongly rejecting A1/A2-style simultaneous BATCH bookings; it
-- is instead protected by a deterministic per-division pessimistic lock
-- acquired in ExtraLabService.book, serializing (never rejecting) concurrent
-- bookings within one division.
--
-- btree_gist supplies the GiST operator classes for plain scalar equality
-- (bigint) so it can be combined with tsrange's native GiST support in one
-- multi-column EXCLUDE constraint. Verified live against this project's
-- actual postgres:16-alpine Docker image before writing this file (the
-- configured POSTGRES_USER is a superuser in the official Postgres image, so
-- CREATE EXTENSION succeeds without further privilege grants) - see
-- docs/04-DATABASE-DESIGN.md for the verification record.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Half-open [start, end) semantics, matching TimeIntervalUtils and every
-- other time-range comparison in this project: 'date + start_time' and
-- 'date + end_time' each yield a naive TIMESTAMP (no timezone, matching the
-- allocation_date/start_time/end_time columns' own types - a session never
-- crosses a timezone boundary), and tsrange(..., ..., '[)') makes the start
-- instant inclusive and the end instant exclusive, so back-to-back sessions
-- (09:00-11:00 and 11:00-13:00) are correctly never treated as overlapping.
--
-- Each constraint is scoped, via its own partial WHERE predicate, to only
-- the statuses that actually occupy a resource (AllocationStatus.blocksScheduling()
-- - APPROVED, PUBLISHED) - a CANCELLED row never participates and therefore
-- can never block a new booking for the same resource/time, matching the
-- application-level rule these constraints back up, not replace.

-- HC-01 (Lab Conflict) database-level counterpart.
ALTER TABLE allocation ADD CONSTRAINT ex_allocation_lab_overlap
    EXCLUDE USING gist (
        lab_id WITH =,
        tsrange(allocation_date + start_time, allocation_date + end_time, '[)') WITH &&
    )
    WHERE (status IN ('APPROVED', 'PUBLISHED'));

-- HC-02 (Faculty Conflict) database-level counterpart.
ALTER TABLE allocation ADD CONSTRAINT ex_allocation_faculty_overlap
    EXCLUDE USING gist (
        faculty_id WITH =,
        tsrange(allocation_date + start_time, allocation_date + end_time, '[)') WITH &&
    )
    WHERE (status IN ('APPROVED', 'PUBLISHED'));

-- HC-04 (Batch Conflict) database-level counterpart - same batch only.
-- 'batch_id IS NOT NULL' excludes DIVISION-targeted rows (which always carry
-- a NULL batch_id, chk_allocation_target_invariant) from this constraint
-- entirely; they are covered instead by the per-division pessimistic lock
-- (ADR-073) alongside HC-05's cross-type check. Different batches of the
-- same division (e.g. A1 and A2) never share a batch_id, so this constraint
-- - by construction - can never reject that legitimate simultaneous case.
ALTER TABLE allocation ADD CONSTRAINT ex_allocation_batch_overlap
    EXCLUDE USING gist (
        batch_id WITH =,
        tsrange(allocation_date + start_time, allocation_date + end_time, '[)') WITH &&
    )
    WHERE (status IN ('APPROVED', 'PUBLISHED') AND batch_id IS NOT NULL);
