package com.motorinsurance.policy.api;

import static com.motorinsurance.shared.api.CurrentUser.currentUserId;

import com.motorinsurance.policy.application.PolicyService;
import com.motorinsurance.policy.application.PolicyView;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Policy module's public endpoints (Story 8.3, FR-M3-10). A module owns its
 * own URL space (Architecture Spine AD-2): policy reads live here, under
 * {@code /api/v1/policies}, while acceptance stays a command on a quote in
 * {@code quote.api} because that is what it is.
 *
 * <p>Both endpoints return {@link PolicyView} - the same shape the accept
 * endpoint already returns, so a client that just issued a policy and one
 * that opens it a month later parse identical JSON.
 *
 * <p>{@code @PreAuthorize} declares this module's required role without
 * {@code auth.config.SecurityConfig} knowing its URL shape, and the
 * customer id comes from the {@code Authentication} principal - never a
 * request parameter (AD-10). Ownership is enforced inside the query, so a
 * policy belonging to someone else is a 404, never a 403.
 */
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    /**
     * A bare, newest-first JSON array of the same DTO {@link #getById}
     * returns (AD-12): no envelope, no page metadata, no limit parameter
     * this milestone.
     */
    @GetMapping
    @PreAuthorize("hasRole('CLIENT')")
    public List<PolicyView> list(Authentication authentication) {
        return policyService.listForCustomer(currentUserId(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public PolicyView getById(@PathVariable("id") UUID id, Authentication authentication) {
        return policyService.getById(id, currentUserId(authentication));
    }
}
