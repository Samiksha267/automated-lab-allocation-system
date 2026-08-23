package com.college.laballocation.scheduling.explanation;

import com.college.laballocation.scheduling.ConstraintViolation;
import java.util.Map;
import java.util.Objects;

/**
 * A display-friendly wrapper around one already-computed {@link ConstraintViolation}
 * (Phase 9) - the machine {@code errorCode} is preserved unchanged alongside
 * a short {@code displayLabel}, and {@code message}/{@code details} are
 * copied as-is, never regenerated (PART 8/35/38 of the Phase 12 brief).
 */
public record ViolationExplanation(
        String errorCode, String displayLabel, String message, String affectedResourceType, String affectedResourceId, Map<String, Object> details) {

    public ViolationExplanation {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        Objects.requireNonNull(displayLabel, "displayLabel must not be null");
        Objects.requireNonNull(message, "message must not be null");
        details = Map.copyOf(details);
    }

    public static ViolationExplanation from(ConstraintViolation violation) {
        return new ViolationExplanation(
                violation.errorCode(),
                ViolationErrorCodeLabels.labelFor(violation.errorCode()),
                violation.message(),
                violation.affectedResourceType(),
                violation.affectedResourceId(),
                violation.details());
    }
}
