package com.college.laballocation.scheduling.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.LabUtilizationService;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.BatchRef;
import com.college.laballocation.scheduling.SchedulingRefs.DivisionRef;
import com.college.laballocation.scheduling.SchedulingRefs.FacultyRef;
import com.college.laballocation.scheduling.SchedulingRefs.LabRef;
import com.college.laballocation.scheduling.SchedulingRefs.SubjectRef;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.constraint.ConstraintEvaluation;
import com.college.laballocation.scheduling.generation.CandidateGenerationResult;
import com.college.laballocation.scheduling.generation.EvaluatedCandidate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScoringEngineTest {

    @Mock
    private LabUtilizationService labUtilizationService;

    private ScoringEngine engine(List<AllocationScorer> scorers) {
        return new ScoringEngine(scorers, labUtilizationService);
    }

    private SchedulingRequest request() {
        return new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
    }

    private SchedulingContext context() {
        return new SchedulingContext(
                request(),
                new SubjectRef(3L, "BDA", "Big Data Analytics", 10L, null, null),
                new FacultyRef(4L, "FAC-BDA", "Faculty BDA", true),
                new DivisionRef(1L, "A", 68, 10L),
                new BatchRef(2L, "A1", 23, 1L),
                List.of(), List.of(), List.of());
    }

    private LabRef labRef(Long id, String code) {
        return new LabRef(id, code, true, 72, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of());
    }

    private EvaluatedCandidate valid(SchedulingContext context, Long labId, String code) {
        CandidateAllocation candidate = new CandidateAllocation(context, labRef(labId, code));
        ConstraintEvaluation evaluation = ConstraintEvaluation.of(List.of(ConstraintResult.pass(HardConstraintId.HC_07_CAPACITY)));
        return new EvaluatedCandidate(candidate, evaluation);
    }

    private EvaluatedCandidate invalid(SchedulingContext context, Long labId, String code) {
        CandidateAllocation candidate = new CandidateAllocation(context, labRef(labId, code));
        ConstraintViolation violation = new ConstraintViolation("CAPACITY_VIOLATION", "x", "LAB", code, Map.of());
        ConstraintEvaluation evaluation = ConstraintEvaluation.of(List.of(ConstraintResult.fail(HardConstraintId.HC_07_CAPACITY, violation)));
        return new EvaluatedCandidate(candidate, evaluation);
    }

    @BeforeEach
    void stubNoUtilizationData() {
        // Not called at all when the valid-candidate set is empty (the engine short-circuits
        // before building a ScoringContext) - lenient so that scenario isn't flagged as unused.
        org.mockito.Mockito.lenient()
                .when(labUtilizationService.scheduledMinutesByLab(any(), anyCollection()))
                .thenReturn(Optional.empty());
    }

    private AllocationScorer flatScorer(ScoringFactorId id, double weight, double pointsPerCandidate) {
        return new AllocationScorer() {
            @Override
            public ScoringFactorId id() {
                return id;
            }

            @Override
            public ScoreContribution score(ScoringContext scoringContext, CandidateAllocation candidate) {
                return new ScoreContribution(id, ScoreApplicability.APPLIED, pointsPerCandidate, weight, "flat", Map.of());
            }
        };
    }

    @Test
    void onlyValidCandidatesAreScored() {
        SchedulingContext context = context();
        CandidateGenerationResult generationResult = new CandidateGenerationResult(
                request(), List.of(valid(context, 1L, "A-101"), invalid(context, 2L, "B-201")));

        ScoringResult result = engine(List.of(flatScorer(ScoringFactorId.CAPACITY_FIT, 30, 30)))
                .score(generationResult);

        assertThat(result.rankedCandidates()).hasSize(1);
        assertThat(result.rankedCandidates().get(0).labCode()).isEqualTo("A-101");
    }

    @Test
    void emptyValidSetReturnsEmptyRankingNotAnException() {
        SchedulingContext context = context();
        CandidateGenerationResult generationResult =
                new CandidateGenerationResult(request(), List.of(invalid(context, 1L, "A-101")));

        ScoringResult result = engine(List.of(flatScorer(ScoringFactorId.CAPACITY_FIT, 30, 30)))
                .score(generationResult);

        assertThat(result.rankedCandidates()).isEmpty();
        assertThat(result.validCandidateCount()).isEqualTo(0);
    }

    @Test
    void scoresAreSummedAcrossAllRegisteredScorers() {
        SchedulingContext context = context();
        CandidateGenerationResult generationResult =
                new CandidateGenerationResult(request(), List.of(valid(context, 1L, "A-101")));

        List<AllocationScorer> scorers = List.of(
                flatScorer(ScoringFactorId.CAPACITY_FIT, 30, 20),
                flatScorer(ScoringFactorId.PREFERRED_LAB_TYPE, 15, 15));

        ScoringResult result = engine(scorers).score(generationResult);

        assertThat(result.rankedCandidates().get(0).totalScore()).isEqualTo(35);
        assertThat(result.rankedCandidates().get(0).maxPossibleScore()).isEqualTo(45);
    }

    @Test
    void notApplicableFactorIsExcludedFromMaxPossibleScore() {
        SchedulingContext context = context();
        CandidateGenerationResult generationResult =
                new CandidateGenerationResult(request(), List.of(valid(context, 1L, "A-101")));

        AllocationScorer notApplicableScorer = new AllocationScorer() {
            @Override
            public ScoringFactorId id() {
                return ScoringFactorId.PREFERRED_LAB_TYPE;
            }

            @Override
            public ScoreContribution score(ScoringContext scoringContext, CandidateAllocation candidate) {
                return ScoreContribution.notApplicable(ScoringFactorId.PREFERRED_LAB_TYPE, "no preference");
            }
        };

        ScoringResult result =
                engine(List.of(flatScorer(ScoringFactorId.CAPACITY_FIT, 30, 30), notApplicableScorer)).score(generationResult);

        assertThat(result.rankedCandidates().get(0).maxPossibleScore()).isEqualTo(30);
        assertThat(result.rankedCandidates().get(0).totalScore()).isEqualTo(30);
    }

    @Test
    void rankingIsDescendingByNormalizedScore() {
        SchedulingContext context = context();
        CandidateGenerationResult generationResult = new CandidateGenerationResult(
                request(), List.of(valid(context, 1L, "A-101"), valid(context, 2L, "B-201")));

        AllocationScorer variableScorer = new AllocationScorer() {
            @Override
            public ScoringFactorId id() {
                return ScoringFactorId.CAPACITY_FIT;
            }

            @Override
            public ScoreContribution score(ScoringContext scoringContext, CandidateAllocation candidate) {
                double points = candidate.lab().code().equals("A-101") ? 10 : 25;
                return new ScoreContribution(ScoringFactorId.CAPACITY_FIT, ScoreApplicability.APPLIED, points, 30, "x", Map.of());
            }
        };

        ScoringResult result = engine(List.of(variableScorer)).score(generationResult);

        assertThat(result.rankedCandidates()).extracting(ScoredCandidate::labCode).containsExactly("B-201", "A-101");
    }

    @Test
    void tiedScoresBreakDeterministicallyByLabCodeAscending() {
        SchedulingContext context = context();
        CandidateGenerationResult generationResult = new CandidateGenerationResult(
                request(), List.of(valid(context, 2L, "B-201"), valid(context, 1L, "A-101")));

        ScoringResult result =
                engine(List.of(flatScorer(ScoringFactorId.CAPACITY_FIT, 30, 30))).score(generationResult);

        assertThat(result.rankedCandidates()).extracting(ScoredCandidate::labCode).containsExactly("A-101", "B-201");
    }

    @Test
    void contributionBreakdownIsPreservedPerCandidate() {
        SchedulingContext context = context();
        CandidateGenerationResult generationResult =
                new CandidateGenerationResult(request(), List.of(valid(context, 1L, "A-101")));

        ScoringResult result = engine(List.of(
                        flatScorer(ScoringFactorId.CAPACITY_FIT, 30, 20), flatScorer(ScoringFactorId.PREFERRED_LAB_TYPE, 15, 5)))
                .score(generationResult);

        assertThat(result.rankedCandidates().get(0).contributions()).extracting(ScoreContribution::factor)
                .containsExactlyInAnyOrder(ScoringFactorId.CAPACITY_FIT, ScoringFactorId.PREFERRED_LAB_TYPE);
    }

    @Test
    void allValidCandidatesAreScoredNoneSkipped() {
        SchedulingContext context = context();
        CandidateGenerationResult generationResult = new CandidateGenerationResult(
                request(),
                List.of(
                        valid(context, 1L, "A-101"),
                        valid(context, 2L, "B-201"),
                        invalid(context, 3L, "C-301"),
                        valid(context, 4L, "D-101")));

        ScoringResult result =
                engine(List.of(flatScorer(ScoringFactorId.CAPACITY_FIT, 30, 30))).score(generationResult);

        assertThat(result.rankedCandidates()).hasSize(3);
        assertThat(result.validCandidateCount()).isEqualTo(3);
    }
}
