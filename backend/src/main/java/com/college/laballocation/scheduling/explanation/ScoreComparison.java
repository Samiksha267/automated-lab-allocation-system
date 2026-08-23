package com.college.laballocation.scheduling.explanation;

import com.college.laballocation.scheduling.scoring.ScoreApplicability;
import com.college.laballocation.scheduling.scoring.ScoreContribution;
import com.college.laballocation.scheduling.scoring.ScoringFactorId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A small, reusable pairwise comparison helper (PART 17 of the Phase 12
 * brief: "structured score-difference comparison is enough" - deliberately
 * not a natural-language explanation generator, PART 37). Answers "why did A
 * outrank B?" by diffing each candidate's already-computed
 * {@link ScoreContribution}s per factor - no formula is recomputed here.
 */
public final class ScoreComparison {

    private ScoreComparison() {}

    /**
     * One {@link ContributionDifference} per scoring factor that is
     * {@link ScoreApplicability#APPLIED} for at least one of the two
     * candidates, ordered the same way {@code candidateA}'s contributions
     * were originally produced (the registered-scorer order from
     * {@code ScoringEngine}, PART 34). A factor {@code NOT_APPLICABLE} for
     * both candidates is omitted - there is nothing to compare.
     */
    public static List<ContributionDifference> compare(ExplainedValidCandidate candidateA, ExplainedValidCandidate candidateB) {
        Map<ScoringFactorId, ScoreContribution> byFactorB =
                candidateB.scoreContributions().stream().collect(Collectors.toMap(ScoreContribution::factor, Function.identity()));

        return candidateA.scoreContributions().stream()
                .filter(a -> a.applicability() == ScoreApplicability.APPLIED
                        || byFactorB.getOrDefault(a.factor(), a).applicability() == ScoreApplicability.APPLIED)
                .map(a -> {
                    ScoreContribution b = byFactorB.get(a.factor());
                    double pointsA = a.pointsAwarded();
                    double pointsB = b != null ? b.pointsAwarded() : 0;
                    return new ContributionDifference(
                            a.factor(), ScoringFactorLabels.labelFor(a.factor()), pointsA, pointsB, pointsA - pointsB);
                })
                .toList();
    }
}
