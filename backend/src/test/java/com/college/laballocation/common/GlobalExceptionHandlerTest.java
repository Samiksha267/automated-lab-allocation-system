package com.college.laballocation.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Direct unit test of {@link GlobalExceptionHandler} - verifies every baseline
 * error path (VALIDATION_ERROR, RESOURCE_NOT_FOUND, INTERNAL_ERROR) produces
 * the standard {@link ApiErrorResponse} shape without leaking internal
 * exception details, per docs/10-API-DOCUMENTATION.md#error-model. Exercises
 * the handler methods directly rather than through a demo controller + full
 * MockMvc/web context, keeping the test fast and avoiding a throwaway
 * controller that would otherwise need removing before Phase 2 completion.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceNotFoundExceptionMapsToStandardErrorShape() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleApiException(new ResourceNotFoundException("Probe resource not found."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Probe resource not found.");
    }

    @Test
    void unexpectedExceptionNeverLeaksInternalDetails() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpected(new IllegalStateException("db password=hunter2 - must never reach client"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred.");
        assertThat(response.getBody().message()).doesNotContain("hunter2");
    }

    @Test
    void methodArgumentNotValidMapsFieldErrorsIntoDetails() throws NoSuchMethodException {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "probeRequest");
        bindingResult.addError(new FieldError("probeRequest", "name", "must not be blank"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTargetMethod", String.class), 0),
                bindingResult);

        ResponseEntity<Object> response =
                handler.handleMethodArgumentNotValid(ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);
        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.details()).containsEntry("name", "must not be blank");
    }

    @SuppressWarnings("unused")
    private void dummyTargetMethod(String name) {}
}
