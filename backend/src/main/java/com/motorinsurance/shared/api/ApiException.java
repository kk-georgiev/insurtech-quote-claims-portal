package com.motorinsurance.shared.api;

import java.util.List;

/**
 * Base type for every module-specific error this backend can throw (AD-7).
 * A module extends this with a concrete exception (e.g.
 * {@code auth.application.EmailAlreadyRegisteredException}) carrying its own
 * HTTP status, stable {@code code} (namespaced {@code MODULE_REASON}) and
 * dev-facing message. {@link GlobalExceptionHandler} handles this base type
 * with exactly one generic {@code @ExceptionHandler(ApiException.class)}
 * method - {@code shared} never imports a module's concrete exception
 * classes, which would invert AD-2's dependency direction.
 */
public abstract class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final List<ApiError.FieldError> fieldErrors;

    protected ApiException(int status, String code, String message) {
        this(status, code, message, List.of());
    }

    protected ApiException(int status, String code, String message, List<ApiError.FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<ApiError.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
