package com.college.laballocation.scheduling;

/**
 * The three possible outcomes of one {@code SchedulingConstraint} evaluation
 * (Phase 9). {@code NOT_APPLICABLE} is deliberately distinct from
 * {@code PASS} - a constraint that was never meaningfully evaluated (e.g.
 * HC-11 CR Authorization when the scheduling request carries no CR actor at
 * all, such as automated REGULAR generation) is not the same claim as "this
 * candidate satisfies this rule." Both count as non-failing for overall
 * candidate validity, but only {@code PASS} means the rule was actually
 * checked - a distinction future explainability (Phase 12) may need.
 */
public enum ConstraintOutcome {
    PASS,
    FAIL,
    NOT_APPLICABLE
}
