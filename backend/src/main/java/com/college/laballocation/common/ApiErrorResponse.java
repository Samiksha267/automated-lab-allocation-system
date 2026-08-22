package com.college.laballocation.common;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response shape for every API error (see docs/10-API-DOCUMENTATION.md#error-model).
 * {@code code} is the stable, machine-readable identifier the frontend branches on;
 * {@code message} is human-readable and may change wording without being a breaking
 * change; {@code details} is endpoint-specific context - never a stack trace, SQL
 * error, or credential.
 */
public record ApiErrorResponse(
        String code,
        String message,
        Map<String, Object> details,
        Instant timestamp) {

    public static ApiErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ApiErrorResponse(code, message, details, Instant.now());
    }

    public static ApiErrorResponse of(String code, String message) {
        return of(code, message, Map.of());
    }
}
