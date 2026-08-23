package com.motorinsurance.shared.api;

import java.time.Instant;
import java.util.List;

/**
 * Uniform API error envelope (AD-7). Every error response produced by this
 * backend is shaped exactly like this record.
 *
 * <p>{@code code} is a stable, language-independent key namespaced
 * {@code MODULE_REASON} (e.g. {@code AUTH_INVALID_CREDENTIALS}) - it is the
 * only thing the frontend uses to select translated text (AD-8). Every
 * {@code code} a module can emit must have exactly one matching translation
 * entry in that module's i18n namespace, added in the same change.
 *
 * <p>{@code message} is developer/log-facing only and must never be
 * rendered to an end user directly.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldError> fieldErrors) {

    public ApiError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiError of(int status, String code, String message) {
        return new ApiError(Instant.now(), status, code, message, List.of());
    }

    public static ApiError of(int status, String code, String message, List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), status, code, message, fieldErrors);
    }

    /** One field-level validation failure, keyed by the offending request field. */
    public record FieldError(String field, String message) {
    }
}
