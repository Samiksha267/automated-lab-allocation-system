package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.common.TimeIntervalUtils;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.InstantRange;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingTimeMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * HC-06 - Lab Availability. Two components (docs/06-CONSTRAINTS.md):
 *
 * <ol>
 *   <li>Permanent: {@code lab.active == false} - a retired lab is never a
 *       valid candidate, checked defensively here even though Phase 10's
 *       candidate generation should never offer one in the first place.</li>
 *   <li>Temporary: the candidate interval must not overlap any
 *       {@code LabUnavailability} window, compared in {@link java.time.Instant}
 *       space via {@link SchedulingTimeMapper} - the resolved bridge between
 *       {@code Allocation}'s {@code LocalDate}/{@code LocalTime} and
 *       {@code LabUnavailability}'s native {@code TIMESTAMPTZ}/{@code Instant}
 *       (ADR-037). No timezone conversion is written here directly.</li>
 * </ol>
 */
@Component
public class LabAvailabilityConstraint implements SchedulingConstraint {

    private final SchedulingTimeMapper schedulingTimeMapper;

    public LabAvailabilityConstraint(SchedulingTimeMapper schedulingTimeMapper) {
        this.schedulingTimeMapper = schedulingTimeMapper;
    }

    @Override
    public HardConstraintId id() {
        return HardConstraintId.HC_06_LAB_AVAILABILITY;
    }

    @Override
    public ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate) {
        if (!candidate.lab().active()) {
            return ConstraintResult.fail(
                    id(),
                    new ConstraintViolation(
                            "LAB_UNAVAILABLE",
                            "Lab " + candidate.lab().code() + " is permanently inactive.",
                            "LAB",
                            candidate.lab().code(),
                            Map.of("reason", "PERMANENTLY_INACTIVE")));
        }

        var request = context.request();
        InstantRange requested = schedulingTimeMapper.toInstantRange(request.allocationDate(), request.startTime(), request.endTime());

        for (InstantRange window : candidate.lab().unavailabilityWindows()) {
            if (TimeIntervalUtils.overlaps(requested.start(), requested.end(), window.start(), window.end())) {
                return ConstraintResult.fail(
                        id(),
                        new ConstraintViolation(
                                "LAB_UNAVAILABLE",
                                "Lab " + candidate.lab().code() + " is administratively unavailable during the requested interval.",
                                "LAB",
                                candidate.lab().code(),
                                Map.of(
                                        "unavailableFrom", window.start().toString(),
                                        "unavailableUntil", window.end().toString())));
            }
        }
        return ConstraintResult.pass(id());
    }
}
