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
 * HC-02 - Faculty Conflict. The same faculty member cannot conduct two
 * overlapping active allocations, independent of lab. Reads
 * {@code context.existingFacultyAllocations()} - candidate-independent,
 * precomputed once per {@code SchedulingContext} rather than per candidate
 * (unlike HC-01, faculty conflict never depends on which lab is being
 * evaluated).
 *
 * <p>Distinct from HC-03 (Faculty Availability): a faculty can be generally
 * *available* at a time they are already *booked* elsewhere - availability
 * is a declared weekly boundary, conflict is real allocation data. Both
 * checks run independently; neither substitutes for the other.
 */
@Component
public class FacultyConflictConstraint implements SchedulingConstraint {

    @Override
    public HardConstraintId id() {
        return HardConstraintId.HC_02_FACULTY_CONFLICT;
    }

    @Override
    public ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate) {
        var request = context.request();
        for (ExistingAllocationSnapshot existing : context.existingFacultyAllocations()) {
            if (existing.allocationDate().equals(request.allocationDate())
                    && TimeIntervalUtils.overlaps(existing.startTime(), existing.endTime(), request.startTime(), request.endTime())) {
                return ConstraintResult.fail(
                        id(),
                        new ConstraintViolation(
                                "FACULTY_CONFLICT",
                                "Faculty " + context.faculty().employeeCode() + " is already allocated during "
                                        + existing.startTime() + "-" + existing.endTime() + ".",
                                "FACULTY",
                                context.faculty().employeeCode(),
                                Map.of(
                                        "facultyId", context.faculty().id(),
                                        "existingAllocationId", existing.allocationId())));
            }
        }
        return ConstraintResult.pass(id());
    }
}
