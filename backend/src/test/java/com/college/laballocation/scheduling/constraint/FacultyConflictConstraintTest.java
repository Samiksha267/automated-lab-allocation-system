package com.college.laballocation.scheduling.constraint;

import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchContextWith;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.candidate;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.existing;
import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.TargetType;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FacultyConflictConstraintTest {

    private final FacultyConflictConstraint constraint = new FacultyConflictConstraint();

    @Test
    void idIsHc02() {
        assertThat(constraint.id()).isEqualTo(HardConstraintId.HC_02_FACULTY_CONFLICT);
    }

    @Test
    void passesWhenFacultyHasNoExistingAllocations() {
        SchedulingContext context = batchContextWith(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)), List.of(), List.of(), List.of());
        ConstraintResult result = constraint.evaluate(context, candidate(context));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void failsWhenSameFacultyIsAlreadyAllocatedOverlappingInADifferentLab() {
        var existing = existing(88L, 999L, 4L, 1L, 2L, TargetType.BATCH, LocalTime.of(9, 0), LocalTime.of(11, 0));
        SchedulingContext context =
                batchContextWith(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)), List.of(existing), List.of(), List.of());

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("FACULTY_CONFLICT");
    }

    @Test
    void differentFacultySimultaneouslyInDifferentLabsIsNotThisConstraintsProblem() {
        // The A1/A2 scenario, faculty-conflict half: with no existing rows in
        // *this* faculty's snapshot at all, HC-02 passes regardless of what
        // other faculty are doing.
        SchedulingContext context = batchContextWith(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)), List.of(), List.of(), List.of());
        ConstraintResult result = constraint.evaluate(context, candidate(context));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }
}
