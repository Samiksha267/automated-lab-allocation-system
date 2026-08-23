package com.college.laballocation.scheduling.scoring;

import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.TargetType;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Rewards the <b>closest</b>-fitting capacity, not the largest lab (PART 12
 * of the Phase 11 brief) - HC-07 already guarantees every candidate reaching
 * this scorer has {@code capacity >= required}, so this measures efficiency
 * among already-valid candidates only.
 *
 * <p>Formula (docs/07-ALLOCATION-SCORING.md):
 * <pre>fitRatio = required / capacity
 * score = weight * fitRatio</pre>
 * Since a valid candidate always has {@code capacity >= required > 0},
 * {@code fitRatio} is always in {@code (0, 1]}, so
 * {@code 0 < score <= weight} - no division by zero, no clamping needed.
 * {@code required} is computed the same way HC-07 ({@code CapacityConstraint})
 * computes it - batch strength for a {@code BATCH} request, division
 * strength for {@code DIVISION} - intentionally re-derived here rather than
 * calling into Phase 9's constraint class, since a scorer must never depend
 * on constraint internals (PART 3: scoring and validation are separate
 * concerns evaluated independently, even when they read the same inputs).
 */
@Component
public class CapacityFitScorer implements AllocationScorer {

    private final ScoringConfiguration configuration;

    public CapacityFitScorer(ScoringConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public ScoringFactorId id() {
        return ScoringFactorId.CAPACITY_FIT;
    }

    @Override
    public ScoreContribution score(ScoringContext scoringContext, CandidateAllocation candidate) {
        double weight = configuration.capacityFitWeight();
        var context = scoringContext.schedulingContext();
        int required = context.request().targetType() == TargetType.BATCH
                ? context.batch().strength()
                : context.division().strength();
        int capacity = candidate.lab().capacity();

        double fitRatio = (double) required / capacity;
        double points = weight * fitRatio;

        return new ScoreContribution(
                id(),
                ScoreApplicability.APPLIED,
                points,
                weight,
                "Lab capacity " + capacity + " for required capacity " + required + " (fit ratio "
                        + String.format("%.4f", fitRatio) + ").",
                Map.of("requiredCapacity", required, "labCapacity", capacity, "fitRatio", fitRatio));
    }
}
