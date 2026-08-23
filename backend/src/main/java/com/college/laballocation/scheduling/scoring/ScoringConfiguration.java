package com.college.laballocation.scheduling.scoring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized, configurable soft-scoring weights (PART 10 of the Phase 11
 * brief) - never scattered magic numbers inside individual scorer classes.
 * Backed by {@code app.scoring.*} in {@code application.yml}, following this
 * project's existing convention of constructor-injected {@code @Value}
 * fields ({@code SchedulingTimeMapper}, {@code JwtService}) rather than
 * {@code @ConfigurationProperties}.
 *
 * <p>Only the three factors Phase 11's readiness analysis found actually
 * implementable (docs/07-ALLOCATION-SCORING.md) have a weight here.
 * Additional Environment Fit, Faculty Preference and Fewer Timetable Gaps
 * are deferred - no weight exists for them because no scorer bean exists for
 * them (PART 66: do not manufacture configuration for a factor that isn't
 * implemented). Weights need not sum to 100 (PART 11) - the engine computes
 * each candidate's applicable maximum from whichever factors actually
 * applied, never a hardcoded denominator.
 */
@Component
public class ScoringConfiguration {

    private final double capacityFitWeight;
    private final double preferredLabTypeWeight;
    private final double balancedUtilizationWeight;

    public ScoringConfiguration(
            @Value("${app.scoring.capacity-fit-weight}") double capacityFitWeight,
            @Value("${app.scoring.preferred-lab-type-weight}") double preferredLabTypeWeight,
            @Value("${app.scoring.balanced-utilization-weight}") double balancedUtilizationWeight) {
        requireNonNegative(ScoringFactorId.CAPACITY_FIT, capacityFitWeight);
        requireNonNegative(ScoringFactorId.PREFERRED_LAB_TYPE, preferredLabTypeWeight);
        requireNonNegative(ScoringFactorId.BALANCED_UTILIZATION, balancedUtilizationWeight);
        this.capacityFitWeight = capacityFitWeight;
        this.preferredLabTypeWeight = preferredLabTypeWeight;
        this.balancedUtilizationWeight = balancedUtilizationWeight;
    }

    private static void requireNonNegative(ScoringFactorId factor, double weight) {
        if (weight < 0) {
            throw new IllegalArgumentException("Scoring weight for " + factor + " must be >= 0, got " + weight);
        }
    }

    public double capacityFitWeight() {
        return capacityFitWeight;
    }

    public double preferredLabTypeWeight() {
        return preferredLabTypeWeight;
    }

    public double balancedUtilizationWeight() {
        return balancedUtilizationWeight;
    }
}
