package com.college.laballocation.scheduling.constraint;

import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.FACULTY_ID;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.MONDAY;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.TERM_ID;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchContext;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.candidate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.college.laballocation.faculty.FacultyAvailabilityService;
import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FacultyAvailabilityConstraintTest {

    @Mock
    private FacultyAvailabilityService facultyAvailabilityService;

    @Test
    void idIsHc03() {
        assertThat(new FacultyAvailabilityConstraint(facultyAvailabilityService).id())
                .isEqualTo(HardConstraintId.HC_03_FACULTY_AVAILABILITY);
    }

    @Test
    void passesWhenServiceReportsAvailable() {
        when(facultyAvailabilityService.isAvailable(FACULTY_ID, TERM_ID, MONDAY.getDayOfWeek(), LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .thenReturn(true);
        FacultyAvailabilityConstraint constraint = new FacultyAvailabilityConstraint(facultyAvailabilityService);
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(10, 0), LocalTime.of(11, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void failsWhenServiceReportsUnavailable() {
        when(facultyAvailabilityService.isAvailable(FACULTY_ID, TERM_ID, MONDAY.getDayOfWeek(), LocalTime.of(12, 30), LocalTime.of(13, 30)))
                .thenReturn(false);
        FacultyAvailabilityConstraint constraint = new FacultyAvailabilityConstraint(facultyAvailabilityService);
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(12, 30), LocalTime.of(13, 30)));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("FACULTY_UNAVAILABLE");
    }

    @Test
    void dayOfWeekIsDerivedFromAllocationDateNeverSuppliedIndependently() {
        // MONDAY is 2026-08-24 - the service must be called with DayOfWeek.MONDAY,
        // proving the constraint derives it from allocationDate rather than
        // accepting a separately-supplied day.
        when(facultyAvailabilityService.isAvailable(
                        FACULTY_ID, TERM_ID, java.time.DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .thenReturn(true);
        FacultyAvailabilityConstraint constraint = new FacultyAvailabilityConstraint(facultyAvailabilityService);
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(10, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void availabilityAndConflictAreIndependentDistinctChecks() {
        // PART 11 of the Phase 9 brief: a faculty generally available 09:00-12:00
        // but already booked 09:00-11:00 must PASS FacultyAvailabilityConstraint
        // and FAIL FacultyConflictConstraint for a candidate at 09:00-11:00 -
        // neither check substitutes for the other.
        when(facultyAvailabilityService.isAvailable(FACULTY_ID, TERM_ID, MONDAY.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(11, 0)))
                .thenReturn(true);
        var existingBooking = SchedulingFixtures.existing(
                88L, 999L, FACULTY_ID, 1L, 2L, com.college.laballocation.scheduling.TargetType.BATCH, LocalTime.of(9, 0), LocalTime.of(11, 0));
        SchedulingContext context = SchedulingFixtures.batchContextWith(
                batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)), java.util.List.of(existingBooking), java.util.List.of(), java.util.List.of());

        ConstraintResult availabilityResult =
                new FacultyAvailabilityConstraint(facultyAvailabilityService).evaluate(context, candidate(context));
        ConstraintResult conflictResult = new FacultyConflictConstraint().evaluate(context, candidate(context));

        assertThat(availabilityResult.outcome()).isEqualTo(ConstraintOutcome.PASS);
        assertThat(conflictResult.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(conflictResult.violation().errorCode()).isEqualTo("FACULTY_CONFLICT");
    }
}
