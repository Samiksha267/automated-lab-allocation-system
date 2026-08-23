package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.common.TimeIntervalUtils;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.ExistingAllocationSnapshot;
import com.college.laballocation.scheduling.TargetType;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * HC-04 - Batch Conflict. For a {@code BATCH} candidate, the same batch
 * cannot have two overlapping active allocations. A different batch in the
 * same division being busy is irrelevant here - that scenario (A1 and A2
 * both booking simultaneously) must pass this constraint; see
 * {@code BatchConflictConstraintTest}.
 *
 * <p>Deliberately does not check DIVISION-wide occupancy - a BATCH candidate
 * blocked by an overlapping DIVISION-type allocation is
 * {@link DivisionWideConflictConstraint}'s responsibility (HC-05), keeping
 * this constraint focused purely on same-batch overlap (PART 18 of the
 * Phase 9 brief).
 *
 * <p>A {@code DIVISION}-targeted candidate has no batch to conflict on -
 * this constraint passes vacuously for it (not NOT_APPLICABLE: the rule
 * "the same batch has no overlap" is trivially, actually true when there is
 * no batch, the same reasoning HC-08/09 use for an empty requirement set).
 */
@Component
public class BatchConflictConstraint implements SchedulingConstraint {

    @Override
    public HardConstraintId id() {
        return HardConstraintId.HC_04_BATCH_CONFLICT;
    }

    @Override
    public ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate) {
        if (context.request().targetType() != TargetType.BATCH) {
            return ConstraintResult.pass(id());
        }
        var request = context.request();
        for (ExistingAllocationSnapshot existing : context.existingBatchAllocations()) {
            if (existing.allocationDate().equals(request.allocationDate())
                    && TimeIntervalUtils.overlaps(existing.startTime(), existing.endTime(), request.startTime(), request.endTime())) {
                return ConstraintResult.fail(
                        id(),
                        new ConstraintViolation(
                                "BATCH_CONFLICT",
                                "Batch " + context.batch().code() + " already has an overlapping allocation ("
                                        + existing.startTime() + "-" + existing.endTime() + ").",
                                "BATCH",
                                context.batch().code(),
                                Map.of(
                                        "batchId", context.batch().id(),
                                        "existingAllocationId", existing.allocationId())));
            }
        }
        return ConstraintResult.pass(id());
    }
}
