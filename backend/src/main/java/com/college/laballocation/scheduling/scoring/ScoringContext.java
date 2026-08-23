package com.college.laballocation.scheduling.scoring;

import com.college.laballocation.scheduling.SchedulingContext;
import java.util.Map;

/**
 * Everything a scorer needs that is <b>not</b> already on
 * {@link com.college.laballocation.scheduling.CandidateAllocation} - the
 * candidate-independent {@link SchedulingContext} (the exact same instance
 * every candidate in one generation run already shares, per Phase 10) plus
 * data that is inherently relative across the whole valid-candidate set
 * rather than derivable from one candidate alone (PART 59 of the Phase 11
 * brief). Today that is only the per-lab scheduled-minutes load Balanced
 * Utilization needs to rank labs relative to each other; {@code
 * CapacityFitScorer} and {@code PreferredLabTypeScorer} read only
 * {@link #schedulingContext()}.
 *
 * <p>{@code minLoadMinutes}/{@code maxLoadMinutes} are the minimum/maximum
 * scheduled-minutes value across the <b>valid candidate set actually being
 * scored this run</b> (every candidate lab included, zero for a lab with no
 * scheduled minutes) - computed once by {@code ScoringEngine} rather than by
 * each candidate's scorer independently re-scanning the map (PART 55/59).
 * Both are {@code null} when {@link #utilizationDataAvailable()} is
 * {@code false}.
 *
 * <p>Assembled once per {@code ScoringEngine.score(...)} call, never
 * per-candidate.
 */
public record ScoringContext(
        SchedulingContext schedulingContext,
        boolean utilizationDataAvailable,
        Map<Long, Long> scheduledMinutesByLabId,
        Long minLoadMinutes,
        Long maxLoadMinutes) {

    public ScoringContext {
        scheduledMinutesByLabId = Map.copyOf(scheduledMinutesByLabId);
    }

    /** Scheduled minutes for one lab; a lab absent from the map (never allocated in the relevant version) is zero load. */
    public long scheduledMinutesFor(Long labId) {
        return scheduledMinutesByLabId.getOrDefault(labId, 0L);
    }
}
