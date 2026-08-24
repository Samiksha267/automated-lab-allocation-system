package com.college.laballocation.scheduling.automatic;

import java.util.Objects;

/**
 * One requirement the returned schedule could not place, with a real
 * (never fabricated) reason summary (PART 51 of the Phase 14 brief) -
 * derived from one representative {@code ExplainableAllocationService.recommend(...)}
 * call against the final search state, reusing
 * {@code ConflictAnalyzer}/{@code RejectionSummary} rather than a bare
 * "scheduling failed" message. Scoped honestly to that one representative
 * slot's reasons, not claimed to be exhaustive across every slot in the
 * range - see {@code AutomaticSchedulingEngine} javadoc.
 */
public record UnscheduledRequirement(String requirementKey, String reasonSummary) {

    public UnscheduledRequirement {
        Objects.requireNonNull(requirementKey, "requirementKey must not be null");
        Objects.requireNonNull(reasonSummary, "reasonSummary must not be null");
    }
}
