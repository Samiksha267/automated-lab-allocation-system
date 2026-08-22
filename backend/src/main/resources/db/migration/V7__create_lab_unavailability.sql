-- Phase 5: administrative/time-based lab unavailability (maintenance, repair,
-- college event) - distinct from lab.active (permanent deactivation, see
-- Lab entity javadoc) and distinct from allocation-driven booking, which
-- doesn't exist until Phase 9+. HC-06 will later combine this table with
-- real allocation data; this migration only establishes the administrative
-- half of that source data.
--
-- No recurrence model (PART 16 of the phase brief - explicit interval only,
-- weekly recurrence deferred until a real requirement demands it).

CREATE TABLE lab_unavailability (
    id                BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lab_id            BIGINT      NOT NULL REFERENCES lab (id),
    start_date_time   TIMESTAMPTZ NOT NULL,
    end_date_time     TIMESTAMPTZ NOT NULL,
    reason            VARCHAR(500) NOT NULL,
    created_by        BIGINT      NOT NULL REFERENCES app_user (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Half-open interval semantics [start, end) as used throughout the
    -- project (docs/06-CONSTRAINTS.md) - end must strictly follow start.
    CONSTRAINT chk_lab_unavailability_interval CHECK (end_date_time > start_date_time)
);
CREATE INDEX idx_lab_unavailability_lab ON lab_unavailability (lab_id);
CREATE INDEX idx_lab_unavailability_window ON lab_unavailability (lab_id, start_date_time, end_date_time);
