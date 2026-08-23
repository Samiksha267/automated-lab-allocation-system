package com.college.laballocation.scheduling.conflict;

import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.explanation.AllocationRecommendation;
import com.college.laballocation.scheduling.explanation.RecommendationStatus;
import com.college.laballocation.scheduling.explanation.RejectedCandidateExplanation;
import com.college.laballocation.scheduling.explanation.RejectionSummary;
import com.college.laballocation.scheduling.explanation.ViolationExplanation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConflictAnalyzerTest {

    private final ConflictAnalyzer analyzer = new ConflictAnalyzer();

    private SchedulingRequest request() {
        return new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
    }

    private ViolationExplanation violation(String errorCode) {
        return new ViolationExplanation(errorCode, errorCode, "x", "LAB", "1", Map.of());
    }

    private RejectedCandidateExplanation rejected(Long labId, String labCode, String... errorCodes) {
        return new RejectedCandidateExplanation(labId, labCode, List.of(errorCodes).stream().map(this::violation).toList());
    }

    private AllocationRecommendation noValidRecommendation(List<RejectedCandidateExplanation> rejected) {
        RejectionSummary summary = RejectionSummary.from(rejected);
        return new AllocationRecommendation(
                request(), RecommendationStatus.NO_VALID_CANDIDATE, null, List.of(), rejected, summary,
                List.of("No valid laboratory satisfies all hard constraints."), rejected.size(), 0, rejected.size());
    }

    @Test
    void candidateFailingOnlyTemporalConstraintIsStructurallyViable() {
        AllocationRecommendation recommendation = noValidRecommendation(List.of(rejected(1L, "A-101", "LAB_CONFLICT")));

        ConflictAnalysis analysis = analyzer.analyze(recommendation);

        assertThat(analysis.structurallyViableLabIds()).containsExactly(1L);
        assertThat(analysis.alternativeTimeSearchWorthwhile()).isTrue();
    }

    @Test
    void candidateFailingStructuralAndTemporalIsNotStructurallyViable() {
        AllocationRecommendation recommendation =
                noValidRecommendation(List.of(rejected(1L, "A-101", "SOFTWARE_MISMATCH", "LAB_CONFLICT")));

        ConflictAnalysis analysis = analyzer.analyze(recommendation);

        assertThat(analysis.structurallyViableLabIds()).isEmpty();
        assertThat(analysis.alternativeTimeSearchWorthwhile()).isFalse();
    }

    @Test
    void allCandidatesFailingCapacityMeansNoStructurallyViableLabs() {
        AllocationRecommendation recommendation = noValidRecommendation(List.of(
                rejected(1L, "A-101", "CAPACITY_VIOLATION"), rejected(2L, "B-201", "CAPACITY_VIOLATION")));

        ConflictAnalysis analysis = analyzer.analyze(recommendation);

        assertThat(analysis.structurallyViableLabIds()).isEmpty();
        assertThat(analysis.alternativeTimeSearchWorthwhile()).isFalse();
    }

    @Test
    void mixedStructuralAndTemporalCandidatesStillYieldsViableLabs() {
        List<RejectedCandidateExplanation> rejected = List.of(
                rejected(1L, "A-101", "SOFTWARE_MISMATCH"),
                rejected(2L, "B-201", "SOFTWARE_MISMATCH"),
                rejected(3L, "C-301", "FACULTY_UNAVAILABLE"));
        AllocationRecommendation recommendation = noValidRecommendation(rejected);

        ConflictAnalysis analysis = analyzer.analyze(recommendation);

        assertThat(analysis.structurallyViableLabIds()).containsExactly(3L);
        assertThat(analysis.alternativeTimeSearchWorthwhile()).isTrue();
    }

    @Test
    void multipleTemporalFailuresOnOneCandidateAreBothRetained() {
        AllocationRecommendation recommendation =
                noValidRecommendation(List.of(rejected(1L, "A-101", "LAB_CONFLICT", "FACULTY_CONFLICT")));

        ConflictAnalysis analysis = analyzer.analyze(recommendation);

        assertThat(analysis.structurallyViableLabIds()).containsExactly(1L);
        assertThat(analysis.temporalFailuresByLabId().get(1L)).containsExactlyInAnyOrder("LAB_CONFLICT", "FACULTY_CONFLICT");
    }

    @Test
    void conflictDetailsCarryCorrectCategoryAndCount() {
        List<RejectedCandidateExplanation> rejected = List.of(
                rejected(1L, "A-101", "SOFTWARE_MISMATCH"),
                rejected(2L, "B-201", "SOFTWARE_MISMATCH", "CAPACITY_VIOLATION"));
        AllocationRecommendation recommendation = noValidRecommendation(rejected);

        ConflictAnalysis analysis = analyzer.analyze(recommendation);

        ConflictDetail software = analysis.conflicts().stream().filter(c -> c.errorCode().equals("SOFTWARE_MISMATCH")).findFirst().orElseThrow();
        assertThat(software.category()).isEqualTo(ConflictCategory.STRUCTURAL);
        assertThat(software.occurrenceCount()).isEqualTo(2);
    }
}
