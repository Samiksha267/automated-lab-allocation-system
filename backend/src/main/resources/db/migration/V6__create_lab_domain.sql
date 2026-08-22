-- Phase 5: laboratory domain - what labs exist, where, their type, and what
-- software/equipment they have. This is the future Constraint Engine's
-- source of truth for HC-06 (availability), HC-07 (capacity), HC-08
-- (software), HC-09 (equipment), HC-10 (lab type) - see docs/06-CONSTRAINTS.md.
--
-- Deliberately NOT included here: SubjectSoftwareRequirement /
-- SubjectEquipmentRequirement (Phase 6 - what a SUBJECT needs, as opposed to
-- what a LAB has), and no scheduling/allocation data at all (Phase 9+).

-- Configurable, not an enum (PART 61) - new lab types must not require a
-- code deployment, same reasoning as Program/Stream in Phase 4.
CREATE TABLE lab_type (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_lab_type_code UNIQUE (code)
);

-- code is the stable, user-facing identifier (e.g. "C-304") - allocation
-- explanations reference this, never the database id (PART 4 of the phase
-- brief). Immutable after creation (see docs/15-DESIGN-DECISIONS.md) - no
-- UPDATE path in the application ever changes it.
CREATE TABLE lab (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(32)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    capacity     INT          NOT NULL,
    lab_type_id  BIGINT       NOT NULL REFERENCES lab_type (id),
    wing         VARCHAR(16)  NOT NULL,
    floor        VARCHAR(16)  NOT NULL,
    room_number  VARCHAR(16)  NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_lab_code UNIQUE (code),
    CONSTRAINT chk_lab_capacity_positive CHECK (capacity > 0)
);
CREATE INDEX idx_lab_lab_type ON lab (lab_type_id);

-- Master catalog of software capabilities. `code` is a normalized key
-- (e.g. "CLOUDERA") so "Cloudera"/"cloudera"/"CLOUDERA" can never become
-- three separate rows; `name` is the display label. No version column here -
-- version, if tracked at all, is per-installation metadata on lab_software
-- (a lab's installed Cloudera version, not "Cloudera 6.3" as a distinct
-- software capability) - see docs/15-DESIGN-DECISIONS.md.
CREATE TABLE software (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(64)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_software_code UNIQUE (code)
);

CREATE TABLE equipment (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_equipment_code UNIQUE (code)
);

-- Explicit association entity (not an implicit @ManyToMany join table) so it
-- can carry its own metadata (installed_version) without a later migration
-- to convert a plain join table into a real entity - docs/15-DESIGN-DECISIONS.md.
CREATE TABLE lab_software (
    id                BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lab_id            BIGINT      NOT NULL REFERENCES lab (id),
    software_id       BIGINT      NOT NULL REFERENCES software (id),
    installed_version VARCHAR(64),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_lab_software UNIQUE (lab_id, software_id)
);
CREATE INDEX idx_lab_software_lab ON lab_software (lab_id);
CREATE INDEX idx_lab_software_software ON lab_software (software_id);

-- quantity is meaningful for this domain (PART 11 of the phase brief's own
-- "Routers: 10" example) and costs nothing to include now versus retrofitting
-- it once a real quantity-sensitive requirement appears.
CREATE TABLE lab_equipment (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lab_id       BIGINT      NOT NULL REFERENCES lab (id),
    equipment_id BIGINT      NOT NULL REFERENCES equipment (id),
    quantity     INT         NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_lab_equipment UNIQUE (lab_id, equipment_id),
    CONSTRAINT chk_lab_equipment_quantity_positive CHECK (quantity > 0)
);
CREATE INDEX idx_lab_equipment_lab ON lab_equipment (lab_id);
CREATE INDEX idx_lab_equipment_equipment ON lab_equipment (equipment_id);
