-- Phase 4: academic hierarchy. Program -> Stream -> AcademicYear -> Division -> Batch,
-- plus AcademicTerm (a separate, independent concept - see docs/04-DATABASE-DESIGN.md
-- for the AcademicYear-vs-AcademicTerm distinction: AcademicYear is "Year 3 of the
-- program," AcademicTerm is "Semester 5, 2026-27").
--
-- Program/Stream names are deliberately NOT enums (PART 61 of the project brief) -
-- college structure must be data-driven and administrable without a code change.

CREATE TABLE program (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           VARCHAR(32)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    duration_years INT          NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_program_code UNIQUE (code),
    CONSTRAINT chk_program_duration_positive CHECK (duration_years > 0)
);

CREATE TABLE stream (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    program_id BIGINT       NOT NULL REFERENCES program (id),
    code       VARCHAR(32)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Scoped to (program_id, code), not a global-unique code: "CS" under B.Tech and
    -- a hypothetical "CS" under another program are different rows (docs/04-DATABASE-DESIGN.md).
    CONSTRAINT uq_stream_program_code UNIQUE (program_id, code)
);
CREATE INDEX idx_stream_program ON stream (program_id);

CREATE TABLE academic_year (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stream_id  BIGINT      NOT NULL REFERENCES stream (id),
    year_number INT        NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_academic_year_stream_number UNIQUE (stream_id, year_number),
    CONSTRAINT chk_academic_year_number_positive CHECK (year_number > 0)
);
CREATE INDEX idx_academic_year_stream ON academic_year (stream_id);

-- Independent of the Program/Stream/AcademicYear chain above - a term is a
-- scheduling period (e.g. "Semester 5, 2026-27") that applies across the
-- institution, not nested under one program. Deliberately no single global
-- "current term" singleton: different programs may run on different active
-- terms simultaneously (PART 34 of the project brief warns against a
-- simplistic global-singleton model), so `status` allows more than one
-- ACTIVE row at a time - this is intentional, not an oversight.
CREATE TABLE academic_term (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    academic_year_label VARCHAR(16) NOT NULL,
    term_number         INT          NOT NULL,
    display_name        VARCHAR(255) NOT NULL,
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'UPCOMING',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_academic_term_label_number UNIQUE (academic_year_label, term_number),
    CONSTRAINT chk_academic_term_status CHECK (status IN ('UPCOMING', 'ACTIVE', 'CLOSED')),
    CONSTRAINT chk_academic_term_dates CHECK (start_date < end_date)
);

CREATE TABLE division (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    academic_year_id BIGINT      NOT NULL REFERENCES academic_year (id),
    code            VARCHAR(16)  NOT NULL,
    strength        INT          NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_division_year_code UNIQUE (academic_year_id, code),
    CONSTRAINT chk_division_strength_positive CHECK (strength > 0)
);
CREATE INDEX idx_division_academic_year ON division (academic_year_id);

CREATE TABLE batch (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    division_id BIGINT       NOT NULL REFERENCES division (id),
    code        VARCHAR(16)  NOT NULL,
    strength    INT          NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Number of batches per division is variable (PART 8/60 of the project brief) -
    -- never assume exactly two; nothing here caps the count.
    CONSTRAINT uq_batch_division_code UNIQUE (division_id, code),
    CONSTRAINT chk_batch_strength_positive CHECK (strength > 0)
);
CREATE INDEX idx_batch_division ON batch (division_id);
