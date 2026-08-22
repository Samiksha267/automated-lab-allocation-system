package com.college.laballocation.auth;

import com.college.laballocation.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A single, generic failure for "email doesn't exist", "password is wrong", and
 * "account is inactive" alike - deliberately does not distinguish these cases
 * in its response, to avoid user enumeration (docs/09-AUTHORIZATION-RBAC.md).
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }
}
