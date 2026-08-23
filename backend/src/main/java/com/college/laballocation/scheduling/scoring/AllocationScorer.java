package com.college.laballocation.scheduling.scoring;

import com.college.laballocation.scheduling.CandidateAllocation;

/**
 * One independent soft-scoring concern (PART 5 of the Phase 11 brief).
 * Every implementation is Spring-discovered by {@code ScoringEngine} - to
 * add a factor later, add a bean; to remove one, delete the bean, no engine
 * change required.
 *
 * <p>A scorer must never invalidate a candidate (that is exclusively
 * {@code ConstraintEngine}'s job, Phase 9) and must never query/mutate the
 * database beyond what {@link ScoringContext} already carries - see
 * {@code LabUtilizationService} for the one exception, which loads its data
 * once per scoring run rather than once per candidate.
 */
public interface AllocationScorer {

    ScoringFactorId id();

    ScoreContribution score(ScoringContext scoringContext, CandidateAllocation candidate);
}
