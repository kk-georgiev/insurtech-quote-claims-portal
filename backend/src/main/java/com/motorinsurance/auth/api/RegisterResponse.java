package com.motorinsurance.auth.api;

import com.motorinsurance.auth.domain.Role;
import java.util.UUID;

/**
 * Registration response DTO. Deliberately excludes the password/hash -
 * the raw password is never returned in any response (Story 1.2
 * Boundaries &amp; Constraints).
 */
public record RegisterResponse(UUID id, String email, Role role) {
}
