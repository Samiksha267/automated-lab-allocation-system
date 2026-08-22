-- Phase 4: Subject and Faculty, plus the contextual SubjectFacultyAssignment
-- that resolves "which faculty teaches this subject for this division/batch/term"
-- (docs/04-DATABASE-DESIGN.md §3). No SubjectOffering entity is introduced -
-- SubjectFacultyAssignment already carries the term-specific offering context
-- a separate offering table would otherwise exist for; adding one here would
-- duplicate that without a concrete need (see docs/15-DESIGN-DECISIONS.md).

CREATE TABLE subject (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    academic_year_id BIGINT       NOT NULL REFERENCES academic_year (id),
    code             VARCHAR(32)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_subject_year_code UNIQUE (academic_year_id, code)
);
CREATE INDEX idx_subject_academic_year ON subject (academic_year_id);

-- Faculty is a pure domain entity - no login, no app_user row (ADR-006).
CREATE TABLE faculty (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_code VARCHAR(32)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255),
    department    VARCHAR(255),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_faculty_employee_code UNIQUE (employee_code),
    -- Nullable + unique: Postgres allows any number of NULLs under a plain
    -- UNIQUE constraint (NULLs are never considered equal to each other), so
    -- faculty without a recorded email don't collide while any email that IS
    -- recorded still can't be reused for two faculty.
    CONSTRAINT uq_faculty_email UNIQUE (email)
);

-- faculty + subject + division + batch(nullable) + academic_term.
-- batch_id NULL means the assignment covers the whole division for that
-- subject/term (docs/04-DATABASE-DESIGN.md §3) - not "unknown batch."
--
-- Uniqueness needs two PARTIAL indexes, not one plain UNIQUE constraint,
-- because Postgres treats every NULL as distinct under an ordinary UNIQUE
-- constraint - a naive UNIQUE(subject_id, division_id, batch_id, academic_term_id)
-- would silently allow unlimited duplicate division-level (batch_id IS NULL)
-- rows for the same subject/division/term. The two indexes below close that
-- gap: one enforces at-most-one batch-specific assignment per
-- (subject, division, batch, term), the other enforces at-most-one
-- division-level assignment per (subject, division, term).
CREATE TABLE subject_faculty_assignment (
    id                 BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_id         BIGINT      NOT NULL REFERENCES subject (id),
    faculty_id         BIGINT      NOT NULL REFERENCES faculty (id),
    division_id        BIGINT      NOT NULL REFERENCES division (id),
    batch_id           BIGINT      REFERENCES batch (id),
    academic_term_id   BIGINT      NOT NULL REFERENCES academic_term (id),
    active             BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_sfa_batch_scoped
    ON subject_faculty_assignment (subject_id, division_id, batch_id, academic_term_id)
    WHERE batch_id IS NOT NULL AND active;
CREATE UNIQUE INDEX uq_sfa_division_scoped
    ON subject_faculty_assignment (subject_id, division_id, academic_term_id)
    WHERE batch_id IS NULL AND active;
CREATE INDEX idx_sfa_lookup ON subject_faculty_assignment (subject_id, division_id, academic_term_id);
