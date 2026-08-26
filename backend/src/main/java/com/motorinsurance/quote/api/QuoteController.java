package com.motorinsurance.quote.api;

import com.motorinsurance.quote.application.QuoteService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quote module's public endpoints (Story 1.5's calculation, Story 1.6's
 * persistence/retrieval). {@code @PreAuthorize} is how this module declares
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
 */
@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    public QuoteResponse calculate(@Valid @RequestBody CreateQuoteRequest request, Authentication authentication) {
        return quoteService.calculate(request, currentUserId(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public QuoteResponse getById(@PathVariable UUID id, Authentication authentication) {
        return quoteService.getById(id, currentUserId(authentication));
    }

    private UUID currentUserId(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }
}
