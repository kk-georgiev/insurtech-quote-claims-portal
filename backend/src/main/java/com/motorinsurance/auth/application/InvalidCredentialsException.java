package com.motorinsurance.auth.application;

import com.motorinsurance.shared.api.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown by {@link AuthenticationService#login} for both an unknown email
 * and a registered email with the wrong password - the exact same
 * exception, carrying no field or detail that distinguishes the two cases
 * (Story 1.3 Boundaries &amp; Constraints, AD-3 no user enumeration). Maps to
 * HTTP 401 with code {@code AUTH_INVALID_CREDENTIALS} through the generic
 * {@code ApiException} handler in {@code shared.api.GlobalExceptionHandler}
 * - no one-off catch block.
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED.value(), "AUTH_INVALID_CREDENTIALS", "Invalid email or password");
    }
}
