package com.college.laballocation.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base type for exceptions that should surface as a structured {@link ApiErrorResponse}
 * with a specific machine-readable code and HTTP status, rather than as a generic 500.
 * Scheduling-specific subclasses (LAB_CONFLICT, FACULTY_CONFLICT, etc. - see
 * docs/06-CONSTRAINTS.md) are added once the constraint engine exists (Phase 9);
 * this phase only establishes the mechanism.
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public ApiException(String code, HttpStatus status, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details;
    }

    public ApiException(String code, HttpStatus status, String message) {
        this(code, status, message, Map.of());
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
