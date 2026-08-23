package com.college.laballocation.scheduling.alternative;

/**
 * The outcome of one {@code AlternativeSuggestionService.findAlternatives(...)}
 * call (PART 16 of the Phase 13 brief). {@link #ALTERNATIVES_NOT_NEEDED}
 * means the original request already has a valid same-time recommendation
 * (Phase 12) - no search was attempted at all. {@link #NO_ALTERNATIVE_FOUND}
 * covers two distinct real situations, both non-exceptional: the request is
 * structurally impossible (search was never attempted, PART 19/20), or a
 * search was attempted but every candidate slot also failed (PART 62).
 */
public enum AlternativeSearchStatus {
    ALTERNATIVES_NOT_NEEDED,
    ALTERNATIVES_FOUND,
    NO_ALTERNATIVE_FOUND
}
