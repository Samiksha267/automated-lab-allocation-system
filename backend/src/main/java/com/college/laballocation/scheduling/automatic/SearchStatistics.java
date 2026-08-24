package com.college.laballocation.scheduling.automatic;

/**
 * Precise, defined search metrics (PART 38/39/40 of the Phase 14 brief) -
 * useful for debugging, testing, and interview explanation, never left
 * ambiguous.
 *
 * @param nodesExplored one recursive search-state visit = one node (PART 39)
 *     - incremented once per call to the recursive solve step, regardless
 *     of outcome.
 * @param backtracks a provisional choice was added, the deeper search
 *     failed for every remaining alternative, and the solver returned to
 *     try a different choice for the same or an earlier requirement
 *     (PART 40) - incremented once per such "give up on this choice, try
 *     the next one" event.
 * @param maxDepthReached the greatest number of requirements ever
 *     simultaneously assigned in one branch during the search (bounded by
 *     the total requirement count).
 * @param choicesEvaluated the total number of individual {@link SchedulingChoice}s
 *     tried (assigned then either kept or undone) across the whole search.
 * @param searchLimitReached {@code true} iff {@code nodesExplored} hit the
 *     configured {@code maxNodes} bound before the search could finish on
 *     its own.
 */
public record SearchStatistics(int nodesExplored, int backtracks, int maxDepthReached, int choicesEvaluated, boolean searchLimitReached) {

    public static SearchStatistics empty() {
        return new SearchStatistics(0, 0, 0, 0, false);
    }
}
