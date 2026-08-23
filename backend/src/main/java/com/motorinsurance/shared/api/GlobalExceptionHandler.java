package com.motorinsurance.shared.api;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Centralized exception handling (AD-7 skeleton). Every backend module
 * relies on this single {@code @RestControllerAdvice} to turn exceptions
 * into the uniform {@link ApiError} envelope - no module handles its own
 * error shaping.
 *
 * <p>Generic, module-agnostic cases (request validation failures and an
 * unhandled-exception fallback) are seeded here directly. Module-specific
 * errors are modeled as an {@link ApiException} subclass and handled by the
 * single generic {@code @ExceptionHandler(ApiException.class)} method below
 * - a new module error (e.g. {@code AUTH_INVALID_CREDENTIALS},
 * {@code QUOTE_VALIDATION_ERROR}) never requires a new handler method here,
 * only a new {@link ApiException} subclass plus its i18n entry per AD-7/AD-8.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_ERROR_CODE = "SHARED_VALIDATION_ERROR";
    private static final String NOT_FOUND_ERROR_CODE = "SHARED_NOT_FOUND";
    private static final String INTERNAL_ERROR_CODE = "SHARED_INTERNAL_ERROR";

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex) {
        ApiError body = ApiError.of(HttpStatus.NOT_FOUND.value(), NOT_FOUND_ERROR_CODE, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(), VALIDATION_ERROR_CODE, "Request validation failed", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(), VALIDATION_ERROR_CODE, "Request validation failed", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        ApiError body = ApiError.of(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getFieldErrors());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        // ex.getMessage() is deliberately NOT put in the response: ApiError.message
        // is developer/log-facing only (see its javadoc) and can leak internal
        // detail (DB errors, class/field names) to API callers. Log it here -
        // with the response no longer carrying it, this is the only server-side
        // trace left to diagnose the 500 from.
        log.error("Unhandled exception while handling request", ex);
        ApiError body = ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), INTERNAL_ERROR_CODE, "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
