package com.motorinsurance.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration request DTO. Deliberately has no {@code role} field - the
 * service always hardcodes {@code Role.CLIENT} (privilege-escalation
 * invariant, see Story 1.2 Boundaries &amp; Constraints). Bean Validation
 * here mirrors the {@code users} table's DB-layer constraints.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password) {
}
