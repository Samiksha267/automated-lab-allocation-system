package com.college.laballocation.common;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource (by id) does not exist. Defaults to
 * {@code RESOURCE_NOT_FOUND}; callers that want a more specific, documented
 * code (e.g. {@code PROGRAM_NOT_FOUND}, {@code DIVISION_NOT_FOUND} - see
 * docs/10-API-DOCUMENTATION.md) pass it explicitly rather than this project
 * minting a whole exception subclass per entity (PART 59: keep the error
 * taxonomy manageable).
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }

    public ResourceNotFoundException(String code, String message) {
        super(code, HttpStatus.NOT_FOUND, message);
    }
}
