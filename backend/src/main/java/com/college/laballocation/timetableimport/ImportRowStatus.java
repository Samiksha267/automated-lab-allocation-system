package com.college.laballocation.timetableimport;

/**
 * Independent per-row severity (Phase 19, PART 8) - one import can carry
 * {@code VALID}, {@code WARNING}, and {@code ERROR} rows simultaneously; a
 * single bad row never makes the whole import an all-or-nothing failure at
 * the parsing stage (only at {@code approve} time does "any ERROR row"
 * block anything, {@code TimetableImportService.approve}).
 */
public enum ImportRowStatus {
    /** No issues - mapped and passed every hard/cross-row check. */
    VALID,
    /** Mapped and passed every hard constraint, but something is worth a reviewer's attention (a normalized value changed meaning, an ambiguous-but-resolved match). Approvable as-is. */
    WARNING,
    /** Unresolved mapping or a failed hard constraint (conflict, capacity, missing requirement, invalid time). Blocks approval until corrected. */
    ERROR
}
