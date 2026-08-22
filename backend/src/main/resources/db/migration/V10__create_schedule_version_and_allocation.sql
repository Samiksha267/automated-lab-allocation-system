-- Phase 8: Scheduling Domain & Allocation Persistence Foundation.
--
-- Establishes the persisted core the future Constraint Engine (Phase 9) will
-- query: academic_term -> schedule_version -> allocation. No constraint
-- evaluation, candidate generation, scoring, or backtracking logic exists
-- yet - this migration only creates the tables and the invariants the
-- database itself can guarantee (docs/06-CONSTRAINTS.md, docs/15-DESIGN-DECISIONS.md).

CREATE TABLE schedule_version (
    id                BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    academic_term_id  BIGINT      NOT NULL REFERENCES academic_term (id),
    version_number    INT         NOT NULL,
    status            VARCHAR(16) NOT NULL,
    reason            VARCHAR(500),
    created_by        BIGINT      NOT NULL REFERENCES app_user (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_by      BIGINT      REFERENCES app_user (id),
    published_at      TIMESTAMPTZ,

    CONSTRAINT chk_schedule_version_number_positive CHECK (version_number > 0),
    CONSTRAINT chk_schedule_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
    CONSTRAINT uq_schedule_version_term_number UNIQUE (academic_term_id, version_number)
);

CREATE INDEX idx_schedule_version_term ON schedule_version (academic_term_id);

-- At most one currently PUBLISHED version per academic term (ADR-009) -
-- enforced here as a real database guarantee, not merely a service-layer
-- convention that a future bug could silently violate.
CREATE UNIQUE INDEX uq_schedule_version_one_published_per_term
    ON schedule_version (academic_term_id)
    WHERE status = 'PUBLISHED';

-- The central persisted entity. Every Allocation is created only once it is
-- already known valid (either Lab-Assistant-approved from a PDF import entry,
-- Phase 19, or hard-constraint-validated in the same transaction as an EXTRA
-- booking, Phase 15) - there is deliberately no DRAFT/PENDING_REVIEW/CONFLICT
-- status here; those live on TimetableImportEntry instead
-- (docs/03-SYSTEM-ARCHITECTURE.md §5).
CREATE TABLE allocation (
    id                    BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    allocation_type       VARCHAR(16) NOT NULL,
    target_type           VARCHAR(16) NOT NULL,

    division_id           BIGINT      NOT NULL REFERENCES division (id),
    batch_id              BIGINT      REFERENCES batch (id),

    subject_id            BIGINT      NOT NULL REFERENCES subject (id),
    faculty_id            BIGINT      NOT NULL REFERENCES faculty (id),
    lab_id                BIGINT      NOT NULL REFERENCES lab (id),

    allocation_date       DATE        NOT NULL,
    start_time            TIME        NOT NULL,
    end_time              TIME        NOT NULL,

    status                VARCHAR(16) NOT NULL,

    schedule_version_id   BIGINT      NOT NULL REFERENCES schedule_version (id),

    created_by            BIGINT      NOT NULL REFERENCES app_user (id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    approved_by           BIGINT      REFERENCES app_user (id),
    approved_at           TIMESTAMPTZ,

    cancelled_by          BIGINT      REFERENCES app_user (id),
    cancelled_at          TIMESTAMPTZ,
    cancellation_reason   VARCHAR(500),

    CONSTRAINT chk_allocation_type CHECK (allocation_type IN ('REGULAR', 'EXTRA')),
    CONSTRAINT chk_allocation_target_type CHECK (target_type IN ('BATCH', 'DIVISION')),
    CONSTRAINT chk_allocation_status CHECK (status IN ('APPROVED', 'PUBLISHED', 'CANCELLED')),

    -- Half-open interval semantics [start_time, end_time) as used throughout
    -- the project (docs/06-CONSTRAINTS.md) - a session is always within a
    -- single local college day, no overnight sessions.
    CONSTRAINT chk_allocation_interval CHECK (end_time > start_time),

    -- The batch/division target invariant (docs/15-DESIGN-DECISIONS.md ADR-005):
    -- BATCH-targeted rows must carry a batch_id; DIVISION-targeted rows must not.
    -- The cross-table "batch actually belongs to division_id" rule cannot be a
    -- CHECK constraint (Postgres CHECK cannot query another table) and remains
    -- application-validated (Allocation.forBatch/forDivision).
    CONSTRAINT chk_allocation_target_invariant CHECK (
        (target_type = 'BATCH' AND batch_id IS NOT NULL)
        OR (target_type = 'DIVISION' AND batch_id IS NULL)
    )
);

-- Hot lookup paths the future Constraint Engine (Phase 9) will use for
-- HC-01 (lab), HC-02 (faculty), HC-04 (batch), HC-05 (division) conflict
-- checks - partial indexes since only APPROVED/PUBLISHED rows ever occupy a
-- resource (see AllocationStatus.blocksScheduling()); CANCELLED rows are
-- history, never queried on this hot path.
CREATE INDEX idx_allocation_lab_date ON allocation (lab_id, allocation_date)
    WHERE status IN ('APPROVED', 'PUBLISHED');
CREATE INDEX idx_allocation_faculty_date ON allocation (faculty_id, allocation_date)
    WHERE status IN ('APPROVED', 'PUBLISHED');
CREATE INDEX idx_allocation_batch_date ON allocation (batch_id, allocation_date)
    WHERE status IN ('APPROVED', 'PUBLISHED');
CREATE INDEX idx_allocation_division_date ON allocation (division_id, allocation_date)
    WHERE status IN ('APPROVED', 'PUBLISHED');

CREATE INDEX idx_allocation_schedule_version ON allocation (schedule_version_id);
CREATE INDEX idx_allocation_status ON allocation (status);
CREATE INDEX idx_allocation_subject ON allocation (subject_id);
