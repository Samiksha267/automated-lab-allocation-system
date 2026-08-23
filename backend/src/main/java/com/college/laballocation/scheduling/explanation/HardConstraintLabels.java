package com.college.laballocation.scheduling.explanation;

import com.college.laballocation.scheduling.HardConstraintId;
import java.util.Map;

/**
 * Display-layer-only mapping from {@link HardConstraintId} to a short,
 * human-readable label - never a substitute for the stable machine
 * identifier itself (PART 35/36 of the Phase 12 brief). Every explanation
 * type keeps the raw {@link HardConstraintId} alongside this label, so a
 * future UI/API can always fall back to the machine code and never has to
 * parse the label back into one.
 */
public final class HardConstraintLabels {

    private static final Map<HardConstraintId, String> LABELS = Map.ofEntries(
            Map.entry(HardConstraintId.HC_01_LAB_CONFLICT, "No lab conflict"),
            Map.entry(HardConstraintId.HC_02_FACULTY_CONFLICT, "No faculty conflict"),
            Map.entry(HardConstraintId.HC_03_FACULTY_AVAILABILITY, "Faculty available"),
            Map.entry(HardConstraintId.HC_04_BATCH_CONFLICT, "No batch conflict"),
            Map.entry(HardConstraintId.HC_05_DIVISION_CONFLICT, "No division conflict"),
            Map.entry(HardConstraintId.HC_06_LAB_AVAILABILITY, "Lab administratively available"),
            Map.entry(HardConstraintId.HC_07_CAPACITY, "Capacity requirement"),
            Map.entry(HardConstraintId.HC_08_REQUIRED_SOFTWARE, "Required software available"),
            Map.entry(HardConstraintId.HC_09_REQUIRED_EQUIPMENT, "Required equipment available"),
            Map.entry(HardConstraintId.HC_10_REQUIRED_LAB_TYPE, "Required lab type"),
            Map.entry(HardConstraintId.HC_11_CR_AUTHORIZATION, "CR authorization"),
            Map.entry(HardConstraintId.HC_12_ACADEMIC_RELATIONSHIP, "Academic relationship"));

    private HardConstraintLabels() {}

    public static String labelFor(HardConstraintId id) {
        return LABELS.getOrDefault(id, id.name());
    }
}
