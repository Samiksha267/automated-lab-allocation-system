-- Phase 4: CRAssignment - connects an app_user (role=CR) to a division for a
-- given academic term, preserving history rather than overwriting on
-- reassignment (docs/04-DATABASE-DESIGN.md §1).
--
-- Two rules, each its own partial unique index (application-enforced role
-- check - "user_id must reference a CR-role app_user" - cannot be expressed
-- as a plain CHECK constraint across tables in Postgres, see V4's assignment
-- table comment for the same limitation):
--   1. at most one ACTIVE assignment per (division_id, academic_term_id)
--   2. at most one ACTIVE assignment per (user_id, academic_term_id)
--      (PART 17 of the project brief: one active division per CR per term)

CREATE TABLE cr_assignment (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES app_user (id),
    division_id      BIGINT      NOT NULL REFERENCES division (id),
    academic_term_id BIGINT      NOT NULL REFERENCES academic_term (id),
    status           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    valid_from       TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to         TIMESTAMPTZ,
    created_by       BIGINT      NOT NULL REFERENCES app_user (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_cr_assignment_status CHECK (status IN ('ACTIVE', 'ENDED'))
);
CREATE UNIQUE INDEX uq_cr_assignment_division_active
    ON cr_assignment (division_id, academic_term_id)
    WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_cr_assignment_user_active
    ON cr_assignment (user_id, academic_term_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_cr_assignment_user ON cr_assignment (user_id);
CREATE INDEX idx_cr_assignment_division ON cr_assignment (division_id);
