package com.college.laballocation.scheduling.generation;

import com.college.laballocation.scheduling.SchedulingRequest;
import java.util.List;
import java.util.Objects;

/**
 * The full outcome of one {@link CandidateGenerator#generate} call - every
 * evaluated candidate, valid and invalid alike (PART 6/8 of the Phase 10
 * brief). Invalid candidates are deliberately preserved, not discarded:
 * Phase 12 (explainability) and Phase 13 (alternatives) both need their
 * attached {@code ConstraintViolation}s later, and re-running generation
 * just to recover a rejection reason would be wasted work.
 *
 * <p>Zero valid candidates is a normal, successful result here - never an
 * exception (PART 29/48). Nothing about this type ranks or scores; that is
 * Phase 11.
 */
public record CandidateGenerationResult(SchedulingRequest request, List<EvaluatedCandidate> evaluatedCandidates) {

    public CandidateGenerationResult {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(evaluatedCandidates, "evaluatedCandidates must not be null");
        for (EvaluatedCandidate candidate : evaluatedCandidates) {
            Objects.requireNonNull(candidate, "evaluatedCandidates must not contain a null element");
        }
        evaluatedCandidates = List.copyOf(evaluatedCandidates);
    }

    public List<EvaluatedCandidate> validCandidates() {
        return evaluatedCandidates.stream().filter(EvaluatedCandidate::isValid).toList();
    }

    public List<EvaluatedCandidate> invalidCandidates() {
        return evaluatedCandidates.stream().filter(c -> !c.isValid()).toList();
    }

    public int totalCount() {
        return evaluatedCandidates.size();
    }

    public int validCount() {
        return (int) evaluatedCandidates.stream().filter(EvaluatedCandidate::isValid).count();
    }

    public int invalidCount() {
        return totalCount() - validCount();
    }
}
