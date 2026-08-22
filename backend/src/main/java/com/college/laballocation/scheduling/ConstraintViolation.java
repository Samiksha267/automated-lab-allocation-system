package com.college.laballocation.scheduling;

import java.util.Map;

/**
 * A structured, explainable failure reason - deliberately not an untyped
 * {@code Map}-only design (PART 19 of the Phase 8 brief), so a rejected
 * candidate's explanation (Phase 12) can be rendered consistently regardless
 * of which constraint produced it.
 *
 * @param errorCode the wire-level API error code (e.g. {@code LAB_CONFLICT}) - see docs/10-API-DOCUMENTATION.md#error-model
 * @param message human-readable explanation
 * @param affectedResourceType e.g. {@code "LAB"}, {@code "FACULTY"}, {@code "BATCH"}, {@code "DIVISION"}, {@code "SOFTWARE"}
 * @param affectedResourceId the specific resource's stable identifier (code or id, as a string) - nullable if not applicable
 * @param details endpoint-specific context (e.g. the conflicting allocation's id/time) - never a stack trace
 */
public record ConstraintViolation(
        String errorCode, String message, String affectedResourceType, String affectedResourceId, Map<String, Object> details) {

    public ConstraintViolation {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ConstraintViolation(String errorCode, String message, String affectedResourceType, String affectedResourceId) {
        this(errorCode, message, affectedResourceType, affectedResourceId, Map.of());
    }
}
