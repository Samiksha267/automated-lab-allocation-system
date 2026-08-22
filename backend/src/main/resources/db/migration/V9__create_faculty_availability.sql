-- Phase 7: recurring weekly faculty availability - "when is a faculty member
-- allowed to teach", the source data for the future FacultyAvailabilityConstraint
-- (HC-03, docs/06-CONSTRAINTS.md). This models availability only, never an
-- actual booked/allocated session.
--
-- Term-scoped and mandatory (academic_term_id NOT NULL) - this supersedes the
-- earlier Phase 1 draft's nullable "applies every term" design
-- (docs/ASSUMPTIONS.md A-15, superseded this phase; see ADR-031 in
-- docs/15-DESIGN-DECISIONS.md for the full reasoning).
--
-- Overlap protection is application-level only (no PostgreSQL exclusion
-- constraint) - see ADR-032: recurring weekly ranges have no clean native
-- Postgres range type, and availability data is low-volume/administratively
-- mutated, so the extra complexity of a generated-range + exclusion
-- constraint was judged not worth it. The half-open interval CHECK below
-- (start_time < end_time) remains a real, non-bypassable database guarantee.

CREATE TABLE faculty_availability (
    id                BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    faculty_id        BIGINT      NOT NULL REFERENCES faculty (id),
    academic_term_id  BIGINT      NOT NULL REFERENCES academic_term (id),
    day_of_week       VARCHAR(16) NOT NULL,
    start_time        TIME        NOT NULL,
    end_time          TIME        NOT NULL,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Half-open interval semantics [start, end) as used throughout the
    -- project (docs/06-CONSTRAINTS.md) - end must strictly follow start.
    CONSTRAINT chk_faculty_availability_interval CHECK (end_time > start_time),
    CONSTRAINT chk_faculty_availability_day CHECK (day_of_week IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'))
);

CREATE INDEX idx_faculty_availability_faculty ON faculty_availability (faculty_id);
CREATE INDEX idx_faculty_availability_term ON faculty_availability (academic_term_id);

-- Hot lookup path for the availability query/check service and the future
-- FacultyAvailabilityConstraint: faculty + term + day, active rows only.
CREATE INDEX idx_faculty_availability_lookup
    ON faculty_availability (faculty_id, academic_term_id, day_of_week)
    WHERE active;
