package com.college.laballocation.scheduling.alternative;

/**
 * How an {@code AlternativeSuggestion} differs from the originally requested
 * session (PART 14 of the Phase 13 brief). Deliberately omits
 * {@code SAME_TIME_DIFFERENT_LAB}: Phase 10's {@code CandidateGenerator}
 * already evaluates every lab at the exact requested time, so if any lab
 * were valid there, {@code ExplainableAllocationService} would already
 * return {@code RECOMMENDED} and {@code AlternativeSuggestionService} would
 * never reach the time-search path that produces these suggestions at all
 * (see {@code AlternativeSearchStatus#ALTERNATIVES_NOT_NEEDED}). A
 * same-time-different-lab enum value would therefore never actually be
 * producible - creating it would violate PART 14's "do not create enum
 * values for unsupported behavior."
 */
public enum AlternativeType {
    SAME_DAY_DIFFERENT_TIME,
    DIFFERENT_DAY
}
