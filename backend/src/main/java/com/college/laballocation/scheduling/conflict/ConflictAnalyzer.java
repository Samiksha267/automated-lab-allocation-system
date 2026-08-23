package com.college.laballocation.scheduling.conflict;

import com.college.laballocation.scheduling.explanation.AllocationRecommendation;
import com.college.laballocation.scheduling.explanation.RecommendationStatus;
import com.college.laballocation.scheduling.explanation.RejectedCandidateExplanation;
import com.college.laballocation.scheduling.explanation.ViolationErrorCodeLabels;
import com.college.laballocation.scheduling.explanation.ViolationExplanation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Converts an already-computed {@link AllocationRecommendation} (Phase 12)
 * into a {@link ConflictAnalysis} - a pure, in-memory transformation only
 * (PART 33 of the Phase 13 brief). Queries no repository, evaluates no
 * constraint a second time; every fact here is read directly from
 * {@code recommendation.rejectedCandidates()}/{@code rejectionSummary()}.
 */
@Component
public class ConflictAnalyzer {

    public ConflictAnalysis analyze(AllocationRecommendation recommendation) {
        List<RejectedCandidateExplanation> rejected = recommendation.rejectedCandidates();

        Map<String, Integer> counts = recommendation.rejectionSummary().countByErrorCode();
        List<ConflictDetail> conflicts = counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ConflictDetail(
                        entry.getKey(),
                        ViolationErrorCodeLabels.labelFor(entry.getKey()),
                        ConflictClassification.categoryOf(entry.getKey()),
                        entry.getValue()))
                .toList();

        Set<Long> structurallyViableLabIds = new HashSet<>();
        Map<Long, List<String>> temporalFailuresByLabId = new HashMap<>();
        for (RejectedCandidateExplanation candidate : rejected) {
            boolean hasStructuralFailure = candidate.violations().stream()
                    .anyMatch(v -> ConflictClassification.categoryOf(v.errorCode()) == ConflictCategory.STRUCTURAL);
            if (!hasStructuralFailure) {
                structurallyViableLabIds.add(candidate.labId());
                List<String> temporalCodes =
                        candidate.violations().stream().map(ViolationExplanation::errorCode).toList();
                temporalFailuresByLabId.put(candidate.labId(), temporalCodes);
            }
        }

        return new ConflictAnalysis(
                recommendation.request(),
                recommendation.status() == RecommendationStatus.NO_VALID_CANDIDATE,
                recommendation.totalCandidatesEvaluated(),
                rejected.size(),
                recommendation.rejectionSummary(),
                conflicts,
                structurallyViableLabIds,
                temporalFailuresByLabId);
    }
}
