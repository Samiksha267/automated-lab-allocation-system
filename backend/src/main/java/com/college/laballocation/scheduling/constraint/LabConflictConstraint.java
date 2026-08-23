package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.common.TimeIntervalUtils;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.ExistingAllocationSnapshot;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * HC-01 - Lab Conflict (docs/06-CONSTRAINTS.md). One lab hosts at most one
 * active allocation at any overlapping instant, regardless of
 * {@code target_type}/{@code allocation_type}. Reads
 * {@code candidate.lab().existingAllocations()} - already scoped to this
 * lab and the requested date by {@code CandidateAllocationFactory}
 * (via {@code AllocationQueryService.findActiveForLab}, which itself only
 * ever returns {@code AllocationStatus.blocksScheduling()} rows - a
 * CANCELLED allocation is never a candidate here). Also re-checks
 * {@code allocationDate} equality defensively rather than trusting the
 * upstream query's date filter alone - never treats recurring-time equality
 * across two different dates as a conflict.
 */
@Component
public class LabConflictConstraint implements SchedulingConstraint {

    @Override
    public HardConstraintId id() {
        return HardConstraintId.HC_01_LAB_CONFLICT;
    }

    @Override
    public ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate) {
        var request = context.request();
        for (ExistingAllocationSnapshot existing : candidate.lab().existingAllocations()) {
            if (existing.allocationDate().equals(request.allocationDate())
                    && TimeIntervalUtils.overlaps(existing.startTime(), existing.endTime(), request.startTime(), request.endTime())) {
                return ConstraintResult.fail(
                        id(),
                        new ConstraintViolation(
                                "LAB_CONFLICT",
                                "Lab " + candidate.lab().code() + " already hosts an overlapping allocation ("
                                        + existing.startTime() + "-" + existing.endTime() + ").",
                                "LAB",
                                candidate.lab().code(),
                                Map.of(
                                        "existingAllocationId", existing.allocationId(),
                                        "existingStartTime", existing.startTime().toString(),
                                        "existingEndTime", existing.endTime().toString())));
            }
        }
        return ConstraintResult.pass(id());
    }
}
