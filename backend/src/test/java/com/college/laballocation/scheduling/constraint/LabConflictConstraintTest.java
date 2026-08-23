package com.college.laballocation.scheduling.constraint;

import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.LAB_ID;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchContext;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.candidate;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.existing;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.lab;
import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.LabRef;
import com.college.laballocation.scheduling.TargetType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabConflictConstraintTest {

    private final LabConflictConstraint constraint = new LabConflictConstraint();

    @Test
    void idIsHc01() {
        assertThat(constraint.id()).isEqualTo(HardConstraintId.HC_01_LAB_CONFLICT);
    }

    @Test
    void passesWhenLabHasNoExistingAllocations() {
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));
        ConstraintResult result = constraint.evaluate(context, candidate(context));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void failsWhenOverlappingExistingAllocationOnSameLabAndDate() {
        var existing = existing(88L, LAB_ID, 99L, 1L, 2L, TargetType.BATCH, LocalTime.of(9, 0), LocalTime.of(11, 0));
        LabRef lab = lab(70, 20L, "COMPUTER", java.util.Set.of(), java.util.Map.of(), List.of(existing), List.of());
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(10, 0), LocalTime.of(12, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, lab));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("LAB_CONFLICT");
        assertThat(result.violation().details()).containsEntry("existingAllocationId", 88L);
    }

    @Test
    void passesWhenBackToBack() {
        var existing = existing(88L, LAB_ID, 99L, 1L, 2L, TargetType.BATCH, LocalTime.of(9, 0), LocalTime.of(11, 0));
        LabRef lab = lab(70, 20L, "COMPUTER", java.util.Set.of(), java.util.Map.of(), List.of(existing), List.of());
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(11, 0), LocalTime.of(13, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, lab));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void doesNotTreatSameTimeOnADifferentDateAsAConflict() {
        var existingDifferentDate =
                existing(88L, LAB_ID, 99L, 1L, 2L, TargetType.BATCH, LocalDate.of(2026, 8, 25), LocalTime.of(9, 0), LocalTime.of(11, 0));
        LabRef lab = lab(70, 20L, "COMPUTER", java.util.Set.of(), java.util.Map.of(), List.of(existingDifferentDate), List.of());
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, lab));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void cancelledAllocationsNeverAppearInTheSnapshotSoTheyCannotBlock() {
        // CandidateAllocationFactory only ever populates existingAllocations from
        // AllocationQueryService, which filters to AllocationStatus.blocksScheduling()
        // (Phase 8) - a CANCELLED row is never present here at all, proven by
        // constructing a lab with zero existing allocations and confirming PASS.
        LabRef lab = lab(70, 20L, "COMPUTER", java.util.Set.of(), java.util.Map.of(), List.of(), List.of());
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        CandidateAllocation candidate = candidate(context, lab);
        ConstraintResult result = constraint.evaluate(context, candidate);

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }
}
