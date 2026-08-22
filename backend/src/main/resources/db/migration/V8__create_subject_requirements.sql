-- Phase 6: what a SUBJECT requires - the other half of the future
-- Constraint Engine's inputs, complementing what a LAB provides (Phase 5).
-- Never mixed onto the same tables: requirements live on subject_* tables,
-- capabilities stay on lab_* tables (docs/03-SYSTEM-ARCHITECTURE.md).
--
-- Requirement scope decision: subject-level, not a per-term/division
-- "SubjectOffering" - nothing in the existing requirements documents a case
-- where the same subject needs different software in different terms, and
-- introducing that scope now would be exactly the kind of speculative
-- complexity the project's working rules warn against (docs/15-DESIGN-DECISIONS.md).

-- Required lab type is modeled as nullable FK columns directly on `subject`,
-- not a separate requirement table - a subject can require at most ONE lab
-- type, so a join table would only ever hold zero or one row per subject,
-- which a nullable column expresses more directly (mirrors the same
-- reasoning already used for subject.required_lab_type_id in the Phase 1
-- design). `preferred_lab_type_id` is the separate *soft* scoring signal
-- from docs/07-ALLOCATION-SCORING.md - never the same concept as "required."
--
-- The CHECK constraint below enforces the documented rule: if a required
-- type is set, no preferred type may also be set (a required type already
-- pins the choice; "preferring" something else within that single valid
-- type is meaningless). Both null, or exactly one of the two set, are the
-- only valid states.
ALTER TABLE subject ADD COLUMN required_lab_type_id BIGINT REFERENCES lab_type (id);
ALTER TABLE subject ADD COLUMN preferred_lab_type_id BIGINT REFERENCES lab_type (id);
ALTER TABLE subject ADD CONSTRAINT chk_subject_lab_type_pref
    CHECK (required_lab_type_id IS NULL OR preferred_lab_type_id IS NULL);
CREATE INDEX idx_subject_required_lab_type ON subject (required_lab_type_id);
CREATE INDEX idx_subject_preferred_lab_type ON subject (preferred_lab_type_id);

-- No `required` boolean column - every row's mere existence means
-- "required" (PART 4 of the phase brief: a boolean that's always true on
-- every row is redundant). ALL-required semantics (not ANY) are enforced by
-- the future constraint engine reading every row for a subject, not by
-- anything in this schema.
CREATE TABLE subject_software_requirement (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_id  BIGINT      NOT NULL REFERENCES subject (id),
    software_id BIGINT      NOT NULL REFERENCES software (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_subject_software_requirement UNIQUE (subject_id, software_id)
);
CREATE INDEX idx_ssr_subject ON subject_software_requirement (subject_id);
CREATE INDEX idx_ssr_software ON subject_software_requirement (software_id);

-- required_quantity mirrors lab_equipment.quantity (Phase 5) - future
-- compatibility semantics: availableQuantity(lab, equipment) >= requiredQuantity.
CREATE TABLE subject_equipment_requirement (
    id                BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_id        BIGINT      NOT NULL REFERENCES subject (id),
    equipment_id      BIGINT      NOT NULL REFERENCES equipment (id),
    required_quantity INT         NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_subject_equipment_requirement UNIQUE (subject_id, equipment_id),
    CONSTRAINT chk_ser_quantity_positive CHECK (required_quantity > 0)
);
CREATE INDEX idx_ser_subject ON subject_equipment_requirement (subject_id);
CREATE INDEX idx_ser_equipment ON subject_equipment_requirement (equipment_id);
