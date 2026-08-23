package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.faculty.FacultyAvailabilityService;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * HC-03 - Faculty Availability. Wraps the existing, already-tested
 * {@link FacultyAvailabilityService#isAvailable} (Phase 7) as a
 * {@code SchedulingConstraint} - the merging/containment logic is not
 * duplicated here. Day-of-week is always derived from
 * {@link SchedulingRequest#allocationDate()} (never accepted independently),
 * so date and day-of-week can never disagree.
 */
@Component
public class FacultyAvailabilityConstraint implements SchedulingConstraint {

    private final FacultyAvailabilityService facultyAvailabilityService;

    public FacultyAvailabilityConstraint(FacultyAvailabilityService facultyAvailabilityService) {
        this.facultyAvailabilityService = facultyAvailabilityService;
    }

    @Override
    public HardConstraintId id() {
        return HardConstraintId.HC_03_FACULTY_AVAILABILITY;
    }

    @Override
    public ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate) {
        SchedulingRequest request = context.request();
        boolean available = facultyAvailabilityService.isAvailable(
                request.facultyId(),
                request.academicTermId(),
                request.allocationDate().getDayOfWeek(),
                request.startTime(),
                request.endTime());
        if (!available) {
            return ConstraintResult.fail(
                    id(),
                    new ConstraintViolation(
                            "FACULTY_UNAVAILABLE",
                            "Faculty " + context.faculty().employeeCode() + " is not available "
                                    + request.allocationDate().getDayOfWeek() + " " + request.startTime() + "-" + request.endTime()
                                    + ".",
                            "FACULTY",
                            context.faculty().employeeCode(),
                            Map.of(
                                    "facultyId", context.faculty().id(),
                                    "dayOfWeek", request.allocationDate().getDayOfWeek().toString(),
                                    "requestedStartTime", request.startTime().toString(),
                                    "requestedEndTime", request.endTime().toString())));
        }
        return ConstraintResult.pass(id());
    }
}
