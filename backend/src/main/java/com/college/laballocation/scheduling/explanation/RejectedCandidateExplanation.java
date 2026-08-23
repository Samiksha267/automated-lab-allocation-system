package com.college.laballocation.scheduling.explanation;

import com.college.laballocation.scheduling.generation.EvaluatedCandidate;
import java.util.List;
import java.util.Objects;

/**
 * An explainable view of one invalid candidate (Phase 10) - carries every
 * {@link ViolationExplanation} it failed, never collapsed to a single
 * message (PART 7/33 of the Phase 12 brief: a candidate failing capacity,
 * software, AND lab availability retains all three, not just the first).
 *
 * <p>Deliberately has no score field at all, not even a nullable one set to
 * {@code null} - an invalid candidate was never scored (PART 28), and a
 * missing field is a stronger guarantee than a nullable one a caller could
 * forget to check.
 */
public record RejectedCandidateExplanation(Long labId, String labCode, List<ViolationExplanation> violations) {

    public RejectedCandidateExplanation {
        Objects.requireNonNull(labId, "labId must not be null");
        Objects.requireNonNull(labCode, "labCode must not be null");
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("A rejected candidate must carry at least one violation");
        }
        violations = List.copyOf(violations);
    }

    public static RejectedCandidateExplanation from(EvaluatedCandidate invalidCandidate) {
        if (invalidCandidate.isValid()) {
            throw new IllegalArgumentException(
                    "RejectedCandidateExplanation.from() requires an invalid EvaluatedCandidate; lab "
                            + invalidCandidate.candidate().lab().code() + " is valid.");
        }
        List<ViolationExplanation> violations =
                invalidCandidate.violations().stream().map(ViolationExplanation::from).toList();
        return new RejectedCandidateExplanation(
                invalidCandidate.candidate().lab().id(), invalidCandidate.candidate().lab().code(), violations);
    }
}
