package com.college.laballocation.scheduling.automatic;

import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.explanation.ExplainedValidCandidate;
import java.util.Objects;

/**
 * One valid (slot, lab) option for one {@link SessionRequirement}, produced
 * by re-using the real Phase 10/11/12 pipeline
 * ({@code ExplainableAllocationService.recommend(request, searchState)}) -
 * never a duplicated scoring/validity computation (PART 21/49 of the Phase
 * 14 brief). Selecting a choice during search turns it into a
 * {@link PlannedAllocation}.
 */
public record SchedulingChoice(SchedulingRequest request, ExplainedValidCandidate candidate) {

    public SchedulingChoice {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
    }

    public double normalizedScore() {
        return candidate.normalizedScore();
    }

    public String labCode() {
        return candidate.labCode();
    }
}
