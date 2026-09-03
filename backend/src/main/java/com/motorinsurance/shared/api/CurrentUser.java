package com.motorinsurance.shared.api;

import java.util.UUID;
import org.springframework.security.core.Authentication;

/**
 * The one place that reads the authenticated caller's id (Milestone 4
 * prerequisite P-2). Every controller's {@code Authentication} principal is
 * always the JWT subject's {@link UUID} - {@code
 * auth.config.JwtAuthenticationFilter} is the only thing that ever populates
 * the security context, and it always sets the principal this way.
 *
 * <p>Extracted here because {@code quote.api.QuoteController} and {@code
 * policy.api.PolicyController} had each defined an identical private method,
 * and {@code claim.api.ClaimController} would otherwise have been a third
 * byte-for-byte copy (Epic 8 retro item 50, Milestone 4 PRD prerequisite
 * P-2). Lives in {@code shared.api} rather than any one module, since every
 * controller needs it and none of them owns it.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID currentUserId(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }
}
