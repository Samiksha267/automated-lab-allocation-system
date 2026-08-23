package com.college.laballocation.scheduling.scoring;

/**
 * Stable identifiers for every soft scoring factor named in
 * docs/07-ALLOCATION-SCORING.md's original weighting table - kept as one
 * complete enum even though only {@link #CAPACITY_FIT},
 * {@link #PREFERRED_LAB_TYPE} and {@link #BALANCED_UTILIZATION} are backed
 * by a registered {@code AllocationScorer} bean today (Phase 11's readiness
 * analysis). {@link #ADDITIONAL_ENVIRONMENT_FIT}, {@link #FACULTY_PREFERENCE}
 * and {@link #TIMETABLE_GAP} are documented, stable IDs reserved for a
 * future phase once their underlying data exists - registering a fake
 * scorer for them now was explicitly prohibited (PART 45).
 */
public enum ScoringFactorId {
    CAPACITY_FIT,
    ADDITIONAL_ENVIRONMENT_FIT,
    PREFERRED_LAB_TYPE,
    BALANCED_UTILIZATION,
    FACULTY_PREFERENCE,
    TIMETABLE_GAP
}
