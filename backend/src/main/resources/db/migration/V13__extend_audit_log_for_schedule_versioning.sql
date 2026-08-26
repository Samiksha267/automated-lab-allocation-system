-- Phase 18: extends the Phase 17 audit_log CHECK constraints with the three
-- new schedule-versioning actions (SCHEDULE_VERSION_CREATED, SCHEDULE_PUBLISHED,
-- SCHEDULE_SUPERSEDED) and one new resource type (SCHEDULE_VERSION).
--
-- A separate migration, not an edit to V12, because V12 is already applied
-- to real environments (Flyway migrations are immutable once applied -
-- editing an already-run migration's checksum breaks validation on every
-- environment that already ran it). The audit_log table itself, its indexes,
-- and its append-only trigger are all unchanged - only the two CHECK
-- constraints widen.

ALTER TABLE audit_log DROP CONSTRAINT chk_audit_log_action;
ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_action CHECK (action IN (
    'EXTRA_LAB_BOOKED', 'EXTRA_LAB_CANCELLED',
    'CR_ASSIGNED', 'CR_ASSIGNMENT_ENDED',
    'LAB_CREATED', 'LAB_UPDATED', 'LAB_SOFTWARE_CHANGED', 'LAB_EQUIPMENT_CHANGED', 'LAB_UNAVAILABILITY_CHANGED',
    'FACULTY_AVAILABILITY_CHANGED',
    'SUBJECT_REQUIREMENTS_CHANGED',
    'SCHEDULE_VERSION_CREATED', 'SCHEDULE_PUBLISHED', 'SCHEDULE_SUPERSEDED'
));

ALTER TABLE audit_log DROP CONSTRAINT chk_audit_log_resource_type;
ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_resource_type CHECK (resource_type IN (
    'ALLOCATION', 'CR_ASSIGNMENT', 'LAB', 'FACULTY', 'SUBJECT', 'SCHEDULE_VERSION'
));
