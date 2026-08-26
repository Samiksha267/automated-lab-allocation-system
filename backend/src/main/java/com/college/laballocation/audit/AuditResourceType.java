package com.college.laballocation.audit;

/**
 * Stable resource-type identifiers an {@link AuditLog} row's
 * {@code resourceId} is scoped to. Deliberately small (five values) and
 * reused for association-level changes on a parent resource - e.g.
 * {@code LAB_SOFTWARE_CHANGED} uses {@code LAB}/{@code labId} with the
 * software code carried in {@code metadata}, rather than inventing a
 * separate {@code LAB_SOFTWARE} resource type for every join table (PART 10/11
 * of the phase brief: "keep it extensible... avoid overcomplicated
 * polymorphic foreign keys").
 */
public enum AuditResourceType {
    ALLOCATION,
    CR_ASSIGNMENT,
    LAB,
    FACULTY,
    SUBJECT,
    SCHEDULE_VERSION,
    TIMETABLE_IMPORT
}
