package com.college.laballocation.scheduling.explanation;

import com.college.laballocation.scheduling.scoring.ScoringFactorId;
import java.util.Map;

/**
 * Display-layer-only mapping from {@link ScoringFactorId} to a short,
 * human-readable label - covers all six documented factors (docs/07-ALLOCATION-SCORING.md),
 * not just the three currently backed by a registered {@code AllocationScorer}
 * bean, so a future phase enabling a deferred factor needs no new label.
 */
public final class ScoringFactorLabels {

    private static final Map<ScoringFactorId, String> LABELS = Map.ofEntries(
            Map.entry(ScoringFactorId.CAPACITY_FIT, "Capacity fit"),
            Map.entry(ScoringFactorId.ADDITIONAL_ENVIRONMENT_FIT, "Additional environment fit"),
            Map.entry(ScoringFactorId.PREFERRED_LAB_TYPE, "Preferred lab type"),
            Map.entry(ScoringFactorId.BALANCED_UTILIZATION, "Balanced utilization"),
            Map.entry(ScoringFactorId.FACULTY_PREFERENCE, "Faculty preference"),
            Map.entry(ScoringFactorId.TIMETABLE_GAP, "Fewer timetable gaps"));

    private ScoringFactorLabels() {}

    public static String labelFor(ScoringFactorId factor) {
        return LABELS.getOrDefault(factor, factor.name());
    }
}
