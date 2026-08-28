package com.motorinsurance.auth.application;

import com.motorinsurance.shared.api.ApiError;
import com.motorinsurance.shared.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown when self-registration is attempted with an email already present
 * in {@code users}. Maps to HTTP 409 with code {@code AUTH_EMAIL_TAKEN}
 * through the generic {@code ApiException} handler in
 * {@code shared.api.GlobalExceptionHandler} - no one-off catch block.
 *
 * <p>Attaches a {@code FieldError} for {@code email}, matching the convention
 * Story 1.5's pricing exceptions established (Epic 1 retro action item 2).
 * Unlike {@link InvalidCredentialsException}, naming the field here leaks
 * nothing a caller doesn't already know: registration necessarily tells the
 * user their chosen email is taken (that is the whole point of the 409), so
 * there is no enumeration concern to weigh against a clearer error shape.
 */
public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException(String email) {
        super(
                HttpStatus.CONFLICT.value(),
                "AUTH_EMAIL_TAKEN",
                "Email already registered: " + email,
                List.of(new ApiError.FieldError("email", "Email already registered")));
    }
}
