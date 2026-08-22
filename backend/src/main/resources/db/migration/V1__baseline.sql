-- Baseline migration for Phase 2 (Project Foundation).
--
-- Purpose: verify that Flyway is wired correctly (connects, creates its history
-- table, applies a versioned migration) before any real domain schema exists.
-- The actual domain schema (program/stream/division/batch/allocation/etc., see
-- docs/04-DATABASE-DESIGN.md) is introduced through its own migrations starting
-- in Phase 4, one bounded domain at a time - never all at once here.

CREATE TABLE IF NOT EXISTS schema_baseline_check (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    note VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO schema_baseline_check (note)
VALUES ('Flyway baseline applied successfully - Phase 2 foundation');
