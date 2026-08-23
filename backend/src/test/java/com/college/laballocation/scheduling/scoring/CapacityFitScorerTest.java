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

class CapacityFitScorerTest {

    private static final double WEIGHT = 30;

    private final CapacityFitScorer scorer = new CapacityFitScorer(new ScoringConfiguration(WEIGHT, 15, 15));

    private SchedulingRequest request(TargetType targetType, Long batchId) {
        return new SchedulingRequest(
                AllocationType.EXTRA, targetType, 1L, batchId, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
    }

    private SchedulingContext batchContext(int batchStrength) {
        return new SchedulingContext(
                request(TargetType.BATCH, 2L),
                new SubjectRef(3L, "BDA", "Big Data Analytics", 10L, null, null),
                new FacultyRef(4L, "FAC-BDA", "Faculty BDA", true),
                new DivisionRef(1L, "A", 68, 10L),
                new BatchRef(2L, "A1", batchStrength, 1L),
                List.of(), List.of(), List.of());
    }

    private SchedulingContext divisionContext(int divisionStrength) {
        return new SchedulingContext(
                request(TargetType.DIVISION, null),
                new SubjectRef(3L, "BDA", "Big Data Analytics", 10L, null, null),
                new FacultyRef(4L, "FAC-BDA", "Faculty BDA", true),
                new DivisionRef(1L, "A", divisionStrength, 10L),
                null,
                List.of(), List.of(), List.of());
    }

    private CandidateAllocation candidate(SchedulingContext context, int capacity) {
        LabRef lab = new LabRef(9L, "C-202", true, capacity, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of());
        return new CandidateAllocation(context, lab);
    }

    @Test
    void exactCapacityMatchScoresFullWeight() {
        ScoreContribution contribution = scorer.score(new ScoringContext(batchContext(68), false, Map.of(), null, null), candidate(batchContext(68), 68));

        assertThat(contribution.applicability()).isEqualTo(ScoreApplicability.APPLIED);
        assertThat(contribution.pointsAwarded()).isEqualTo(WEIGHT);
        assertThat(contribution.maxPoints()).isEqualTo(WEIGHT);
    }

    @Test
    void slightlyLargerCapacityScoresNearFullButLessThanExactMatch() {
        SchedulingContext context = batchContext(68);
        double exact = scorer.score(new ScoringContext(context, false, Map.of(), null, null), candidate(context, 68)).pointsAwarded();
        double slightlyLarger = scorer.score(new ScoringContext(context, false, Map.of(), null, null), candidate(context, 70)).pointsAwarded();

        assertThat(slightlyLarger).isLessThan(exact).isGreaterThan(WEIGHT * 0.9);
    }

    @Test
    void muchLargerCapacityScoresMaterialllyLower() {
        SchedulingContext context = batchContext(68);
        double score150 = scorer.score(new ScoringContext(context, false, Map.of(), null, null), candidate(context, 150)).pointsAwarded();

        assertThat(score150).isLessThan(WEIGHT * 0.5);
    }

    @Test
    void closerFitAlwaysOutranksMuchLargerCapacity() {
        SchedulingContext context = batchContext(68);
        double labA = scorer.score(new ScoringContext(context, false, Map.of(), null, null), candidate(context, 70)).pointsAwarded();
        double labC = scorer.score(new ScoringContext(context, false, Map.of(), null, null), candidate(context, 150)).pointsAwarded();

        assertThat(labA).isGreaterThan(labC);
    }

    @Test
    void divisionTargetedRequestComparesAgainstDivisionStrengthNotBatch() {
        SchedulingContext context = divisionContext(68);

        ScoreContribution contribution = scorer.score(new ScoringContext(context, false, Map.of(), null, null), candidate(context, 68));

        assertThat(contribution.details()).containsEntry("requiredCapacity", 68);
    }

    @Test
    void scoreIsAlwaysWithinZeroToWeightBounds() {
        SchedulingContext context = batchContext(68);
        for (int capacity : new int[] {68, 70, 72, 80, 100, 150}) {
            double points = scorer.score(new ScoringContext(context, false, Map.of(), null, null), candidate(context, capacity)).pointsAwarded();
            assertThat(points).isGreaterThan(0).isLessThanOrEqualTo(WEIGHT);
        }
    }
}
