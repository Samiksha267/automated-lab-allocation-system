package com.college.laballocation.common;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a CR attempts to act on a division they are not currently
 * assigned to - HC-11 (docs/06-CONSTRAINTS.md), enforced server-side from the
 * authenticated user's {@code CrAssignment}, never from a client-supplied
 * {@code divisionId} (docs/09-AUTHORIZATION-RBAC.md).
 */
public class ForbiddenDivisionAccessException extends ApiException {

    public ForbiddenDivisionAccessException(String message) {
        super("FORBIDDEN_DIVISION_ACCESS", HttpStatus.FORBIDDEN, message);
    }
}
