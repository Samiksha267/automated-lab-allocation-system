package com.college.laballocation.scheduling.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ScoreContributionTest {

    @Test
    void pointsAwardedMustNotExceedMaxPoints() {
        assertThatThrownBy(() -> new ScoreContribution(
                        ScoringFactorId.CAPACITY_FIT, ScoreApplicability.APPLIED, 35, 30, "x", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pointsAwardedMustNotBeNegative() {
        assertThatThrownBy(() -> new ScoreContribution(
                        ScoringFactorId.CAPACITY_FIT, ScoreApplicability.APPLIED, -1, 30, "x", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notApplicableMustAwardZeroOfZero() {
        assertThatThrownBy(() -> new ScoreContribution(
                        ScoringFactorId.PREFERRED_LAB_TYPE, ScoreApplicability.NOT_APPLICABLE, 15, 15, "x", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notApplicableFactoryProducesZeroOfZero() {
        ScoreContribution contribution = ScoreContribution.notApplicable(ScoringFactorId.PREFERRED_LAB_TYPE, "no preference");

        assertThat(contribution.pointsAwarded()).isEqualTo(0);
        assertThat(contribution.maxPoints()).isEqualTo(0);
        assertThat(contribution.applicability()).isEqualTo(ScoreApplicability.NOT_APPLICABLE);
    }

    @Test
    void detailsMapIsImmutable() {
        ScoreContribution contribution = new ScoreContribution(
                ScoringFactorId.CAPACITY_FIT, ScoreApplicability.APPLIED, 30, 30, "x", Map.of("a", 1));

        assertThatThrownBy(() -> contribution.details().put("b", 2)).isInstanceOf(UnsupportedOperationException.class);
    }
}
