package com.college.laballocation.scheduling.explanation;

import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.SchedulingActor;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.automatic.SchedulingSearchState;
import com.college.laballocation.scheduling.generation.CandidateGenerationResult;
import com.college.laballocation.scheduling.generation.CandidateGenerator;
import com.college.laballocation.scheduling.generation.EvaluatedCandidate;
import com.college.laballocation.scheduling.scoring.ScoreApplicability;
import com.college.laballocation.scheduling.scoring.ScoreContribution;
import com.college.laballocation.scheduling.scoring.ScoredCandidate;
import com.college.laballocation.scheduling.scoring.ScoringEngine;
import com.college.laballocation.scheduling.scoring.ScoringResult;
import com.college.laballocation.user.UserRole;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The first orchestration layer combining Phase 10 ({@code CandidateGenerator})
 * and Phase 11 ({@code ScoringEngine}) into one structured, explainable
 * {@link AllocationRecommendation} - answering "which lab is currently the
 * best valid candidate, and why?" and "why were the others rejected or
 * ranked lower?" (PART 1 of the Phase 12 brief).
 *
 * <p><b>Advisory only</b> (PART 2): nothing here persists an {@code Allocation},
 * reserves a lab, or locks any row - {@code recommend(...)} runs inside one
 * {@code @Transactional(readOnly = true)} boundary (joining the read-only
 * transactions {@code CandidateGenerator}/{@code ScoringEngine} already open)
 * and performs no writes. The result is a snapshot that can become stale the
 * moment the transaction ends - concurrent-safe commit-time revalidation is
 * Phase 16's responsibility, not this one's.
 *
 * <p><b>No recomputation</b> (PART 38/39): every hard-constraint outcome and
 * every score contribution here is read directly from the already-produced
 * {@link CandidateGenerationResult}/{@link ScoringResult} - this class
 * performs zero additional database queries beyond what those two already
 * ran, and duplicates no formula.
 */
@Service
@Transactional(readOnly = true)
public class ExplainableAllocationService {

    private final CandidateGenerator candidateGenerator;
    private final ScoringEngine scoringEngine;

    public ExplainableAllocationService(CandidateGenerator candidateGenerator, ScoringEngine scoringEngine) {
        this.candidateGenerator = candidateGenerator;
        this.scoringEngine = scoringEngine;
    }

    public AllocationRecommendation recommend(SchedulingRequest request) {
        return recommend(request, SchedulingSearchState.empty());
    }

    /**
     * Identical to {@link #recommend(SchedulingRequest)} except that
     * candidate generation also sees {@code searchState}'s provisional
     * decisions (Phase 14, PART 4/5/21) - reusing this exact pipeline is
     * how the automatic-scheduling engine gets full Phase 11 scoring and
     * Phase 12 explainability for every candidate slot "for free," with no
     * duplicated formula. Every existing caller uses
     * {@link #recommend(SchedulingRequest)} (an empty search state) and is
     * completely unaffected by this addition.
     */
    public AllocationRecommendation recommend(SchedulingRequest request, SchedulingSearchState searchState) {
        CandidateGenerationResult generationResult = candidateGenerator.generate(request, searchState);
        ScoringResult scoringResult = scoringEngine.score(generationResult);

        List<ExplainedValidCandidate> ranked = explainValidCandidates(scoringResult);
        List<RejectedCandidateExplanation> rejected = generationResult.invalidCandidates().stream()
                .map(RejectedCandidateExplanation::from)
                .toList();
        RejectionSummary rejectionSummary = RejectionSummary.from(rejected);

        boolean hasWinner = !ranked.isEmpty();
        RecommendationStatus status = hasWinner ? RecommendationStatus.RECOMMENDED : RecommendationStatus.NO_VALID_CANDIDATE;
        ExplainedValidCandidate recommended = hasWinner ? ranked.get(0) : null;

        List<String> summary = hasWinner
                ? buildRecommendedSummary(recommended, ranked.size())
                : buildNoValidCandidateSummary(generationResult.totalCount(), rejectionSummary);

        return new AllocationRecommendation(
                request,
                status,
                recommended,
                ranked,
                rejected,
                rejectionSummary,
                summary,
                generationResult.totalCount(),
                generationResult.validCount(),
                generationResult.invalidCount());
    }

    private List<ExplainedValidCandidate> explainValidCandidates(ScoringResult scoringResult) {
        List<ExplainedValidCandidate> explained = new ArrayList<>(scoringResult.rankedCandidates().size());
        int rank = 1;
        for (ScoredCandidate scored : scoringResult.rankedCandidates()) {
            List<ConstraintCheckExplanation> checks = constraintChecksFor(scored.evaluatedCandidate());
            explained.add(ExplainedValidCandidate.of(scored, rank, checks));
            rank++;
        }
        return explained;
    }

    /** Converts already-computed {@link ConstraintResult}s (Phase 9) into display-friendly explanations - never re-evaluates HC-11 or any other constraint. */
    private List<ConstraintCheckExplanation> constraintChecksFor(EvaluatedCandidate candidate) {
        SchedulingActor actor = candidate.candidate().context().request().actor();
        String notApplicableReason = actor == null
                ? "no actor context (internal/automated scheduling)"
                : actor.role() == UserRole.LAB_ASSISTANT ? "not applicable for Lab Assistant-originated requests" : null;

        List<ConstraintResult> results = candidate.constraintEvaluation().results();
        List<ConstraintCheckExplanation> checks = new ArrayList<>(results.size());
        for (ConstraintResult result : results) {
            checks.add(ConstraintCheckExplanation.from(result, notApplicableReason));
        }
        return checks;
    }

    /** PART 15/21: factual, non-marketing bullets derived directly from already-computed contributions - no recomputation, no invented prose. */
    private List<String> buildRecommendedSummary(ExplainedValidCandidate recommended, int validCount) {
        List<String> summary = new ArrayList<>();
        summary.add(validCount == 1
                ? "Only one candidate satisfied all hard constraints."
                : "Satisfies all applicable hard constraints (" + validCount + " valid candidates evaluated).");
        for (ScoreContribution contribution : recommended.scoreContributions()) {
            if (contribution.applicability() == ScoreApplicability.APPLIED) {
                summary.add(ScoringFactorLabels.labelFor(contribution.factor()) + ": " + formatPoints(contribution.pointsAwarded())
                        + "/" + formatPoints(contribution.maxPoints()));
            }
        }
        return summary;
    }

    /** PART 59: describes absence of a valid candidate factually, without searching another time (that is Phase 13's job). */
    private List<String> buildNoValidCandidateSummary(int totalEvaluated, RejectionSummary rejectionSummary) {
        List<String> summary = new ArrayList<>();
        summary.add("No valid laboratory satisfies all hard constraints.");
        summary.add(totalEvaluated + " candidate(s) evaluated, " + rejectionSummary.rejectedCount() + " rejected.");
        List<String> mostCommon = rejectionSummary.mostCommonReasons();
        if (!mostCommon.isEmpty()) {
            summary.add("Most common reason: " + String.join(", ", mostCommon) + ".");
        }
        return summary;
    }

    private static String formatPoints(double value) {
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }
}
