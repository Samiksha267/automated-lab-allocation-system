package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.TargetType;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * HC-07 - Capacity. Required capacity is {@code batch.strength} for a
 * {@code BATCH} candidate, {@code division.strength} for {@code DIVISION}.
 * Pure ≥ comparison, no fit scoring - a 150-seat lab is a valid candidate
 * even if inefficient; ranking labs by how tightly capacity fits is Phase 11
 * (docs/07-ALLOCATION-SCORING.md's Capacity Fit factor), never a hard
 * rejection here.
 */
@Component
public class CapacityConstraint implements SchedulingConstraint {

    @Override
    public HardConstraintId id() {
        return HardConstraintId.HC_07_CAPACITY;
    }

    @Override
    public ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate) {
        int required = context.request().targetType() == TargetType.BATCH
                ? context.batch().strength()
                : context.division().strength();
        int capacity = candidate.lab().capacity();

        if (capacity < required) {
            return ConstraintResult.fail(
                    id(),
                    new ConstraintViolation(
                            "CAPACITY_VIOLATION",
                            "Lab capacity " + capacity + " is below required capacity " + required + ".",
                            "LAB",
                            candidate.lab().code(),
                            Map.of("requiredCapacity", required, "labCapacity", capacity)));
        }
        return ConstraintResult.pass(id());
    }
}
