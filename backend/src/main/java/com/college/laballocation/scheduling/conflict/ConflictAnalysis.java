package com.college.laballocation.scheduling.conflict;

import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.explanation.RejectionSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A pure, structured transformation of an already-computed {@code AllocationRecommendation}
 * (Phase 12) - never re-queries the database and never re-evaluates a
 * constraint (PART 33 of the Phase 13 brief). {@code rejectionSummary} is
 * the exact Phase 12 object, not a re-aggregation.
 *
 * <p>{@code structurallyViableLabIds} is the heart of this phase's search
 * decision (PART 35/36): a rejected candidate belongs here only if none of
 * its violations are {@link ConflictCategory#STRUCTURAL} - meaning every
 * reason it currently fails is one that changing the requested time could
 * plausibly fix. {@link #alternativeTimeSearchWorthwhile()} is simply
 * "this set is non-empty" - if zero candidates are structurally viable,
 * no amount of time search can help, so none is attempted.
 */
public record ConflictAnalysis(
        SchedulingRequest request,
        boolean hasConflict,
        int totalCandidatesEvaluated,
        int rejectedCandidateCount,
        RejectionSummary rejectionSummary,
        List<ConflictDetail> conflicts,
        Set<Long> structurallyViableLabIds,
        Map<Long, List<String>> temporalFailuresByLabId) {

    public ConflictAnalysis {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(rejectionSummary, "rejectionSummary must not be null");
        conflicts = List.copyOf(conflicts);
        structurallyViableLabIds = Set.copyOf(structurallyViableLabIds);
        temporalFailuresByLabId = Map.copyOf(temporalFailuresByLabId);
    }

    /** {@code true} iff at least one rejected candidate has no structural (time-unsolvable) failure. */
    public boolean alternativeTimeSearchWorthwhile() {
        return !structurallyViableLabIds.isEmpty();
    }
}
