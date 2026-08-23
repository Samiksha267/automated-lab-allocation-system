package com.college.laballocation.scheduling.explanation;

/**
 * The two possible outcomes of one {@code ExplainableAllocationService.recommend(...)}
 * call - never a persisted status, purely descriptive of one advisory
 * snapshot (PART 4/5 of the Phase 12 brief). {@link #NO_VALID_CANDIDATE} is a
 * normal, expected result (every lab genuinely failed some hard constraint
 * for this request), never represented as an error/exception.
 */
public enum RecommendationStatus {
    RECOMMENDED,
    NO_VALID_CANDIDATE
}
