package com.college.laballocation.common;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Centralized exception -> {@link ApiErrorResponse} translation for every controller.
 * Never leaks stack traces, SQL errors, or internal exception details to API
 * consumers (see docs/10-API-DOCUMENTATION.md#error-model and PART 45/12 of the
 * project brief). Scheduling-specific error codes (LAB_CONFLICT, etc.) are added
 * once the constraint engine exists; this phase covers only the baseline codes:
 * VALIDATION_ERROR, BAD_REQUEST, RESOURCE_NOT_FOUND, INTERNAL_ERROR.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(v -> details.put(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_ERROR", "Request validation failed.", details));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_ERROR", "Request validation failed.", details));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("BAD_REQUEST", "Request body is missing or malformed."));
    }

    /**
     * {@code @PreAuthorize} denials are thrown deep inside the controller
     * method invocation (AOP proxy), which is still within Spring MVC's own
     * dispatch - by the time the exception is raised, {@code @RestControllerAdvice}
     * (this class) sees it before Spring Security's {@code ExceptionTranslationFilter}
     * / {@code AccessDeniedHandler} ever would (that filter only catches
     * exceptions that escape the whole dispatch unhandled). Without this
     * handler, a role-authorization denial would fall through to the generic
     * {@code Exception} handler below and incorrectly return 500 instead of
     * 403 - confirmed the hard way via Phase 4's Docker verification, not
     * assumed. {@code RestAccessDeniedHandler} (security package) remains in
     * place for URL-level {@code authorizeHttpRequests} denials, which are a
     * genuinely different code path (rejected before dispatch even begins).
     */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("FORBIDDEN", "You do not have permission to perform this action."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        // Logged with full detail server-side; the client only ever sees a generic message.
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred."));
    }
}
