package com.college.laballocation.scheduling.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingActor;
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
import com.college.laballocation.scheduling.generation.CandidateGenerator;
import com.college.laballocation.scheduling.generation.EvaluatedCandidate;
import com.college.laballocation.scheduling.scoring.ScoreApplicability;
import com.college.laballocation.scheduling.scoring.ScoreContribution;
import com.college.laballocation.scheduling.scoring.ScoredCandidate;
import com.college.laballocation.scheduling.scoring.ScoringEngine;
import com.college.laballocation.scheduling.scoring.ScoringFactorId;
import com.college.laballocation.scheduling.scoring.ScoringResult;
import com.college.laballocation.user.UserRole;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExplainableAllocationServiceTest {

    @Mock
    private CandidateGenerator candidateGenerator;

    @Mock
    private ScoringEngine scoringEngine;

    private ExplainableAllocationService service;

    @BeforeEach
    void setUp() {
        service = new ExplainableAllocationService(candidateGenerator, scoringEngine);
    }

    private SchedulingRequest request() {
        return new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
    }

    private SchedulingRequest requestWithActor(SchedulingActor actor) {
        return new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), actor);
    }

    private SchedulingContext context(SchedulingRequest req) {
        return new SchedulingContext(
                req,
                new SubjectRef(3L, "BDA", "Big Data Analytics", 10L, null, null),
                new FacultyRef(4L, "FAC-BDA", "Faculty BDA", true),
                new DivisionRef(1L, "A", 68, 10L),
                new BatchRef(2L, "A1", 23, 1L),
                List.of(), List.of(), List.of());
    }

    private LabRef labRef(Long id, String code) {
        return new LabRef(id, code, true, 72, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of());
    }

    private EvaluatedCandidate valid(SchedulingContext context, Long labId, String code, ConstraintResult... results) {
        CandidateAllocation candidate = new CandidateAllocation(context, labRef(labId, code));
        ConstraintResult[] toUse = results.length > 0 ? results : new ConstraintResult[] {ConstraintResult.pass(HardConstraintId.HC_07_CAPACITY)};
        return new EvaluatedCandidate(candidate, ConstraintEvaluation.of(List.of(toUse)));
    }

    private EvaluatedCandidate invalid(SchedulingContext context, Long labId, String code, ConstraintViolation... violations) {
        CandidateAllocation candidate = new CandidateAllocation(context, labRef(labId, code));
        List<ConstraintResult> results = new java.util.ArrayList<>();
        for (ConstraintViolation v : violations) {
            results.add(ConstraintResult.fail(HardConstraintId.HC_07_CAPACITY, v));
        }
        return new EvaluatedCandidate(candidate, ConstraintEvaluation.of(results));
    }

    private ScoredCandidate scored(EvaluatedCandidate candidate, double total, double max, ScoreContribution... contributions) {
        return new ScoredCandidate(candidate, List.of(contributions), total, max);
    }

    private ScoreContribution applied(ScoringFactorId factor, double points, double max) {
        return new ScoreContribution(factor, ScoreApplicability.APPLIED, points, max, "x", Map.of());
    }

    @Test
    void topCandidateIsRecommendedAndOthersArePreservedAsOtherValid() {
        SchedulingContext context = context(request());
        EvaluatedCandidate a = valid(context, 1L, "A-101");
        EvaluatedCandidate b = valid(context, 2L, "B-201");
        CandidateGenerationResult generationResult = new CandidateGenerationResult(request(), List.of(a, b));
        ScoredCandidate scoredA = scored(a, 48, 60, applied(ScoringFactorId.CAPACITY_FIT, 48, 60));
        ScoredCandidate scoredB = scored(b, 42, 60, applied(ScoringFactorId.CAPACITY_FIT, 42, 60));
        ScoringResult scoringResult =
                new ScoringResult(request(), List.of(scoredA, scoredB), 2, List.of(ScoringFactorId.CAPACITY_FIT));
        when(candidateGenerator.generate(any(), any())).thenReturn(generationResult);
        when(scoringEngine.score(any())).thenReturn(scoringResult);

        AllocationRecommendation recommendation = service.recommend(request());

        assertThat(recommendation.status()).isEqualTo(RecommendationStatus.RECOMMENDED);
        assertThat(recommendation.recommendedCandidate().labCode()).isEqualTo("A-101");
        assertThat(recommendation.otherValidCandidates()).extracting(ExplainedValidCandidate::labCode).containsExactly("B-201");
    }

    @Test
    void zeroValidCandidatesReturnsNoValidCandidateStatusWithNullRecommendation() {
        SchedulingContext context = context(request());
        EvaluatedCandidate invalidCandidate = invalid(context, 1L, "A-101",
                new ConstraintViolation("CAPACITY_VIOLATION", "too small", "LAB", "A-101", Map.of()));
        CandidateGenerationResult generationResult = new CandidateGenerationResult(request(), List.of(invalidCandidate));
        ScoringResult scoringResult = new ScoringResult(request(), List.of(), 0, List.of(ScoringFactorId.CAPACITY_FIT));
        when(candidateGenerator.generate(any(), any())).thenReturn(generationResult);
        when(scoringEngine.score(any())).thenReturn(scoringResult);

        AllocationRecommendation recommendation = service.recommend(request());

        assertThat(recommendation.status()).isEqualTo(RecommendationStatus.NO_VALID_CANDIDATE);
        assertThat(recommendation.recommendedCandidate()).isNull();
        assertThat(recommendation.rankedValidCandidates()).isEmpty();
        assertThat(recommendation.summary()).anySatisfy(s -> assertThat(s).contains("No valid laboratory"));
    }

    @Test
    void invalidCandidateAppearsOnlyInRejectedListNeverScored() {
        SchedulingContext context = context(request());
        EvaluatedCandidate validOne = valid(context, 1L, "A-101");
        EvaluatedCandidate invalidOne = invalid(context, 2L, "B-201",
                new ConstraintViolation("SOFTWARE_MISMATCH", "missing cloudera", "LAB", "B-201", Map.of()));
        CandidateGenerationResult generationResult =
                new CandidateGenerationResult(request(), List.of(validOne, invalidOne));
        ScoredCandidate scoredA = scored(validOne, 30, 30, applied(ScoringFactorId.CAPACITY_FIT, 30, 30));
        ScoringResult scoringResult = new ScoringResult(request(), List.of(scoredA), 1, List.of(ScoringFactorId.CAPACITY_FIT));
        when(candidateGenerator.generate(any(), any())).thenReturn(generationResult);
        when(scoringEngine.score(any())).thenReturn(scoringResult);

        AllocationRecommendation recommendation = service.recommend(request());

        assertThat(recommendation.rankedValidCandidates()).extracting(ExplainedValidCandidate::labCode).containsExactly("A-101");
        assertThat(recommendation.rejectedCandidates()).extracting(RejectedCandidateExplanation::labCode).containsExactly("B-201");
        assertThat(recommendation.rejectedCandidates().get(0).violations()).extracting(ViolationExplanation::errorCode)
                .containsExactly("SOFTWARE_MISMATCH");
    }

    @Test
    void multipleViolationReasonsAreAllPreserved() {
        SchedulingContext context = context(request());
        EvaluatedCandidate invalidOne = invalid(context, 1L, "A-101",
                new ConstraintViolation("CAPACITY_VIOLATION", "too small", "LAB", "A-101", Map.of()),
                new ConstraintViolation("SOFTWARE_MISMATCH", "missing cloudera", "LAB", "A-101", Map.of()));
        CandidateGenerationResult generationResult = new CandidateGenerationResult(request(), List.of(invalidOne));
        ScoringResult scoringResult = new ScoringResult(request(), List.of(), 0, List.of(ScoringFactorId.CAPACITY_FIT));
        when(candidateGenerator.generate(any(), any())).thenReturn(generationResult);
        when(scoringEngine.score(any())).thenReturn(scoringResult);

        AllocationRecommendation recommendation = service.recommend(request());

        assertThat(recommendation.rejectedCandidates().get(0).violations()).extracting(ViolationExplanation::errorCode)
                .containsExactlyInAnyOrder("CAPACITY_VIOLATION", "SOFTWARE_MISMATCH");
    }

    @Test
    void scoreBreakdownPreservesExactPhase11Contributions() {
        SchedulingContext context = context(request());
        EvaluatedCandidate a = valid(context, 1L, "A-101");
        ScoreContribution capacity = applied(ScoringFactorId.CAPACITY_FIT, 9.86, 30);
        ScoreContribution preferred = applied(ScoringFactorId.PREFERRED_LAB_TYPE, 15, 15);
        ScoreContribution utilization = applied(ScoringFactorId.BALANCED_UTILIZATION, 15, 15);
        ScoredCandidate scoredA = scored(a, 39.86, 60, capacity, preferred, utilization);
        ScoringResult scoringResult = new ScoringResult(
                request(), List.of(scoredA), 1,
                List.of(ScoringFactorId.CAPACITY_FIT, ScoringFactorId.PREFERRED_LAB_TYPE, ScoringFactorId.BALANCED_UTILIZATION));
        when(candidateGenerator.generate(any(), any())).thenReturn(new CandidateGenerationResult(request(), List.of(a)));
        when(scoringEngine.score(any())).thenReturn(scoringResult);

        AllocationRecommendation recommendation = service.recommend(request());

        assertThat(recommendation.recommendedCandidate().scoreContributions()).containsExactly(capacity, preferred, utilization);
        assertThat(recommendation.recommendedCandidate().score()).isEqualTo(39.86);
        assertThat(recommendation.recommendedCandidate().applicableMaxScore()).isEqualTo(60);
    }

    @Test
    void notApplicableScoringFactorIsPreservedAndExcludedFromMax() {
        SchedulingContext context = context(request());
        EvaluatedCandidate a = valid(context, 1L, "A-101");
        ScoreContribution capacity = applied(ScoringFactorId.CAPACITY_FIT, 20, 30);
        ScoreContribution notApplicable = ScoreContribution.notApplicable(ScoringFactorId.PREFERRED_LAB_TYPE, "no preference");
        ScoredCandidate scoredA = scored(a, 20, 30, capacity, notApplicable);
        ScoringResult scoringResult =
                new ScoringResult(request(), List.of(scoredA), 1, List.of(ScoringFactorId.CAPACITY_FIT, ScoringFactorId.PREFERRED_LAB_TYPE));
        when(candidateGenerator.generate(any(), any())).thenReturn(new CandidateGenerationResult(request(), List.of(a)));
        when(scoringEngine.score(any())).thenReturn(scoringResult);

        AllocationRecommendation recommendation = service.recommend(request());

        ExplainedValidCandidate recommended = recommendation.recommendedCandidate();
        assertThat(recommended.applicableMaxScore()).isEqualTo(30);
        assertThat(recommended.scoreContributions()).extracting(ScoreContribution::applicability)
                .contains(ScoreApplicability.NOT_APPLICABLE);
    }

    @Test
    void notApplicableConstraintIsRepresentedAsNotApplicableNeverPass() {
        SchedulingRequest req = requestWithActor(new SchedulingActor(99L, UserRole.LAB_ASSISTANT));
        SchedulingContext context = context(req);
        EvaluatedCandidate a = valid(context, 1L, "A-101",
                ConstraintResult.pass(HardConstraintId.HC_07_CAPACITY),
                ConstraintResult.notApplicable(HardConstraintId.HC_11_CR_AUTHORIZATION));
        ScoredCandidate scoredA = scored(a, 30, 30, applied(ScoringFactorId.CAPACITY_FIT, 30, 30));
        ScoringResult scoringResult = new ScoringResult(req, List.of(scoredA), 1, List.of(ScoringFactorId.CAPACITY_FIT));
        when(candidateGenerator.generate(any(), any())).thenReturn(new CandidateGenerationResult(req, List.of(a)));
        when(scoringEngine.score(any())).thenReturn(scoringResult);

        AllocationRecommendation recommendation = service.recommend(req);

        ConstraintCheckExplanation hc11 = recommendation.recommendedCandidate().constraintChecks().stream()
                .filter(c -> c.constraintId() == HardConstraintId.HC_11_CR_AUTHORIZATION)
                .findFirst()
                .orElseThrow();
        assertThat(hc11.outcome()).isEqualTo(com.college.laballocation.scheduling.ConstraintOutcome.NOT_APPLICABLE);
        assertThat(hc11.detail()).containsIgnoringCase("not applicable");
    }

    @Test
    void tiedScoresAreRepresentedAsEqualWithDeterministicOrderOnly() {
        SchedulingContext context = context(request());
        EvaluatedCandidate a = valid(context, 1L, "A-101");
        EvaluatedCandidate b = valid(context, 2L, "B-201");
        // Engine already broke the tie by lab code - "A-101" listed first, both scores equal.
        ScoredCandidate scoredA = scored(a, 30, 60, applied(ScoringFactorId.CAPACITY_FIT, 30, 60));
        ScoredCandidate scoredB = scored(b, 30, 60, applied(ScoringFactorId.CAPACITY_FIT, 30, 60));
        ScoringResult scoringResult = new ScoringResult(request(), List.of(scoredA, scoredB), 2, List.of(ScoringFactorId.CAPACITY_FIT));
        when(candidateGenerator.generate(any(), any())).thenReturn(new CandidateGenerationResult(request(), List.of(a, b)));
        when(scoringEngine.score(any())).thenReturn(scoringResult);

        AllocationRecommendation recommendation = service.recommend(request());

        assertThat(recommendation.recommendedCandidate().normalizedScore())
                .isEqualTo(recommendation.otherValidCandidates().get(0).normalizedScore());
        assertThat(recommendation.recommendedCandidate().labCode()).isEqualTo("A-101");
    }

    @Test
    void rejectionSummaryAggregatesCorrectlyWithoutOverstatingCandidateCount() {
        SchedulingContext context = context(request());
        EvaluatedCandidate labA = invalid(context, 1L, "A-101",
                new ConstraintViolation("SOFTWARE_MISMATCH", "x", "LAB", "A-101", Map.of()));
        EvaluatedCandidate labB = invalid(context, 2L, "B-201",
                new ConstraintViolation("SOFTWARE_MISMATCH", "x", "LAB", "B-201", Map.of()),
                new ConstraintViolation("CAPACITY_VIOLATION", "x", "LAB", "B-201", Map.of()));
        CandidateGenerationResult generationResult = new CandidateGenerationResult(request(), List.of(labA, labB));
        ScoringResult scoringResult = new ScoringResult(request(), List.of(), 0, List.of(ScoringFactorId.CAPACITY_FIT));
        when(candidateGenerator.generate(any(), any())).thenReturn(generationResult);
        when(scoringEngine.score(any())).thenReturn(scoringResult);

        AllocationRecommendation recommendation = service.recommend(request());

        RejectionSummary summary = recommendation.rejectionSummary();
        assertThat(summary.rejectedCount()).isEqualTo(2);
        assertThat(summary.countByErrorCode()).containsEntry("SOFTWARE_MISMATCH", 2).containsEntry("CAPACITY_VIOLATION", 1);
    }
}
