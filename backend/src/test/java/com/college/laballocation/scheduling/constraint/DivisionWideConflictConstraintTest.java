package com.college.laballocation.scheduling.constraint;

import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchContextWith;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.candidate;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.divisionContextWith;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.divisionRequest;
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

/** The Division Conflict Matrix, PART 20 of the Phase 9 brief. */
class DivisionWideConflictConstraintTest {

    private final DivisionWideConflictConstraint constraint = new DivisionWideConflictConstraint();

    @Test
    void idIsHc05() {
        assertThat(constraint.id()).isEqualTo(HardConstraintId.HC_05_DIVISION_CONFLICT);
    }

    @Test
    void existingBatchA1CandidateBatchA2SameDivisionPasses() {
        var existingA1 = existing(1L, 999L, 77L, 1L, 999L, TargetType.BATCH, LocalTime.of(9, 0), LocalTime.of(11, 0));
        SchedulingContext context =
                batchContextWith(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)), List.of(), List.of(), List.of(existingA1));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void existingBatchA1CandidateDivisionAFails() {
        var existingA1 = existing(1L, 999L, 77L, 1L, 999L, TargetType.BATCH, LocalTime.of(9, 0), LocalTime.of(11, 0));
        SchedulingContext context =
                divisionContextWith(divisionRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)), List.of(), List.of(existingA1));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("DIVISION_CONFLICT");
    }

    @Test
    void existingDivisionACandidateBatchA2Fails() {
        var existingDivision = existing(1L, 999L, 77L, 1L, null, TargetType.DIVISION, LocalTime.of(9, 0), LocalTime.of(11, 0));
        SchedulingContext context =
                batchContextWith(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)), List.of(), List.of(), List.of(existingDivision));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("DIVISION_CONFLICT");
    }

    @Test
    void existingDivisionACandidateDivisionAFails() {
        var existingDivision = existing(1L, 999L, 77L, 1L, null, TargetType.DIVISION, LocalTime.of(9, 0), LocalTime.of(11, 0));
        SchedulingContext context =
                divisionContextWith(divisionRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)), List.of(), List.of(existingDivision));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
    }

    @Test
    void differentDivisionsRunningDivisionWideSessionsSimultaneouslyIsNotAConflict() {
        // existingDivisionAllocations is already scoped to *this* division by
        // AllocationQueryService.findActiveForDivision - a different division's
        // rows never appear in this context's list at all.
        SchedulingContext context = divisionContextWith(divisionRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)), List.of(), List.of());
        ConstraintResult result = constraint.evaluate(context, candidate(context));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }
}
