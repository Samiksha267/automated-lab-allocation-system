package com.college.laballocation.scheduling;

import com.college.laballocation.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The pass/fail outcome of one {@code SchedulingConstraint} (Phase 9) run
 * against one candidate. Deliberately carries no score - hard validation and
 * soft scoring are never mixed (docs/07-ALLOCATION-SCORING.md).
 */
public record ConstraintResult(HardConstraintId constraintId, boolean passed, ConstraintViolation violation) {

    public ConstraintResult {
        if (passed && violation != null) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "A passing ConstraintResult must not carry a violation.");
        }
        if (!passed && violation == null) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "A failing ConstraintResult must carry a violation.");
        }
    }

    public static ConstraintResult pass(HardConstraintId constraintId) {
        return new ConstraintResult(constraintId, true, null);
    }

    public static ConstraintResult fail(HardConstraintId constraintId, ConstraintViolation violation) {
        return new ConstraintResult(constraintId, false, violation);
    }
}
