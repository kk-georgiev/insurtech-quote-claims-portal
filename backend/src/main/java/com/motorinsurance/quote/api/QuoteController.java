package com.motorinsurance.quote.api;

import static com.motorinsurance.shared.api.CurrentUser.currentUserId;

import com.motorinsurance.policy.application.PolicyView;
import com.motorinsurance.quote.application.AcceptanceOutcome;
import com.motorinsurance.quote.application.QuoteAcceptanceService;
import com.motorinsurance.quote.application.QuoteService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quote module's public endpoints (Story 1.5's calculation, Story 1.6's
 * persistence/retrieval, Story 6.3's owner-scoped list, and Story 8.1's
 * acceptance - the one command here, and the only endpoint that returns
 * another module's representation). {@code @PreAuthorize} is how this module declares
 * its own required role without {@code auth.config.SecurityConfig} knowing
 * this module's URL shape (see that class's javadoc) - the underlying
 * "authenticated at all" gate for every non-public path is already Story
 * 1.4's job.
 *
 * <p>The current user's id comes from {@code Authentication.getPrincipal()} -
 * {@code auth.config.JwtAuthenticationFilter} sets it to the JWT's subject
 * (the user id) directly, not a username/{@code UserDetails}, so it's cast
 * straight to {@link UUID} here rather than resolved through a repository
 * lookup this module has no business making into {@code auth}'s data (AD-2).
 *
 * <p>{@code calculate} returns {@code 201 Created}: since Story 1.6 it
 * persists a new quote as part of the call, so it is a resource-creating
 * endpoint like {@code POST /api/v1/auth/register} (Epic 1 retro action item
 * 7 - the two resource-creating endpoints previously disagreed, 200 vs 201;
 * Story 1.6's I/O matrix was renegotiated to 201 to match, 2026-08-27).
 */
@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private final QuoteService quoteService;
    private final QuoteAcceptanceService quoteAcceptanceService;

    public QuoteController(QuoteService quoteService, QuoteAcceptanceService quoteAcceptanceService) {
        this.quoteService = quoteService;
        this.quoteAcceptanceService = quoteAcceptanceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public QuoteResponse calculate(@Valid @RequestBody CreateQuoteRequest request, Authentication authentication) {
        return quoteService.calculate(request, currentUserId(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public QuoteResponse getById(@PathVariable("id") UUID id, Authentication authentication) {
        return quoteService.getById(id, currentUserId(authentication));
    }

    /**
     * Story 6.3 - a bare, ordered JSON array of the same {@link QuoteResponse}
     * shape {@link #getById} returns (Architecture Spine AD-12): no envelope,
     * no pagination this milestone.
     */
    @GetMapping
    @PreAuthorize("hasRole('CLIENT')")
    public List<QuoteResponse> list(Authentication authentication) {
        return quoteService.listForCustomer(currentUserId(authentication));
    }

    /**
     * Story 8.1 - acceptance is a command on a quote that happens to produce
     * a policy, so it lives in this module's URL space and returns the
     * created policy representation (Architecture Spine AD-2, M3): one round
     * trip both commits and renders the result.
     *
     * <p>The status code is per-call rather than fixed, which is why this is
     * the one endpoint here without {@code @ResponseStatus}: the first
     * acceptance creates a policy and returns 201, while every later call
     * for the same quote returns that same policy with 200 (AD-5). An
     * already-accepted quote is a success, never a conflict - the only 409
     * this endpoint reports is an expired offer.
     */
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<PolicyView> accept(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AcceptQuoteRequest request,
            Authentication authentication) {
        AcceptanceOutcome outcome = quoteAcceptanceService.accept(id, currentUserId(authentication), request);

        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(outcome.policy());
    }
}
