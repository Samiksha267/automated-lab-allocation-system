-- Phase 19: PDF timetable import staging - untrusted, extracted/parsed data
-- lives here, completely separate from `allocation`, until a Lab Assistant
-- reviews, corrects, and explicitly approves it (docs/15-DESIGN-DECISIONS.md).
-- No status here ever represents a confirmed booking; `allocation` remains
-- the only source of truth for what is actually scheduled.

CREATE TABLE timetable_import (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    academic_term_id    BIGINT       NOT NULL REFERENCES academic_term (id),
    schedule_version_id BIGINT       NOT NULL REFERENCES schedule_version (id),

    original_filename   VARCHAR(255) NOT NULL,
    file_size_bytes     BIGINT       NOT NULL,
    file_hash           VARCHAR(64)  NOT NULL,

    status               VARCHAR(16) NOT NULL,
    failure_reason        VARCHAR(500),

    uploaded_by           BIGINT      NOT NULL REFERENCES app_user (id),
    uploaded_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    approved_by           BIGINT      REFERENCES app_user (id),
    approved_at           TIMESTAMPTZ,

    CONSTRAINT chk_timetable_import_status CHECK (status IN (
        'UPLOADED', 'NEEDS_REVIEW', 'VALIDATED', 'APPROVED', 'REJECTED', 'FAILED'
    ))
);

CREATE INDEX idx_timetable_import_term ON timetable_import (academic_term_id);
CREATE INDEX idx_timetable_import_version ON timetable_import (schedule_version_id);
CREATE INDEX idx_timetable_import_hash ON timetable_import (file_hash);
CREATE INDEX idx_timetable_import_status ON timetable_import (status);

-- Every parsed timetable row - raw extracted values are preserved forever
-- alongside normalized/mapped values (PART 16), never overwritten by a
-- correction (see `corrected` flag instead).
CREATE TABLE timetable_import_row (
    id                   BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    timetable_import_id  BIGINT      NOT NULL REFERENCES timetable_import (id),
    row_number            INT         NOT NULL,

    raw_day               VARCHAR(50),
    raw_start_time        VARCHAR(50),
    raw_end_time          VARCHAR(50),
    raw_subject           VARCHAR(255),
    raw_faculty           VARCHAR(255),
    raw_lab               VARCHAR(255),
    raw_division          VARCHAR(255),
    raw_batch              VARCHAR(255),

    normalized_day          VARCHAR(16),
    normalized_start_time   TIME,
    normalized_end_time     TIME,

    subject_id    BIGINT REFERENCES subject (id),
    faculty_id    BIGINT REFERENCES faculty (id),
    lab_id        BIGINT REFERENCES lab (id),
    division_id   BIGINT REFERENCES division (id),
    batch_id      BIGINT REFERENCES batch (id),

    allocation_date DATE,

    validation_status    VARCHAR(16) NOT NULL,
    validation_messages  JSONB       NOT NULL DEFAULT '[]'::jsonb,

    corrected  BOOLEAN NOT NULL DEFAULT FALSE,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_timetable_import_row_status CHECK (validation_status IN ('VALID', 'WARNING', 'ERROR')),
    CONSTRAINT uq_timetable_import_row_number UNIQUE (timetable_import_id, row_number)
);

CREATE INDEX idx_timetable_import_row_import ON timetable_import_row (timetable_import_id);
CREATE INDEX idx_timetable_import_row_status ON timetable_import_row (validation_status);

-- Traceability (PART 38): which import produced a confirmed allocation, if any.
-- Nullable - EXTRA bookings and any future non-import allocation source never set it.
ALTER TABLE allocation ADD COLUMN source_import_id BIGINT REFERENCES timetable_import (id);
CREATE INDEX idx_allocation_source_import ON allocation (source_import_id) WHERE source_import_id IS NOT NULL;

-- Extends the Phase 17/18 audit_log CHECK constraints with the three Phase 19
-- import actions - a new migration, not an edit to the already-applied V12/V13
-- (same reasoning as V13's own header comment).
ALTER TABLE audit_log DROP CONSTRAINT chk_audit_log_action;
ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_action CHECK (action IN (
    'EXTRA_LAB_BOOKED', 'EXTRA_LAB_CANCELLED',
    'CR_ASSIGNED', 'CR_ASSIGNMENT_ENDED',
    'LAB_CREATED', 'LAB_UPDATED', 'LAB_SOFTWARE_CHANGED', 'LAB_EQUIPMENT_CHANGED', 'LAB_UNAVAILABILITY_CHANGED',
    'FACULTY_AVAILABILITY_CHANGED',
    'SUBJECT_REQUIREMENTS_CHANGED',
    'SCHEDULE_VERSION_CREATED', 'SCHEDULE_PUBLISHED', 'SCHEDULE_SUPERSEDED',
    'TIMETABLE_IMPORT_UPLOADED', 'TIMETABLE_IMPORT_APPROVED', 'TIMETABLE_IMPORT_REJECTED'
));

ALTER TABLE audit_log DROP CONSTRAINT chk_audit_log_resource_type;
ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_resource_type CHECK (resource_type IN (
    'ALLOCATION', 'CR_ASSIGNMENT', 'LAB', 'FACULTY', 'SUBJECT', 'SCHEDULE_VERSION', 'TIMETABLE_IMPORT'
));
