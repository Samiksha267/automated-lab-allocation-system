package com.college.laballocation.scheduling;

import com.college.laballocation.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The outcome of one {@code SchedulingConstraint} (Phase 9) run against one
 * candidate. Deliberately carries no score - hard validation and soft
 * scoring are never mixed (docs/07-ALLOCATION-SCORING.md).
 *
 * <p>Extended in Phase 9 (originally a {@code boolean passed} in Phase 8) to
 * a three-way {@link ConstraintOutcome} - PASS/FAIL/NOT_APPLICABLE - since
 * HC-11 (CR Authorization) genuinely needs to distinguish "this candidate
 * satisfies the rule" from "this rule doesn't apply to this request at all"
 * (docs/15-DESIGN-DECISIONS.md). No other code in the repository depended on
 * the Phase 8 shape, so this is a clean evolution, not a breaking change to
 * a real consumer.
 */
public record ConstraintResult(HardConstraintId constraintId, ConstraintOutcome outcome, ConstraintViolation violation) {

    public ConstraintResult {
        if (outcome == ConstraintOutcome.FAIL && violation == null) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "A FAIL ConstraintResult must carry a violation.");
        }
        if (outcome != ConstraintOutcome.FAIL && violation != null) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Only a FAIL ConstraintResult may carry a violation.");
        }
    }

    public static ConstraintResult pass(HardConstraintId constraintId) {
        return new ConstraintResult(constraintId, ConstraintOutcome.PASS, null);
    }

    public static ConstraintResult fail(HardConstraintId constraintId, ConstraintViolation violation) {
        return new ConstraintResult(constraintId, ConstraintOutcome.FAIL, violation);
    }

    public static ConstraintResult notApplicable(HardConstraintId constraintId) {
        return new ConstraintResult(constraintId, ConstraintOutcome.NOT_APPLICABLE, null);
    }

    /** {@code true} for PASS and NOT_APPLICABLE alike - both are non-failing for overall candidate validity. */
    public boolean passed() {
        return outcome != ConstraintOutcome.FAIL;
    }
}
