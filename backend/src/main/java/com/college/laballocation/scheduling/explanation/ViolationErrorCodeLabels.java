package com.college.laballocation.scheduling.explanation;

import java.util.Map;

/**
 * Display-layer-only mapping from a {@code ConstraintViolation.errorCode()}
 * (the wire-level API error code, docs/10-API-DOCUMENTATION.md#error-model)
 * to a short, human-readable label - the machine code itself is always kept
 * unchanged alongside this label (PART 35/36 of the Phase 12 brief).
 */
public final class ViolationErrorCodeLabels {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("CAPACITY_VIOLATION", "Capacity requirement"),
            Map.entry("SOFTWARE_MISMATCH", "Required software"),
            Map.entry("EQUIPMENT_MISMATCH", "Required equipment"),
            Map.entry("LAB_TYPE_MISMATCH", "Required lab type"),
            Map.entry("LAB_UNAVAILABLE", "Lab availability"),
            Map.entry("LAB_CONFLICT", "Lab conflict"),
            Map.entry("FACULTY_CONFLICT", "Faculty conflict"),
            Map.entry("FACULTY_UNAVAILABLE", "Faculty availability"),
            Map.entry("BATCH_CONFLICT", "Batch conflict"),
            Map.entry("DIVISION_CONFLICT", "Division conflict"),
            Map.entry("INVALID_ACADEMIC_RELATIONSHIP", "Academic relationship"),
            Map.entry("FORBIDDEN_DIVISION_ACCESS", "CR authorization"),
            Map.entry("CR_ASSIGNMENT_NOT_FOUND", "CR authorization"));

    private ViolationErrorCodeLabels() {}

    public static String labelFor(String errorCode) {
        return LABELS.getOrDefault(errorCode, errorCode);
    }
}
