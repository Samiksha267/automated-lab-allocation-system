package com.college.laballocation.scheduling.conflict;

import java.util.Set;

/**
 * Classifies every existing {@code ConstraintViolation.errorCode()} (Phase
 * 9) as {@link ConflictCategory#STRUCTURAL} or {@link ConflictCategory#TEMPORAL}
 * (PART 4/5 of the Phase 13 brief) - a pure, static lookup, never a
 * duplicate constraint implementation. No new error codes are introduced
 * here; every code below already exists in a Phase 9 {@code SchedulingConstraint}.
 */
public final class ConflictClassification {

    private static final Set<String> STRUCTURAL_CODES = Set.of(
            "CAPACITY_VIOLATION",
            "SOFTWARE_MISMATCH",
            "EQUIPMENT_MISMATCH",
            "LAB_TYPE_MISMATCH",
            "INVALID_ACADEMIC_RELATIONSHIP",
            "FORBIDDEN_DIVISION_ACCESS",
            "CR_ASSIGNMENT_NOT_FOUND");

    private static final Set<String> TEMPORAL_CODES =
            Set.of("LAB_CONFLICT", "FACULTY_CONFLICT", "FACULTY_UNAVAILABLE", "BATCH_CONFLICT", "DIVISION_CONFLICT", "LAB_UNAVAILABLE");

    private ConflictClassification() {}

    /**
     * Defaults an unrecognized code to {@link ConflictCategory#STRUCTURAL} -
     * the conservative choice, since treating an unknown failure as
     * time-solvable when it might not be would cause pointless alternative
     * search rather than merely a missing "solvable" label.
     */
    public static ConflictCategory categoryOf(String errorCode) {
        if (TEMPORAL_CODES.contains(errorCode)) {
            return ConflictCategory.TEMPORAL;
        }
        return ConflictCategory.STRUCTURAL;
    }
}
