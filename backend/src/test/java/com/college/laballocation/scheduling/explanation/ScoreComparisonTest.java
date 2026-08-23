package com.college.laballocation.scheduling.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.scheduling.scoring.ScoreApplicability;
import com.college.laballocation.scheduling.scoring.ScoreContribution;
import com.college.laballocation.scheduling.scoring.ScoringFactorId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScoreComparisonTest {

    private ScoreContribution applied(ScoringFactorId factor, double points, double max) {
        return new ScoreContribution(factor, ScoreApplicability.APPLIED, points, max, "x", Map.of());
    }

    private ExplainedValidCandidate candidate(String labCode, ScoreContribution... contributions) {
        return new ExplainedValidCandidate(1L, labCode, 1, 0, 0, 0, List.of(contributions), List.of());
    }

    @Test
    void identifiesWhichFactorExplainsTheDifference() {
        ExplainedValidCandidate a = candidate(
                "C-202", applied(ScoringFactorId.CAPACITY_FIT, 9.86, 30), applied(ScoringFactorId.PREFERRED_LAB_TYPE, 15, 15));
        ExplainedValidCandidate b = candidate(
                "B-201", applied(ScoringFactorId.CAPACITY_FIT, 13.8, 30), applied(ScoringFactorId.PREFERRED_LAB_TYPE, 0, 15));

        List<ContributionDifference> diffs = ScoreComparison.compare(a, b);

        ContributionDifference preferredTypeDiff = diffs.stream()
                .filter(d -> d.factor() == ScoringFactorId.PREFERRED_LAB_TYPE).findFirst().orElseThrow();
        assertThat(preferredTypeDiff.difference()).isEqualTo(15);

        ContributionDifference capacityDiff =
                diffs.stream().filter(d -> d.factor() == ScoringFactorId.CAPACITY_FIT).findFirst().orElseThrow();
        assertThat(capacityDiff.difference()).isLessThan(0);
    }

    @Test
    void notApplicableForBothFactorsIsOmitted() {
        ExplainedValidCandidate a = candidate("C-202", ScoreContribution.notApplicable(ScoringFactorId.PREFERRED_LAB_TYPE, "x"));
        ExplainedValidCandidate b = candidate("B-201", ScoreContribution.notApplicable(ScoringFactorId.PREFERRED_LAB_TYPE, "x"));

        List<ContributionDifference> diffs = ScoreComparison.compare(a, b);

        assertThat(diffs).isEmpty();
    }
}
