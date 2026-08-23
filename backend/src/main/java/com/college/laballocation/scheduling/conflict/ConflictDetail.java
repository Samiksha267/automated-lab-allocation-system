package com.college.laballocation.scheduling.conflict;

import java.util.Objects;

/**
 * One rejection reason, aggregated across every rejected candidate, with
 * its {@link ConflictCategory} attached - PART 3/49 of the Phase 13 brief.
 * {@code occurrenceCount} mirrors {@code RejectionSummary.countByErrorCode()}'s
 * non-additive semantics: it counts rejected *candidates* carrying this
 * code at least once, not a global failure tally.
 */
public record ConflictDetail(String errorCode, String displayLabel, ConflictCategory category, int occurrenceCount) {

    public ConflictDetail {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        Objects.requireNonNull(displayLabel, "displayLabel must not be null");
        Objects.requireNonNull(category, "category must not be null");
        if (occurrenceCount < 0) {
            throw new IllegalArgumentException("occurrenceCount must be >= 0, got " + occurrenceCount);
        }
    }
}
