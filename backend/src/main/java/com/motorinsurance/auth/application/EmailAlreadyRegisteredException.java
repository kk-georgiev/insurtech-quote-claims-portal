package com.motorinsurance.auth.application;

import com.motorinsurance.shared.api.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when self-registration is attempted with an email already present
 * in {@code users}. Maps to HTTP 409 with code {@code AUTH_EMAIL_TAKEN}
 * through the generic {@code ApiException} handler in
 * {@code shared.api.GlobalExceptionHandler} - no one-off catch block.
 */
public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException(String email) {
        super(HttpStatus.CONFLICT.value(), "AUTH_EMAIL_TAKEN", "Email already registered: " + email);
    }
}
