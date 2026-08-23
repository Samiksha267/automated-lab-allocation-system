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

class PreferredLabTypeScorerTest {

    private static final double WEIGHT = 15;

    private final PreferredLabTypeScorer scorer = new PreferredLabTypeScorer(new ScoringConfiguration(30, WEIGHT, 15));

    private SchedulingContext contextWithPreference(Long preferredLabTypeId) {
        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
        return new SchedulingContext(
                request,
                new SubjectRef(3L, "BDA", "Big Data Analytics", 10L, null, preferredLabTypeId),
                new FacultyRef(4L, "FAC-BDA", "Faculty BDA", true),
                new DivisionRef(1L, "A", 68, 10L),
                new BatchRef(2L, "A1", 23, 1L),
                List.of(), List.of(), List.of());
    }

    private CandidateAllocation candidate(SchedulingContext context, Long labTypeId, String labTypeCode) {
        LabRef lab = new LabRef(9L, "C-202", true, 72, labTypeId, labTypeCode, Set.of(), Map.of(), List.of(), List.of());
        return new CandidateAllocation(context, lab);
    }

    private ScoringContext scoringContext(SchedulingContext context) {
        return new ScoringContext(context, false, Map.of(), null, null);
    }

    @Test
    void matchingPreferredTypeScoresFullWeight() {
        SchedulingContext context = contextWithPreference(30L);
        ScoreContribution contribution = scorer.score(scoringContext(context), candidate(context, 30L, "DATA_ENGINEERING"));

        assertThat(contribution.applicability()).isEqualTo(ScoreApplicability.APPLIED);
        assertThat(contribution.pointsAwarded()).isEqualTo(WEIGHT);
        assertThat(contribution.maxPoints()).isEqualTo(WEIGHT);
    }

    @Test
    void mismatchedPreferredTypeScoresZeroButRemainsApplied() {
        SchedulingContext context = contextWithPreference(30L);
        ScoreContribution contribution = scorer.score(scoringContext(context), candidate(context, 20L, "COMPUTER"));

        assertThat(contribution.applicability()).isEqualTo(ScoreApplicability.APPLIED);
        assertThat(contribution.pointsAwarded()).isEqualTo(0);
        assertThat(contribution.maxPoints()).isEqualTo(WEIGHT);
    }

    @Test
    void noPreferenceRecordedIsNotApplicableAndExcludedFromMax() {
        SchedulingContext context = contextWithPreference(null);
        ScoreContribution contribution = scorer.score(scoringContext(context), candidate(context, 20L, "COMPUTER"));

        assertThat(contribution.applicability()).isEqualTo(ScoreApplicability.NOT_APPLICABLE);
        assertThat(contribution.pointsAwarded()).isEqualTo(0);
        assertThat(contribution.maxPoints()).isEqualTo(0);
    }
}
