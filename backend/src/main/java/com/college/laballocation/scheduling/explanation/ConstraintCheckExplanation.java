package com.college.laballocation.scheduling.explanation;

import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import java.util.Objects;

/**
 * A display-friendly wrapper around one already-computed {@link ConstraintResult}
 * (Phase 9) - never a re-evaluation (PART 39 of the Phase 12 brief). Preserves
 * PASS/FAIL/NOT_APPLICABLE exactly as the {@code ConstraintEngine} produced
 * it; {@code detail} is only populated for FAIL (the violation's message) and
 * NOT_APPLICABLE (a fixed, accurate reason) - never fabricated for PASS,
 * which is self-explanatory via {@code displayLabel} + outcome alone (PART 9).
 *
 * <p>NOT_APPLICABLE is never rendered as "passed" (PART 10/52) - callers must
 * branch on {@link #outcome()}, never assume {@code outcome != FAIL} means
 * "this rule was satisfied."
 */
public record ConstraintCheckExplanation(HardConstraintId constraintId, String displayLabel, ConstraintOutcome outcome, String detail) {

    public ConstraintCheckExplanation {
        Objects.requireNonNull(constraintId, "constraintId must not be null");
        Objects.requireNonNull(displayLabel, "displayLabel must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
    }

    /**
     * @param actorDescription only consulted when {@code result.outcome() == NOT_APPLICABLE}, to
     *     describe *why* without re-evaluating HC-11 - e.g. "no actor context (internal/automated
     *     scheduling)" or "not applicable for Lab Assistant-originated requests". Ignored otherwise.
     */
    public static ConstraintCheckExplanation from(ConstraintResult result, String actorDescription) {
        String label = HardConstraintLabels.labelFor(result.constraintId());
        String detail =
                switch (result.outcome()) {
                    case PASS -> null;
                    case FAIL -> result.violation().message();
                    case NOT_APPLICABLE -> "Not applicable" + (actorDescription != null ? " - " + actorDescription : "") + ".";
                };
        return new ConstraintCheckExplanation(result.constraintId(), label, result.outcome(), detail);
    }
}
