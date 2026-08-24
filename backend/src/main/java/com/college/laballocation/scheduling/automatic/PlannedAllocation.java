package com.college.laballocation.scheduling.automatic;

import com.college.laballocation.scheduling.SchedulingRefs.ExistingAllocationSnapshot;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.explanation.ExplainedValidCandidate;
import java.util.Objects;

/**
 * One provisional decision made during the current automatic-scheduling
 * search - transient algorithm state, never persisted, never a JPA entity
 * (PART 3 of the Phase 14 brief). Deliberately mirrors the same
 * "persisted vs. transient" split this project has drawn since Phase 8
 * ({@code Allocation} vs. {@code CandidateAllocation}) - a {@code PlannedAllocation}
 * is this search's counterpart of a not-yet-committed {@code Allocation}.
 *
 * <p>{@code chosenCandidate} is the exact {@link ExplainedValidCandidate}
 * Phase 12's {@code ExplainableAllocationService} already produced for this
 * concrete slot - carrying its real Phase 11 score breakdown and
 * constraint-check summary, so nothing about "why this lab" needs to be
 * recomputed once a schedule is assembled (PART 50).
 */
public record PlannedAllocation(String requirementKey, SchedulingRequest request, ExplainedValidCandidate chosenCandidate) {

    public PlannedAllocation {
        Objects.requireNonNull(requirementKey, "requirementKey must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(chosenCandidate, "chosenCandidate must not be null");
    }

    /**
     * A synthetic, never-persisted allocation id for provisional snapshots -
     * real PostgreSQL {@code BIGINT} identity columns always start at 1 and
     * are positive (docs/ASSUMPTIONS.md A-14), so a negative sentinel can
     * never collide with one. {@code null} was tried first and rejected: a
     * real bug, found live in Docker (see the Phase 14 completion report's
     * "Real Bugs Found") - HC-01/02/04/05 each build their
     * {@code ConstraintViolation.details()} map with
     * {@code Map.of("existingAllocationId", existing.allocationId(), ...)},
     * and {@code java.util.Map.of(...)} throws {@code NullPointerException}
     * on any null value. Rather than touch four tested Phase 9 constraint
     * classes to null-guard a value they never previously needed to guard,
     * this sentinel keeps every downstream consumer completely unchanged.
     */
    private static final long PROVISIONAL_ALLOCATION_ID = -1L;

    /**
     * A candidate-independent snapshot of this provisional decision, in the
     * exact shape HC-01/02/04/05 already read for persisted allocations
     * ({@link ExistingAllocationSnapshot}) - the single mechanism by which
     * this search's own choices become visible to the unmodified Phase 9
     * constraints for a *later* requirement in the same search (PART 4/23).
     * {@code allocationId} is {@link #PROVISIONAL_ALLOCATION_ID} - this
     * decision has no persisted row and never will unless a later phase
     * explicitly commits it.
     */
    public ExistingAllocationSnapshot toSnapshot() {
        return new ExistingAllocationSnapshot(
                PROVISIONAL_ALLOCATION_ID,
                chosenCandidate.labId(),
                chosenCandidate.labCode(),
                request.facultyId(),
                request.divisionId(),
                request.batchId(),
                request.targetType(),
                request.allocationDate(),
                request.startTime(),
                request.endTime());
    }
}
