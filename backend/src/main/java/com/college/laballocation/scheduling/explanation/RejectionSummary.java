package com.college.laballocation.scheduling.explanation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate rejection data across every {@link RejectedCandidateExplanation}
 * in one {@code AllocationRecommendation} (PART 18/19 of the Phase 12
 * brief).
 *
 * <p><b>Semantics (must be read carefully, PART 18):</b> {@code countByErrorCode}
 * counts how many <i>rejected candidates</i> carried each error code at
 * least once - one candidate failing both {@code CAPACITY_VIOLATION} and
 * {@code SOFTWARE_MISMATCH} increments both counts. The sum of
 * {@code countByErrorCode.values()} is therefore generally <b>greater than
 * or equal to</b> {@code rejectedCount}, never assumed equal to it - this is
 * documented here specifically so a future UI never mistakenly sums the
 * per-reason counts and displays that as the number of rejected labs.
 */
public record RejectionSummary(int rejectedCount, Map<String, Integer> countByErrorCode) {

    public RejectionSummary {
        countByErrorCode = Map.copyOf(countByErrorCode);
    }

    public static RejectionSummary from(List<RejectedCandidateExplanation> rejected) {
        Objects.requireNonNull(rejected, "rejected must not be null");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RejectedCandidateExplanation candidate : rejected) {
            for (ViolationExplanation violation : candidate.violations()) {
                counts.merge(violation.errorCode(), 1, Integer::sum);
            }
        }
        return new RejectionSummary(rejected.size(), counts);
    }

    /** The error code(s) with the highest count - ties return every tied code, never an arbitrary single pick. */
    public List<String> mostCommonReasons() {
        int max = countByErrorCode.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (max == 0) {
            return List.of();
        }
        return countByErrorCode.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
