package com.motorinsurance.quote.api;

import com.motorinsurance.quote.application.QuoteService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quote module's public endpoint (Story 1.5, first real consumer of Story
 * 1.4's shared gate). {@code @PreAuthorize} is how this module declares its
 * own required role without {@code auth.config.SecurityConfig} knowing this
 * module's URL shape (see that class's javadoc) - the underlying
 * "authenticated at all" gate for every non-public path is already Story
 * 1.4's job.
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
    public QuoteResponse calculate(@Valid @RequestBody CreateQuoteRequest request) {
        return quoteService.calculate(request);
    }
}
