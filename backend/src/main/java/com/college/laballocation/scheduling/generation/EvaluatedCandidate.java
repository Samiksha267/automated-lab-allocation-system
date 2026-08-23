package com.college.laballocation.scheduling.generation;

import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.constraint.ConstraintEvaluation;
import java.util.List;

/**
 * One candidate lab paired with its {@link ConstraintEvaluation} - transient
 * algorithm state, never persisted (PART 7/20 of the Phase 10 brief). Valid
 * *at evaluation time* only; it is not a booking, and nothing about
 * generating this record reserves the lab or writes to the database.
 *
 * <p>{@code isValid()}/{@code violations()} are thin delegates to the
 * already-computed {@link ConstraintEvaluation} - no logic is duplicated
 * here, and no candidate needs to be re-evaluated later to answer "why did
 * this fail" (PART 61): the full {@link ConstraintViolation} list from
 * Phase 9 is already attached.
 */
public record EvaluatedCandidate(CandidateAllocation candidate, ConstraintEvaluation constraintEvaluation) {

    public boolean isValid() {
        return constraintEvaluation.valid();
    }

    public List<ConstraintViolation> violations() {
        return constraintEvaluation.violations();
    }
}
