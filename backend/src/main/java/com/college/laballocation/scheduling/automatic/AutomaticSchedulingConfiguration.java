package com.college.laballocation.scheduling.automatic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bounds for automatic scheduling (PART 35/37/28 of the Phase 14 brief) -
 * centralized configuration, following this project's existing
 * constructor-{@code @Value} convention rather than {@code @ConfigurationProperties}.
 *
 * <p>{@code maxNodes} bounds the backtracking search itself (one recursive
 * search-state visit = one node, PART 39) - without this, a pathological or
 * over-constrained input could search indefinitely. {@code maxRequirements}
 * and {@code maxDateRangeDays} guard against malformed/oversized inputs
 * before the search even begins (PART 36/37) - a huge date range multiplies
 * the raw slot universe ({@code SchedulingSlotProvider.generateSlotsInRange})
 * and therefore the per-node cost of computing MRV choice counts.
 */
@Component
public class AutomaticSchedulingConfiguration {

    private final int maxNodes;
    private final int maxRequirements;
    private final int maxDateRangeDays;

    public AutomaticSchedulingConfiguration(
            @Value("${app.scheduling.backtracking.max-nodes}") int maxNodes,
            @Value("${app.scheduling.backtracking.max-requirements}") int maxRequirements,
            @Value("${app.scheduling.backtracking.max-date-range-days}") int maxDateRangeDays) {
        this.maxNodes = requirePositive(maxNodes, "maxNodes");
        this.maxRequirements = requirePositive(maxRequirements, "maxRequirements");
        this.maxDateRangeDays = requirePositive(maxDateRangeDays, "maxDateRangeDays");
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0, got " + value);
        }
        return value;
    }

    public int maxNodes() {
        return maxNodes;
    }

    public int maxRequirements() {
        return maxRequirements;
    }

    public int maxDateRangeDays() {
        return maxDateRangeDays;
    }
}
