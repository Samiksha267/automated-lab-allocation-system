package com.college.laballocation.scheduling.scoring;

import com.college.laballocation.scheduling.SchedulingRequest;
import java.util.List;
import java.util.Objects;

/**
 * {@code ScoringEngine}'s return type - {@code rankedCandidates} is already
 * sorted best-first (PART 31), normalized-score descending then
 * {@code lab.code} ascending as a deterministic tie-breaker (PART 33).
 * Deliberately excludes invalid candidates (PART 36) - those remain
 * retrievable from the {@code CandidateGenerationResult} this result was
 * built from; nothing here duplicates that list.
 *
 * <p>An empty {@code rankedCandidates} (zero valid candidates existed to
 * score) is a normal, non-exceptional result (PART 35) - not an error.
 */
public record ScoringResult(
        SchedulingRequest request, List<ScoredCandidate> rankedCandidates, int validCandidateCount, List<ScoringFactorId> enabledFactors) {

    public ScoringResult {
        Objects.requireNonNull(request, "request must not be null");
        rankedCandidates = List.copyOf(rankedCandidates);
        enabledFactors = List.copyOf(enabledFactors);
    }
}
