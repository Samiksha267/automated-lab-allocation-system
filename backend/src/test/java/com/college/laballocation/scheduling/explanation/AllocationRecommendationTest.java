package com.college.laballocation.scheduling.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AllocationRecommendationTest {

    private SchedulingRequest request() {
        return new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
    }

    private ExplainedValidCandidate candidate(String labCode, int rank) {
        return new ExplainedValidCandidate(1L, labCode, rank, 30, 60, 0.5, List.of(), List.of());
    }

    @Test
    void recommendedStatusRequiresNonNullRecommendedCandidate() {
        assertThatThrownBy(() -> new AllocationRecommendation(
                        request(), RecommendationStatus.RECOMMENDED, null, List.of(), List.of(),
                        new RejectionSummary(0, java.util.Map.of()), List.of(), 1, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noValidCandidateStatusRequiresNullRecommendedCandidate() {
        ExplainedValidCandidate winner = candidate("A-101", 1);
        assertThatThrownBy(() -> new AllocationRecommendation(
                        request(), RecommendationStatus.NO_VALID_CANDIDATE, winner, List.of(winner), List.of(),
                        new RejectionSummary(0, java.util.Map.of()), List.of(), 1, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recommendedCandidateMustBeRankOneOfRankedList() {
        ExplainedValidCandidate rank1 = candidate("A-101", 1);
        ExplainedValidCandidate rank2 = candidate("B-201", 2);
        assertThatThrownBy(() -> new AllocationRecommendation(
                        request(), RecommendationStatus.RECOMMENDED, rank2, List.of(rank1, rank2), List.of(),
                        new RejectionSummary(0, java.util.Map.of()), List.of(), 2, 2, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otherValidCandidatesExcludesTheRecommendedOne() {
        ExplainedValidCandidate rank1 = candidate("A-101", 1);
        ExplainedValidCandidate rank2 = candidate("B-201", 2);
        AllocationRecommendation recommendation = new AllocationRecommendation(
                request(), RecommendationStatus.RECOMMENDED, rank1, List.of(rank1, rank2), List.of(),
                new RejectionSummary(0, java.util.Map.of()), List.of(), 2, 2, 0);

        assertThat(recommendation.otherValidCandidates()).containsExactly(rank2);
    }

    @Test
    void noValidCandidateWithEmptyRankedListIsValid() {
        AllocationRecommendation recommendation = new AllocationRecommendation(
                request(), RecommendationStatus.NO_VALID_CANDIDATE, null, List.of(), List.of(),
                new RejectionSummary(1, java.util.Map.of("CAPACITY_VIOLATION", 1)), List.of("No valid laboratory satisfies all hard constraints."),
                1, 0, 1);

        assertThat(recommendation.recommendedCandidate()).isNull();
        assertThat(recommendation.otherValidCandidates()).isEmpty();
    }
}
