package com.motorinsurance.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request DTO. Bean Validation here is format-only - a blank or
 * malformed email, or a blank password, surfaces as the existing generic
 * 400 validation error and reveals nothing about whether the email is
 * registered (Story 1.3 Boundaries &amp; Constraints). Deliberately no
 * {@code @Size(min=...)} on {@code password}: that's a registration-time
 * business rule (see {@code RegisterRequest}), not a login-format one - a
 * stored account's password could in principle fall outside it (e.g. a
 * future seeded staff account), and rejecting on length here would leak a
 * lookup-independent signal the login flow must not produce.
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email, @NotBlank @Size(max = 100) String password) {
}
