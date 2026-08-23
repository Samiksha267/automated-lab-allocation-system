package com.college.laballocation.scheduling.scoring;

import com.college.laballocation.scheduling.generation.EvaluatedCandidate;
import java.util.List;
import java.util.Objects;

/**
 * One valid candidate's ranked scoring result - the breakdown is preserved
 * (PART 8/52), never collapsed to a single number, so a future explanation
 * layer (Phase 12) can compose "why this lab" without re-running any scorer.
 * Transient, like {@link EvaluatedCandidate} and {@code CandidateAllocation}
 * - never persisted (PART 8, PART 38).
 *
 * <p>{@code maxPossibleScore} is the sum of {@code maxPoints} only across
 * {@link ScoreApplicability#APPLIED} contributions (PART 9) - a factor that
 * did not apply to this candidate (e.g. no preferred lab type) contributes
 * nothing to either the numerator or the denominator, so a subject with
 * fewer applicable preferences is never penalized relative to one with more.
 */
public record ScoredCandidate(
        EvaluatedCandidate evaluatedCandidate, List<ScoreContribution> contributions, double totalScore, double maxPossibleScore) {

    public ScoredCandidate {
        Objects.requireNonNull(evaluatedCandidate, "evaluatedCandidate must not be null");
        if (!evaluatedCandidate.isValid()) {
            throw new IllegalArgumentException("ScoredCandidate must wrap a valid EvaluatedCandidate; lab "
                    + evaluatedCandidate.candidate().lab().code() + " is invalid and must never be scored.");
        }
        contributions = List.copyOf(contributions);
    }

    public String labCode() {
        return evaluatedCandidate.candidate().lab().code();
    }

    /** {@code 0.0} (never a divide-by-zero) when every factor was {@code NOT_APPLICABLE} for this candidate. */
    public double normalizedScore() {
        return maxPossibleScore == 0 ? 0.0 : totalScore / maxPossibleScore;
    }
}
