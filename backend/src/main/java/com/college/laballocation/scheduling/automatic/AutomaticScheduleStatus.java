package com.college.laballocation.scheduling.automatic;

/**
 * The outcome of one {@code AutomaticSchedulingEngine.schedule(...)} call
 * (PART 33/34/52 of the Phase 14 brief).
 *
 * <ul>
 *   <li>{@link #COMPLETE} - every requirement was assigned.</li>
 *   <li>{@link #PARTIAL} - the search fully explored the space within its
 *       node budget and proved no complete assignment exists, but at least
 *       one requirement could be assigned (the best assignment count
 *       observed anywhere during the search is retained and returned).</li>
 *   <li>{@link #NO_SOLUTION} - the search fully explored the space within
 *       its node budget and found that not even a single requirement could
 *       ever be assigned (e.g. the whole date range has no valid slot for
 *       anyone) - mathematically proven infeasible, not merely unexplored.</li>
 *   <li>{@link #SEARCH_LIMIT_REACHED} - the node budget was exhausted
 *       before the search could either find a complete solution or prove
 *       none exists. This is categorically different from {@code NO_SOLUTION}:
 *       it means "we stopped searching," never "this is mathematically
 *       impossible" (PART 52).</li>
 * </ul>
 */
public enum AutomaticScheduleStatus {
    COMPLETE,
    PARTIAL,
    NO_SOLUTION,
    SEARCH_LIMIT_REACHED
}
