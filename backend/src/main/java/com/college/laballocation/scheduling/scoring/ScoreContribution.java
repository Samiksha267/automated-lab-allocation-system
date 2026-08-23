package com.college.laballocation.scheduling.scoring;

import java.util.Map;
import java.util.Objects;

/**
 * One scorer's structured, explainable answer for one candidate - never a
 * bare number (PART 7 of the Phase 11 brief). {@code details} carries the
 * raw values the explanation prose was built from (e.g.
 * {@code requiredCapacity}/{@code labCapacity}), so Phase 12 can compose a
 * richer, user-facing explanation later without re-running the scorer.
 *
 * <p>Invariants (PART 62): when {@link #applicability()} is
 * {@link ScoreApplicability#NOT_APPLICABLE}, both {@code pointsAwarded} and
 * {@code maxPoints} must be zero - a not-applicable factor never contributes
 * to a candidate's applicable maximum. Otherwise {@code 0 <= pointsAwarded
 * <= maxPoints} always holds.
 */
public record ScoreContribution(
        ScoringFactorId factor,
        ScoreApplicability applicability,
        double pointsAwarded,
        double maxPoints,
        String explanation,
        Map<String, Object> details) {

    public ScoreContribution {
        Objects.requireNonNull(factor, "factor must not be null");
        Objects.requireNonNull(applicability, "applicability must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
        details = Map.copyOf(details);
        if (applicability == ScoreApplicability.NOT_APPLICABLE) {
            if (pointsAwarded != 0 || maxPoints != 0) {
                throw new IllegalArgumentException("A NOT_APPLICABLE contribution must award 0 of 0 points, not "
                        + pointsAwarded + "/" + maxPoints);
            }
        } else if (pointsAwarded < 0 || pointsAwarded > maxPoints) {
            throw new IllegalArgumentException(
                    "pointsAwarded (" + pointsAwarded + ") must be within [0, maxPoints=" + maxPoints + "]");
        }
    }

    public static ScoreContribution notApplicable(ScoringFactorId factor, String explanation) {
        return new ScoreContribution(factor, ScoreApplicability.NOT_APPLICABLE, 0, 0, explanation, Map.of());
    }
}
