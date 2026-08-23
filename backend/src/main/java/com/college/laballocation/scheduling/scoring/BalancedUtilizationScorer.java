package com.college.laballocation.scheduling.scoring;

import com.college.laballocation.scheduling.CandidateAllocation;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Rewards a less-loaded lab, relative to the other valid candidates in this
 * same scoring run - never an absolute utilization percentage, since no
 * working-days/daily-operating-hours concept exists to divide by (PART 21/22
 * of the Phase 11 brief; docs/07-ALLOCATION-SCORING.md's "Balanced
 * Utilization" readiness note). "Load" is scheduled minutes within the
 * term's currently PUBLISHED schedule version ({@code LabUtilizationService}).
 *
 * <p>Formula - min-max normalization across the candidate set's loads
 * (chosen over the brief's PART 23 "ratio against the single most-loaded
 * lab" sketch specifically because that formula forces the most-loaded
 * candidate to exactly zero regardless of how close the rest of the field
 * is, which the brief itself flags as potentially unstable):
 * <pre>
 * if maxLoad == minLoad:
 *     score = weight   // every candidate equally loaded - nothing to differentiate, full credit for all
 * else:
 *     score = weight * (maxLoad - candidateLoad) / (maxLoad - minLoad)
 * </pre>
 * Bounded {@code [0, weight]} by construction. {@link ScoreApplicability#NOT_APPLICABLE}
 * only when the term has no PUBLISHED schedule version at all
 * ({@code utilizationDataAvailable() == false}) - there is no basis for
 * comparison, not even a zero-everyone-is-equal one (PART 27's applicability
 * pattern, reused here for the same reason).
 */
@Component
public class BalancedUtilizationScorer implements AllocationScorer {

    private final ScoringConfiguration configuration;

    public BalancedUtilizationScorer(ScoringConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public ScoringFactorId id() {
        return ScoringFactorId.BALANCED_UTILIZATION;
    }

    @Override
    public ScoreContribution score(ScoringContext scoringContext, CandidateAllocation candidate) {
        if (!scoringContext.utilizationDataAvailable()) {
            return ScoreContribution.notApplicable(
                    id(), "No PUBLISHED schedule version exists yet for this term; scheduled load cannot be compared.");
        }

        double weight = configuration.balancedUtilizationWeight();
        long candidateLoad = scoringContext.scheduledMinutesFor(candidate.lab().id());
        long minLoad = scoringContext.minLoadMinutes();
        long maxLoad = scoringContext.maxLoadMinutes();

        double points;
        String explanation;
        if (maxLoad == minLoad) {
            points = weight;
            explanation = "All candidate labs are equally scheduled (" + candidateLoad + " min); full credit.";
        } else {
            points = weight * (maxLoad - candidateLoad) / (double) (maxLoad - minLoad);
            explanation = "Lab scheduled " + candidateLoad + " min this term, versus " + minLoad + "-" + maxLoad
                    + " min across candidate labs.";
        }

        return new ScoreContribution(
                id(),
                ScoreApplicability.APPLIED,
                points,
                weight,
                explanation,
                Map.of("scheduledMinutes", candidateLoad, "minLoadMinutes", minLoad, "maxLoadMinutes", maxLoad));
    }
}
