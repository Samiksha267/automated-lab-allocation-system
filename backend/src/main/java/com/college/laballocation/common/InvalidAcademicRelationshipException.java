package com.college.laballocation.common;

import org.springframework.http.HttpStatus;

/**
 * Thrown when referenced entities don't belong together - e.g. a batch that
 * exists but belongs to a different division than the one specified
 * (docs/06-CONSTRAINTS.md HC-12). This kind of cross-table relationship can't
 * be expressed as a database CHECK constraint in Postgres, so it is validated
 * explicitly in application services (docs/04-DATABASE-DESIGN.md).
 */
public class InvalidAcademicRelationshipException extends ApiException {

    public InvalidAcademicRelationshipException(String message) {
        super("INVALID_ACADEMIC_RELATIONSHIP", HttpStatus.BAD_REQUEST, message);
    }
}
