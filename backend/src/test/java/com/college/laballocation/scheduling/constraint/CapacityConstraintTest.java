package com.college.laballocation.scheduling.constraint;

import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchContext;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.candidate;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.divisionContext;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.divisionRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.lab;
import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class CapacityConstraintTest {

    private final CapacityConstraint constraint = new CapacityConstraint();

    @Test
    void idIsHc07() {
        assertThat(constraint.id()).isEqualTo(HardConstraintId.HC_07_CAPACITY);
    }

    @Test
    void batchCandidateFailsWhenLabCapacityBelowBatchStrength() {
        // batchRef() strength is 23 in the fixture - use a smaller lab.
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));
        ConstraintResult result = constraint.evaluate(context, candidate(context, lab(10)));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("CAPACITY_VIOLATION");
        assertThat(result.violation().details()).containsEntry("requiredCapacity", 23).containsEntry("labCapacity", 10);
    }

    @Test
    void batchCandidatePassesWhenLabCapacityMeetsBatchStrength() {
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));
        ConstraintResult result = constraint.evaluate(context, candidate(context, lab(23)));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void divisionCandidateFailsWhenLabCapacityBelowDivisionStrength() {
        // divisionRef() strength is 68 in the fixture.
        SchedulingContext context = divisionContext(divisionRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));
        ConstraintResult result = constraint.evaluate(context, candidate(context, lab(50)));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().details()).containsEntry("requiredCapacity", 68).containsEntry("labCapacity", 50);
    }

    @Test
    void divisionCandidatePassesWhenLabCapacityExceedsDivisionStrength() {
        SchedulingContext context = divisionContext(divisionRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));
        ConstraintResult result = constraint.evaluate(context, candidate(context, lab(150)));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }
}
