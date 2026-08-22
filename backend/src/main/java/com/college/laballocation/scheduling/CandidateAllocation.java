package com.college.laballocation.scheduling;

/**
 * One (lab) combination under evaluation for a {@link SchedulingContext}'s
 * request - the transient, unpersisted counterpart of {@link Allocation}.
 * Deliberately never a JPA entity (PART 22 of the Phase 8 brief): a
 * candidate is a proposed possibility, not a fact, and most candidates
 * evaluated during a scheduling run are rejected and never need to exist
 * beyond the single evaluation pass.
 *
 * <p>This phase only defines the shape. It does not: generate candidates
 * (Phase 10), score them (Phase 11), or select a winner (Phase 12+) - see
 * class-level scope notes in {@code docs/05-SCHEDULING-ENGINE.md}.
 */
public record CandidateAllocation(SchedulingContext context, Long labId, String labCode) {}
