package com.college.laballocation.scheduling.constraint;

import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.MONDAY;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchContext;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.candidate;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.inactiveLab;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.lab;
import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.InstantRange;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.LabRef;
import com.college.laballocation.scheduling.SchedulingTimeMapper;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Uses a real, fixed-zone {@link SchedulingTimeMapper} - never depends on the test machine's local timezone. */
class LabAvailabilityConstraintTest {

    private final SchedulingTimeMapper timeMapper = new SchedulingTimeMapper("Asia/Kolkata");
    private final LabAvailabilityConstraint constraint = new LabAvailabilityConstraint(timeMapper);

    @Test
    void idIsHc06() {
        assertThat(constraint.id()).isEqualTo(HardConstraintId.HC_06_LAB_AVAILABILITY);
    }

    @Test
    void failsWhenLabIsPermanentlyInactive() {
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));
        ConstraintResult result = constraint.evaluate(context, candidate(context, inactiveLab()));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("LAB_UNAVAILABLE");
    }

    @Test
    void passesWhenNoUnavailabilityWindows() {
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));
        ConstraintResult result = constraint.evaluate(context, candidate(context));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void failsWhenCandidateOverlapsAnUnavailabilityWindow() {
        InstantRange window = timeMapper.toInstantRange(MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0));
        LabRef lab = lab(70, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of(window));
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(11, 0), LocalTime.of(13, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, lab));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("LAB_UNAVAILABLE");
    }

    @Test
    void passesWhenCandidateStartsExactlyWhenUnavailabilityEnds() {
        InstantRange window = timeMapper.toInstantRange(MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0));
        LabRef lab = lab(70, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of(window));
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(12, 0), LocalTime.of(14, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, lab));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }
}
