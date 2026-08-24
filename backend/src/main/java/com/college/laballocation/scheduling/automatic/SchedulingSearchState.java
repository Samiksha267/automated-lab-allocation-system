package com.college.laballocation.scheduling.automatic;

import com.college.laballocation.scheduling.SchedulingRefs.ExistingAllocationSnapshot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The immutable, in-memory record of every provisional decision made so far
 * in one automatic-scheduling search branch (PART 3/41 of the Phase 14
 * brief). {@link #with(PlannedAllocation)} returns a new, independent state
 * rather than mutating this one - "backtracking" a branch is then simply
 * discarding the child state and continuing to use the parent, never an
 * explicit undo operation that could corrupt a sibling branch (PART 41's
 * chosen trade-off: correctness/explainability over micro-allocation-optimization
 * at this project's scale).
 *
 * <p><b>The integration point with Phase 9</b> (PART 4/5/6): this class
 * never duplicates HC-01/02/04/05's conflict logic. It only answers "which
 * provisional {@link ExistingAllocationSnapshot}s are relevant to this
 * faculty/batch/division/lab on this date" - the exact same query shape
 * {@link com.college.laballocation.scheduling.AllocationQueryService}
 * already answers for *persisted* rows. {@code SchedulingContextFactory}/
 * {@code CandidateAllocationFactory} (extended, additively, this phase)
 * append these provisional snapshots onto the same lists the unmodified
 * constraints already read - so a constraint evaluating a candidate cannot
 * tell, and does not need to know, whether one entry came from PostgreSQL
 * or from this search. There remains exactly one place that decides
 * validity: {@code ConstraintEngine}.
 */
public record SchedulingSearchState(List<PlannedAllocation> assignments) {

    public SchedulingSearchState {
        assignments = List.copyOf(assignments);
    }

    public static SchedulingSearchState empty() {
        return new SchedulingSearchState(List.of());
    }

    public SchedulingSearchState with(PlannedAllocation allocation) {
        Objects.requireNonNull(allocation, "allocation must not be null");
        List<PlannedAllocation> next = new ArrayList<>(assignments);
        next.add(allocation);
        return new SchedulingSearchState(next);
    }

    public int size() {
        return assignments.size();
    }

    /** Provisional sessions for this faculty on this date - HC-02's provisional-occupancy input. */
    public List<ExistingAllocationSnapshot> forFaculty(Long facultyId, LocalDate date) {
        return snapshotsWhere(s -> facultyId.equals(s.facultyId()) && date.equals(s.allocationDate()));
    }

    /** Provisional sessions for this batch on this date - HC-04's provisional-occupancy input. */
    public List<ExistingAllocationSnapshot> forBatch(Long batchId, LocalDate date) {
        return snapshotsWhere(s -> batchId.equals(s.batchId()) && date.equals(s.allocationDate()));
    }

    /**
     * Provisional sessions for this division on this date, both BATCH- and
     * DIVISION-targeted alike - HC-05's bidirectional provisional-occupancy
     * input, mirroring how {@code context.existingDivisionAllocations()}
     * already includes both for persisted rows (every row carries
     * {@code divisionId} regardless of target type, docs/04-DATABASE-DESIGN.md §7).
     */
    public List<ExistingAllocationSnapshot> forDivision(Long divisionId, LocalDate date) {
        return snapshotsWhere(s -> divisionId.equals(s.divisionId()) && date.equals(s.allocationDate()));
    }

    /** Provisional sessions in this lab on this date - HC-01's provisional-occupancy input. */
    public List<ExistingAllocationSnapshot> forLab(Long labId, LocalDate date) {
        return snapshotsWhere(s -> labId.equals(s.labId()) && date.equals(s.allocationDate()));
    }

    private List<ExistingAllocationSnapshot> snapshotsWhere(java.util.function.Predicate<ExistingAllocationSnapshot> filter) {
        return assignments.stream().map(PlannedAllocation::toSnapshot).filter(filter).toList();
    }
}
