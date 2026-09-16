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

    /**
     * True when the authenticated caller holds {@code role} (Story 10.4) -
     * checks the {@code ROLE_<role>} authority format {@code
     * auth.config.JwtAuthenticationFilter} always sets, matching what
     * {@code @PreAuthorize("hasRole('...')")} checks internally. Exists for
     * {@code claim.application.ClaimQueryService}'s attachment-download
     * branch, which cannot use {@code @PreAuthorize} role-gating at all (that
     * throws a 403 for the wrong role, forbidden by that endpoint's own AC) -
     * this is the one authorization helper besides {@link
     * #currentUserId(Authentication)} that {@code shared.api} exposes, and no
     * other module needs a second one.
     */
    public static boolean hasRole(Authentication authentication, String role) {
        String authority = "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
