package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import java.util.List;

/**
 * The aggregate outcome of running every applicable hard constraint against
 * one {@code CandidateAllocation} - {@link ConstraintEngine}'s return type.
 * {@code valid} is {@code true} iff no result's outcome is
 * {@link ConstraintOutcome#FAIL} (NOT_APPLICABLE never blocks validity, PASS
 * obviously doesn't either).
 *
 * <p>Deliberately not {@code AllocationDecision} - that type is reserved for
 * Phase 12 (Explainable Allocation), once scoring (Phase 11) and alternative
 * suggestions (Phase 13) exist to give it a real shape. This is purely the
 * constraint-validity answer, nothing about ranking or explanation beyond
 * the per-constraint results themselves.
 */
public record ConstraintEvaluation(boolean valid, List<ConstraintResult> results, List<ConstraintViolation> violations) {

    public ConstraintEvaluation {
        results = List.copyOf(results);
        violations = List.copyOf(violations);
    }

    public static ConstraintEvaluation of(List<ConstraintResult> results) {
        List<ConstraintViolation> violations = results.stream()
                .filter(r -> r.outcome() == ConstraintOutcome.FAIL)
                .map(ConstraintResult::violation)
                .toList();
        return new ConstraintEvaluation(violations.isEmpty(), results, violations);
    }
}
