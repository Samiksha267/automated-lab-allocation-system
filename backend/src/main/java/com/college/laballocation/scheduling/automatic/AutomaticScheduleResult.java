package com.college.laballocation.scheduling.automatic;

import java.util.List;
import java.util.Objects;

/**
 * The full, advisory result of one {@code AutomaticSchedulingEngine.schedule(...)}
 * call - transient, never persisted (PART 42/45 of the Phase 14 brief).
 * Exactly like Phase 12/13's results, this describes a proposed schedule
 * against a snapshot, never a commitment - no {@code Allocation} row is
 * created and no {@code ScheduleVersion} is published by this phase.
 */
public record AutomaticScheduleResult(
        AutomaticScheduleStatus status,
        List<PlannedAllocation> assignments,
        List<UnscheduledRequirement> unscheduledRequirements,
        int totalRequirements,
        int scheduledCount,
        int unscheduledCount,
        SearchStatistics statistics) {

    public AutomaticScheduleResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(statistics, "statistics must not be null");
        assignments = List.copyOf(assignments);
        unscheduledRequirements = List.copyOf(unscheduledRequirements);
    }
}
