package com.college.laballocation.scheduling.scoring;

/**
 * Whether a scoring factor actually differentiated this candidate.
 * {@link #NOT_APPLICABLE} means the factor contributed zero points AND zero
 * to the applicable maximum - e.g. a subject with no preferred lab type
 * should not be penalized for "failing" a preference it never expressed
 * (PART 17/19 of the Phase 11 brief). {@link #APPLIED} means the factor was
 * evaluated for real, even if the result was zero points (e.g. a genuine
 * preferred-type mismatch still counts toward the applicable maximum).
 */
public enum ScoreApplicability {
    APPLIED,
    NOT_APPLICABLE
}
