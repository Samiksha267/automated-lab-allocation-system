package com.college.laballocation.audit;

/**
 * Stable identifiers for the state-changing actions this project records
 * immutable history for (Phase 17, extended Phase 18). Deliberately not
 * exhaustive - only actions with a real, currently-implemented mutation
 * behind them are listed; read/search actions are never audited (see
 * {@link AuditLogService} class javadoc for why) and no action is added
 * speculatively for a mutation that doesn't exist yet.
 *
 * <p>{@code SCHEDULE_VERSION_CREATED}/{@code SCHEDULE_PUBLISHED}/{@code SCHEDULE_SUPERSEDED}
 * (Phase 18, backed by {@code V13__extend_audit_log_for_schedule_versioning.sql})
 * mirror the {@code CR_ASSIGNED}/{@code CR_ASSIGNMENT_ENDED} precedent (ADR-076):
 * a republish that supersedes an existing version emits both
 * {@code SCHEDULE_SUPERSEDED} (for the version that lost currency) and
 * {@code SCHEDULE_PUBLISHED} (for the new one) as two distinct events, never
 * one collapsed "republished" event, for the same reason - a division/term
 * losing its current schedule and a new one becoming current are separate
 * facts worth recording independently.
 */
public enum AuditAction {
    EXTRA_LAB_BOOKED,
    EXTRA_LAB_CANCELLED,
    CR_ASSIGNED,
    CR_ASSIGNMENT_ENDED,
    LAB_CREATED,
    LAB_UPDATED,
    LAB_SOFTWARE_CHANGED,
    LAB_EQUIPMENT_CHANGED,
    LAB_UNAVAILABILITY_CHANGED,
    FACULTY_AVAILABILITY_CHANGED,
    SUBJECT_REQUIREMENTS_CHANGED,
    SCHEDULE_VERSION_CREATED,
    SCHEDULE_PUBLISHED,
    SCHEDULE_SUPERSEDED,
    TIMETABLE_IMPORT_UPLOADED,
    TIMETABLE_IMPORT_APPROVED,
    TIMETABLE_IMPORT_REJECTED
}
