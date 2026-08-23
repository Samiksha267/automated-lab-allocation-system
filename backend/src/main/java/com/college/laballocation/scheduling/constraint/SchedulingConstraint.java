package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;

/**
 * One independently-testable hard constraint (HC-01..HC-12,
 * docs/06-CONSTRAINTS.md). Every implementation is a Spring
 * {@code @Component}, auto-discovered by {@link ConstraintEngine} - adding a
 * future constraint never requires touching the engine itself.
 *
 * <p>Implementations must be read-only (PART 43 of the Phase 9 brief): no
 * mutation of {@code context}, {@code candidate}, or the database. Expected,
 * ordinary invalidity (a real conflict, a missing requirement) is always
 * represented as {@link ConstraintResult#fail}, never a thrown exception -
 * exceptions are reserved for genuinely unexpected technical/domain-state
 * failures (e.g. a referenced entity vanishing mid-transaction), the same
 * distinction this project draws everywhere else between a domain rejection
 * and a bug.
 */
public interface SchedulingConstraint {

    HardConstraintId id();

    ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate);
}
