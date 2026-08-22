package com.college.laballocation.scheduling;

import com.college.laballocation.scheduling.SchedulingRefs.BatchRef;
import com.college.laballocation.scheduling.SchedulingRefs.DivisionRef;
import com.college.laballocation.scheduling.SchedulingRefs.ExistingAllocationSnapshot;
import com.college.laballocation.scheduling.SchedulingRefs.FacultyRef;
import com.college.laballocation.scheduling.SchedulingRefs.SubjectRef;
import java.util.List;

/**
 * Everything needed to evaluate a {@link SchedulingRequest} that does
 * <b>not</b> vary per candidate lab - loaded once per scheduling run and
 * passed by reference, so a future constraint engine (Phase 9) evaluating
 * many candidate labs never re-queries faculty/batch/division data for each
 * one (PART 16 of the Phase 8 brief: "avoid each constraint independently
 * querying the entire database").
 *
 * <p>Deliberately excludes anything that <i>does</i> vary per candidate lab -
 * existing allocations for a specific lab (HC-01) and that lab's
 * unavailability windows (HC-06) are queried once per candidate inside
 * {@link CandidateAllocation} evaluation instead, since precomputing them
 * for every lab in the system before a single candidate is even considered
 * would be wasted work at this project's ~15-lab scale, and pure waste at
 * any larger one.
 *
 * <p>Assembled by {@code SchedulingContextFactory} from the already-existing
 * Phase 4/5 services and repositories - this class itself performs no
 * queries; it is a plain data holder (NFR-08).
 */
public record SchedulingContext(
        SchedulingRequest request,
        SubjectRef subject,
        FacultyRef faculty,
        DivisionRef division,
        BatchRef batch,
        List<ExistingAllocationSnapshot> existingFacultyAllocations,
        List<ExistingAllocationSnapshot> existingBatchAllocations,
        List<ExistingAllocationSnapshot> existingDivisionAllocations) {

    public SchedulingContext {
        existingFacultyAllocations = List.copyOf(existingFacultyAllocations);
        existingBatchAllocations = List.copyOf(existingBatchAllocations);
        existingDivisionAllocations = List.copyOf(existingDivisionAllocations);
    }
}
