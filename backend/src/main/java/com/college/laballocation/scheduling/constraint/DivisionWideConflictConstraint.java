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
 * HC-05 - Division-Wide Conflict. Bidirectional (docs/06-CONSTRAINTS.md):
 *
 * <ul>
 *   <li>Candidate {@code DIVISION}: rejected by any overlapping existing
 *       allocation in the same division, whether the existing row is
 *       {@code DIVISION} or {@code BATCH}-targeted - a division-wide session
 *       occupies every batch beneath it.</li>
 *   <li>Candidate {@code BATCH}: rejected only by an overlapping existing
 *       {@code DIVISION}-targeted allocation for its division - a sibling
 *       {@code BATCH} row is never this constraint's concern (that pairing
 *       is always valid; same-batch overlap is {@link BatchConflictConstraint},
 *       HC-04).</li>
 * </ul>
 *
 * <p>{@code context.existingDivisionAllocations()} already contains both
 * {@code DIVISION}-wide and {@code BATCH} rows for the division in one query
 * (every {@code Allocation} row carries {@code division_id} regardless of
 * {@code target_type} - docs/04-DATABASE-DESIGN.md §7) - this constraint only
 * needs to filter by {@code targetType}, never run a second joined query.
 */
@Component
public class DivisionWideConflictConstraint implements SchedulingConstraint {

    @Override
    public HardConstraintId id() {
        return HardConstraintId.HC_05_DIVISION_CONFLICT;
    }

    @Override
    public ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate) {
        var request = context.request();
        boolean candidateIsDivision = request.targetType() == TargetType.DIVISION;

        for (ExistingAllocationSnapshot existing : context.existingDivisionAllocations()) {
            boolean relevant = candidateIsDivision || existing.targetType() == TargetType.DIVISION;
            if (!relevant || !existing.allocationDate().equals(request.allocationDate())) {
                continue;
            }
            if (TimeIntervalUtils.overlaps(existing.startTime(), existing.endTime(), request.startTime(), request.endTime())) {
                return ConstraintResult.fail(
                        id(),
                        new ConstraintViolation(
                                "DIVISION_CONFLICT",
                                "Division " + context.division().code() + " already has an overlapping "
                                        + existing.targetType() + " allocation (" + existing.startTime() + "-" + existing.endTime()
                                        + ").",
                                "DIVISION",
                                context.division().code(),
                                Map.of(
                                        "divisionId", context.division().id(),
                                        "existingAllocationId", existing.allocationId(),
                                        "existingTargetType", existing.targetType().toString())));
            }
        }
        return ConstraintResult.pass(id());
    }
}
