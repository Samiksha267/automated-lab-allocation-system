package com.college.laballocation.scheduling.automatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.BatchRef;
import com.college.laballocation.scheduling.SchedulingRefs.DivisionRef;
import com.college.laballocation.scheduling.SchedulingRefs.FacultyRef;
import com.college.laballocation.scheduling.SchedulingRefs.LabRef;
import com.college.laballocation.scheduling.SchedulingRefs.SubjectRef;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.constraint.LabConflictConstraint;
import com.college.laballocation.scheduling.explanation.ExplainedValidCandidate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Regression test for a real bug found live in Docker during Phase 14
 * manual verification (see the Phase 14 completion report): a provisional
 * {@code ExistingAllocationSnapshot} built with a {@code null} allocationId
 * crashed {@code LabConflictConstraint} (and, identically, HC-02/04/05) with
 * a {@code NullPointerException} the moment a real conflict was detected,
 * because {@code Map.of("existingAllocationId", null, ...)} throws. This
 * test exercises the REAL constraint class (not a mock) against a snapshot
 * built via {@code PlannedAllocation.toSnapshot()}, so a regression here
 * would be caught immediately rather than only in a live Docker run.
 */
class PlannedAllocationTest {

    private SchedulingRequest request(LocalDate date, LocalTime start, LocalTime end) {
        return new SchedulingRequest(AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L, date, start, end, null);
    }

    private ExplainedValidCandidate candidate(Long labId, String labCode) {
        return new ExplainedValidCandidate(labId, labCode, 1, 30, 30, 1.0, List.of(), List.of());
    }

    @Test
    void toSnapshotNeverProducesANullAllocationId() {
        PlannedAllocation planned = new PlannedAllocation(
                "R1", request(LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0)), candidate(10L, "X"));

        assertThat(planned.toSnapshot().allocationId()).isNotNull();
    }

    @Test
    void realLabConflictConstraintDoesNotThrowAgainstAProvisionalSnapshot() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        PlannedAllocation planned = new PlannedAllocation(
                "R1", request(date, LocalTime.of(9, 0), LocalTime.of(11, 0)), candidate(10L, "X"));
        SchedulingSearchState state = SchedulingSearchState.empty().with(planned);

        SchedulingRequest overlappingRequest = request(date, LocalTime.of(10, 0), LocalTime.of(12, 0));
        SchedulingContext context = new SchedulingContext(
                overlappingRequest,
                new SubjectRef(3L, "BDA", "Big Data Analytics", 10L, null, null),
                new FacultyRef(4L, "FAC-BDA", "Faculty BDA", true),
                new DivisionRef(1L, "A", 68, 10L),
                new BatchRef(2L, "A1", 23, 1L),
                List.of(), List.of(), List.of());
        // The exact same lab (id 10L) the provisional allocation used, with the provisional snapshot merged in -
        // mirrors what CandidateAllocationFactory.build(context, labId, searchState) actually produces.
        LabRef labRef = new LabRef(10L, "X", true, 72, 20L, "COMPUTER", Set.of(), Map.of(), state.forLab(10L, date), List.of());
        CandidateAllocation candidateAllocation = new CandidateAllocation(context, labRef);

        LabConflictConstraint constraint = new LabConflictConstraint();

        assertThatCode(() -> constraint.evaluate(context, candidateAllocation)).doesNotThrowAnyException();
        ConstraintResult result = constraint.evaluate(context, candidateAllocation);
        assertThat(result.outcome().name()).isEqualTo("FAIL");
        assertThat(result.constraintId()).isEqualTo(HardConstraintId.HC_01_LAB_CONFLICT);
        assertThat(result.violation().details()).containsKey("existingAllocationId");
    }
}
