package com.college.laballocation.scheduling.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.BatchRef;
import com.college.laballocation.scheduling.SchedulingRefs.DivisionRef;
import com.college.laballocation.scheduling.SchedulingRefs.FacultyRef;
import com.college.laballocation.scheduling.SchedulingRefs.LabRef;
import com.college.laballocation.scheduling.SchedulingRefs.SubjectRef;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BalancedUtilizationScorerTest {

    private static final double WEIGHT = 15;

    private final BalancedUtilizationScorer scorer = new BalancedUtilizationScorer(new ScoringConfiguration(30, 15, WEIGHT));

    private SchedulingContext context() {
        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
        return new SchedulingContext(
                request,
                new SubjectRef(3L, "BDA", "Big Data Analytics", 10L, null, null),
                new FacultyRef(4L, "FAC-BDA", "Faculty BDA", true),
                new DivisionRef(1L, "A", 68, 10L),
                new BatchRef(2L, "A1", 23, 1L),
                List.of(), List.of(), List.of());
    }

    private CandidateAllocation candidate(SchedulingContext context, Long labId) {
        LabRef lab = new LabRef(labId, "LAB-" + labId, true, 72, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of());
        return new CandidateAllocation(context, lab);
    }

    @Test
    void noPublishedScheduleVersionIsNotApplicable() {
        ScoringContext scoringContext = new ScoringContext(context(), false, Map.of(), null, null);

        ScoreContribution contribution = scorer.score(scoringContext, candidate(context(), 1L));

        assertThat(contribution.applicability()).isEqualTo(ScoreApplicability.NOT_APPLICABLE);
        assertThat(contribution.maxPoints()).isEqualTo(0);
    }

    @Test
    void leastLoadedLabScoresHigherThanMostLoaded() {
        SchedulingContext context = context();
        Map<Long, Long> load = Map.of(1L, 1000L, 2L, 500L, 3L, 0L);
        ScoringContext scoringContext = new ScoringContext(context, true, load, 0L, 1000L);

        double labA = scorer.score(scoringContext, candidate(context, 1L)).pointsAwarded();
        double labB = scorer.score(scoringContext, candidate(context, 2L)).pointsAwarded();
        double labC = scorer.score(scoringContext, candidate(context, 3L)).pointsAwarded();

        assertThat(labC).isGreaterThan(labB).isGreaterThan(labA);
        assertThat(labC).isEqualTo(WEIGHT);
        assertThat(labA).isEqualTo(0);
    }

    @Test
    void allLoadsEqualScoresFullWeightForEveryCandidate() {
        SchedulingContext context = context();
        Map<Long, Long> load = Map.of(1L, 200L, 2L, 200L);
        ScoringContext scoringContext = new ScoringContext(context, true, load, 200L, 200L);

        assertThat(scorer.score(scoringContext, candidate(context, 1L)).pointsAwarded()).isEqualTo(WEIGHT);
        assertThat(scorer.score(scoringContext, candidate(context, 2L)).pointsAwarded()).isEqualTo(WEIGHT);
    }

    @Test
    void allLoadsZeroDoesNotDivideByZeroAndScoresFullWeight() {
        SchedulingContext context = context();
        Map<Long, Long> load = Map.of();
        ScoringContext scoringContext = new ScoringContext(context, true, load, 0L, 0L);

        assertThat(scorer.score(scoringContext, candidate(context, 1L)).pointsAwarded()).isEqualTo(WEIGHT);
    }

    @Test
    void scoreIsAlwaysWithinZeroToWeightBounds() {
        SchedulingContext context = context();
        Map<Long, Long> load = Map.of(1L, 300L, 2L, 150L, 3L, 0L);
        ScoringContext scoringContext = new ScoringContext(context, true, load, 0L, 300L);

        for (long labId : new long[] {1L, 2L, 3L}) {
            double points = scorer.score(scoringContext, candidate(context, labId)).pointsAwarded();
            assertThat(points).isGreaterThanOrEqualTo(0).isLessThanOrEqualTo(WEIGHT);
        }
    }
}
