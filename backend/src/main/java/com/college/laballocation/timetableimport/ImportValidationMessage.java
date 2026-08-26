package com.college.laballocation.timetableimport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One explainable mapping/validation finding for a staged row (PART 24) -
 * structured, never a bare string, reusing this project's established
 * explainability shape (Phase 12: {@code code}/{@code message}/{@code details}).
 * A row's final {@code ImportRowStatus} is the worst severity among all of
 * its messages ({@code ERROR} beats {@code WARNING} beats none at all).
 */
public record ImportValidationMessage(ImportRowStatus severity, String code, String message, Map<String, Object> details) {

    public ImportValidationMessage {
        if (severity == ImportRowStatus.VALID) {
            throw new IllegalArgumentException("A message's severity must be WARNING or ERROR, never VALID.");
        }
    }

    static ImportValidationMessage error(String code, String message, Map<String, Object> details) {
        return new ImportValidationMessage(ImportRowStatus.ERROR, code, message, details);
    }

    static ImportValidationMessage warning(String code, String message, Map<String, Object> details) {
        return new ImportValidationMessage(ImportRowStatus.WARNING, code, message, details);
    }

    Map<String, Object> toStoredMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("severity", severity.name());
        map.put("code", code);
        map.put("message", message);
        if (details != null && !details.isEmpty()) {
            map.put("details", details);
        }
        return map;
    }
}
