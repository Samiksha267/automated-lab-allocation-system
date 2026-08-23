package com.college.laballocation.scheduling.conflict;

/**
 * Whether a hard-constraint failure could plausibly be solved by changing
 * the requested time (PART 5 of the Phase 13 brief). {@link #TEMPORAL}
 * failures (a lab/faculty/batch/division already occupied, a faculty
 * unavailable, a lab temporarily closed) can genuinely change if the time
 * changes. {@link #STRUCTURAL} failures (capacity, missing software,
 * missing equipment, wrong lab type, an invalid academic relationship, or
 * an authorization failure) describe a fact about the candidate/request
 * that is true at every time of day - moving the session to 11:00 instead
 * of 09:00 cannot make a lab acquire a piece of software it doesn't have.
 */
public enum ConflictCategory {
    STRUCTURAL,
    TEMPORAL
}
