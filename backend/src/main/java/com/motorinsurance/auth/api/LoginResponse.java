package com.motorinsurance.auth.api;

/**
 * Login response DTO - exactly the signed JWT (Story 1.3). The frontend
 * decodes its payload client-side (no signature verification, that's the
 * backend's job) purely to display the role; the raw token is what actually
 * gets stored (see {@code frontend/src/api/authToken.ts}).
 */
public record LoginResponse(String token) {
}
