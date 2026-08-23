package com.college.laballocation.scheduling.explanation;

import com.college.laballocation.scheduling.SchedulingRequest;
import java.util.List;
import java.util.Objects;

/**
 * The full, advisory result of one {@code ExplainableAllocationService.recommend(...)}
 * call - transient, never persisted (PART 2/38/39 of the Phase 12 brief).
 * "Recommended" means <i>best candidate according to this snapshot</i>, not
 * <i>successfully booked</i> - no {@code Allocation} row is created, no lab
 * is reserved, and the result can become stale the moment another request
 * commits a real booking (Phase 16 owns revalidation at commit time).
 *
 * <p><b>Terminology (PART 23):</b> deliberately named {@code AllocationRecommendation},
 * not {@code AllocationDecision} - "decision" would imply something was
 * committed, which never happens here.
 *
 * <p>{@code rankedValidCandidates} holds every valid candidate in rank order
 * (rank 1 = {@link #recommendedCandidate()}, when present); {@link #otherValidCandidates()}
 * is a derived view, not a second stored list - the same "computed filtered
 * view, not a duplicate collection" pattern {@code CandidateGenerationResult}
 * (Phase 10) already established.
 */
public record AllocationRecommendation(
        SchedulingRequest request,
        RecommendationStatus status,
        ExplainedValidCandidate recommendedCandidate,
        List<ExplainedValidCandidate> rankedValidCandidates,
        List<RejectedCandidateExplanation> rejectedCandidates,
        RejectionSummary rejectionSummary,
        List<String> summary,
        int totalCandidatesEvaluated,
        int validCandidateCount,
        int invalidCandidateCount) {

    public AllocationRecommendation {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(rejectionSummary, "rejectionSummary must not be null");
        rankedValidCandidates = List.copyOf(rankedValidCandidates);
        rejectedCandidates = List.copyOf(rejectedCandidates);
        summary = List.copyOf(summary);

        boolean recommendedPresent = recommendedCandidate != null;
        if (status == RecommendationStatus.RECOMMENDED && !recommendedPresent) {
            throw new IllegalArgumentException("status RECOMMENDED requires a non-null recommendedCandidate");
        }
        if (status == RecommendationStatus.NO_VALID_CANDIDATE && recommendedPresent) {
            throw new IllegalArgumentException("status NO_VALID_CANDIDATE requires a null recommendedCandidate");
        }
        if (recommendedPresent) {
            if (rankedValidCandidates.isEmpty() || !rankedValidCandidates.get(0).equals(recommendedCandidate)) {
                throw new IllegalArgumentException("recommendedCandidate must equal rankedValidCandidates.get(0)");
            }
        }
    }

    /** Every ranked valid candidate except the recommended one (rank 2+) - "other valid candidates," never "alternatives" (PART 42). */
    public List<ExplainedValidCandidate> otherValidCandidates() {
        return rankedValidCandidates.isEmpty() ? List.of() : rankedValidCandidates.subList(1, rankedValidCandidates.size());
    }
}
