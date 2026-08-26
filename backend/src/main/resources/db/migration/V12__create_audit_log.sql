-- Phase 17: Immutable Audit Logs.
--
-- Records "who did what, to what, when" for the state-changing actions this
-- project's requirements actually call for (FR-32/FR-33) - never a source of
-- truth for current domain state (Allocation.status, CrAssignment.status,
-- etc. remain authoritative; see docs/03-SYSTEM-ARCHITECTURE.md).
--
-- Immutability is enforced at BOTH layers (docs/15-DESIGN-DECISIONS.md):
-- application-level (no update/delete code path exists anywhere in this
-- project - AuditLog has no setters, AuditLogRepository is never handed an
-- UPDATE/DELETE-capable method, AuditLogController exposes GET only) AND
-- database-level (the trigger below), because application-level immutability
-- alone only proves this codebase doesn't mutate the table - it says nothing
-- about a future migration, a manual production hotfix, or a different
-- service sharing this database. The trigger is the actual guarantee.

CREATE TABLE audit_log (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    actor_user_id     BIGINT       NOT NULL REFERENCES app_user (id),
    actor_role        VARCHAR(32)  NOT NULL,

    action            VARCHAR(64)  NOT NULL,
    resource_type     VARCHAR(32)  NOT NULL,
    resource_id       BIGINT       NOT NULL,
    resource_display  VARCHAR(255),

    -- Nullable query-friendly scope columns (PART 15) - populated when the
    -- action genuinely has term/division context (extra-lab booking, CR
    -- assignment), left NULL for admin actions that don't (lab creation,
    -- subject requirement changes). Never required universally.
    academic_term_id  BIGINT       REFERENCES academic_term (id),
    division_id       BIGINT       REFERENCES division (id),

    -- Small, event-specific, non-sensitive detail (PART 12/13/14) - never a
    -- serialized JPA entity, never a password/JWT/secret. Core searchable
    -- facts (actor, action, resource, term, division, timestamp) are real
    -- relational columns above, never buried in this JSON blob.
    metadata          JSONB        NOT NULL DEFAULT '{}'::jsonb,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_audit_log_action CHECK (action IN (
        'EXTRA_LAB_BOOKED', 'EXTRA_LAB_CANCELLED',
        'CR_ASSIGNED', 'CR_ASSIGNMENT_ENDED',
        'LAB_CREATED', 'LAB_UPDATED', 'LAB_SOFTWARE_CHANGED', 'LAB_EQUIPMENT_CHANGED', 'LAB_UNAVAILABILITY_CHANGED',
        'FACULTY_AVAILABILITY_CHANGED',
        'SUBJECT_REQUIREMENTS_CHANGED'
    )),
    CONSTRAINT chk_audit_log_resource_type CHECK (resource_type IN (
        'ALLOCATION', 'CR_ASSIGNMENT', 'LAB', 'FACULTY', 'SUBJECT'
    ))
);

-- Base index for the default "newest first" listing and any date-range filter.
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at DESC);

-- Direct equality filters the activity API supports (PART 18/32).
CREATE INDEX idx_audit_log_actor ON audit_log (actor_user_id);
CREATE INDEX idx_audit_log_action ON audit_log (action);
CREATE INDEX idx_audit_log_resource ON audit_log (resource_type, resource_id);

-- Composite indexes for the two scope filters a Lab Assistant is expected to
-- narrow activity by most often, each already sorted for the default
-- newest-first ordering within that scope (PART 18). Partial, since most
-- audit rows may not carry a term/division at all (admin lab/subject actions).
CREATE INDEX idx_audit_log_term_created ON audit_log (academic_term_id, created_at DESC)
    WHERE academic_term_id IS NOT NULL;
CREATE INDEX idx_audit_log_division_created ON audit_log (division_id, created_at DESC)
    WHERE division_id IS NOT NULL;

-- Database-level append-only enforcement (PART 26 - mandatory). Any UPDATE or
-- DELETE against audit_log, from any source (application, another service,
-- a manual psql session), is rejected before it can take effect. INSERT is
-- untouched. There is deliberately no "superuser bypass" flag or disable
-- switch here - the whole point is that this table cannot be quietly edited.
CREATE FUNCTION audit_log_prevent_update_delete() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_prevent_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_prevent_update_delete();

CREATE TRIGGER trg_audit_log_prevent_delete
    BEFORE DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_prevent_update_delete();
