package com.college.laballocation.scheduling.constraint;

import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchContextWith;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.candidate;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.divisionContext;
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

class BatchConflictConstraintTest {

    private final BatchConflictConstraint constraint = new BatchConflictConstraint();

    @Test
    void idIsHc04() {
        assertThat(constraint.id()).isEqualTo(HardConstraintId.HC_04_BATCH_CONFLICT);
    }

    @Test
    void divisionCandidateHasNoBatchToConflictOnSoAlwaysPasses() {
        SchedulingContext context = divisionContext(divisionRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));
        ConstraintResult result = constraint.evaluate(context, candidate(context));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    /** PART 16 - the critical scenario: A1 and A2 (different batches, same division) may run simultaneously. */
    @Test
    void differentBatchesOfTheSameDivisionMaySimultaneouslyRun() {
        // context.existingBatchAllocations() is A1-batch-scoped only - A2's own
        // request never sees A1's data in *its* batch-allocations list, since
        // AllocationQueryService.findActiveForBatch is scoped to A2's own batchId.
        SchedulingContext context = batchContextWith(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)), List.of(), List.of(), List.of());
        ConstraintResult result = constraint.evaluate(context, candidate(context));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void sameBatchOverlappingIsRejected() {
        var existing = existing(50L, 999L, 77L, 1L, 2L, TargetType.BATCH, LocalTime.of(9, 0), LocalTime.of(11, 0));
        SchedulingContext context =
                batchContextWith(batchRequest(LocalTime.of(10, 0), LocalTime.of(12, 0)), List.of(), List.of(existing), List.of());

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("BATCH_CONFLICT");
    }
}
