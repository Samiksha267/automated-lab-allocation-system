package com.college.laballocation.scheduling;

/**
 * Deliberately small (docs/03-SYSTEM-ARCHITECTURE.md §5) - an {@code Allocation}
 * row is only ever created once it is already known valid, so
 * {@code DRAFT}/{@code PENDING_REVIEW}/{@code CONFLICT}/{@code REJECTED} live
 * on {@code TimetableImportEntry} instead (Phase 19), never here.
 *
 * <p>{@link #blocksScheduling()} is the single, centralized definition of
 * "does this status occupy a lab/faculty/batch/division for future conflict
 * checks" - repositories and services must call this rather than each
 * independently re-deciding which statuses count as active (PART 27 of the
 * Phase 8 brief).
 */
public enum AllocationStatus {
    APPROVED,
    PUBLISHED,
    CANCELLED;

    public boolean blocksScheduling() {
        return this == APPROVED || this == PUBLISHED;
    }
}
