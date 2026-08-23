package com.college.laballocation.scheduling;

import com.college.laballocation.scheduling.SchedulingRefs.LabRef;

/**
 * One candidate lab under evaluation for a {@link SchedulingContext}'s
 * request - the transient, unpersisted counterpart of {@link Allocation}.
 * Deliberately never a JPA entity (ADR-038): a candidate is a proposed
 * possibility, not a fact, and most candidates evaluated during a
 * scheduling run are rejected and never need to exist beyond one
 * evaluation pass.
 *
 * <p>{@code lab} (Phase 9) replaces Phase 8's bare {@code labId}/{@code labCode}
 * fields with a full {@link LabRef} snapshot - capacity, lab type, installed
 * software/equipment, existing lab allocations, and unavailability windows -
 * built once by {@code CandidateAllocationFactory} for this one candidate, so
 * HC-01/06/07/08/09/10 each read from this object instead of independently
 * re-querying the same lab (PART 41/42 of the Phase 9 brief). Building this
 * snapshot for one already-known {@code labId} is not candidate generation
 * (Phase 10, which enumerates many labs) - it is the same "resolve exactly
 * what was asked for" role {@code SchedulingContextFactory} already plays for
 * {@code SchedulingRequest}.
 *
 * <p>This phase only defines the shape and populates it for evaluation - it
 * does not generate candidates (Phase 10), score them (Phase 11), or select
 * a winner (Phase 12+).
 */
public record CandidateAllocation(SchedulingContext context, LabRef lab) {}
