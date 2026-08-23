package com.college.laballocation.scheduling.explanation;

import com.college.laballocation.scheduling.scoring.ScoreContribution;
import com.college.laballocation.scheduling.scoring.ScoredCandidate;
import java.util.List;
import java.util.Objects;

/**
 * A ranked, explainable view of one already-{@link ScoredCandidate valid,
 * scored candidate} (Phase 11) - never recomputes a formula (PART 38). Used
 * for both the recommended candidate (rank 1) and every other ranked valid
 * candidate ("ranked valid alternatives," PART 42 - deliberately not called
 * "alternative scheduling suggestions," a term reserved for Phase 13's
 * different-time/different-lab search).
 *
 * <p>{@code scoreContributions} is the exact, unmodified Phase 11 breakdown -
 * this type only adds {@code rank} and the constraint-pass summary on top,
 * never re-deriving score data (PART 11's exact contribution metadata must
 * be preserved verbatim).
 */
public record ExplainedValidCandidate(
        Long labId,
        String labCode,
        int rank,
        double score,
        double applicableMaxScore,
        double normalizedScore,
        List<ScoreContribution> scoreContributions,
        List<ConstraintCheckExplanation> constraintChecks) {

    public ExplainedValidCandidate {
        Objects.requireNonNull(labId, "labId must not be null");
        Objects.requireNonNull(labCode, "labCode must not be null");
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1, got " + rank);
        }
        scoreContributions = List.copyOf(scoreContributions);
        constraintChecks = List.copyOf(constraintChecks);
    }

    public static ExplainedValidCandidate of(ScoredCandidate scored, int rank, List<ConstraintCheckExplanation> constraintChecks) {
        return new ExplainedValidCandidate(
                scored.evaluatedCandidate().candidate().lab().id(),
                scored.labCode(),
                rank,
                scored.totalScore(),
                scored.maxPossibleScore(),
                scored.normalizedScore(),
                scored.contributions(),
                constraintChecks);
    }
}
