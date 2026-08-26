package com.motorinsurance.quote.application;

import com.motorinsurance.shared.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a quote id doesn't resolve to a quote the requesting customer
 * owns - either it truly doesn't exist, or it belongs to someone else
 * (Story 1.6 Boundaries &amp; Constraints, IDOR protection). Both cases are
 * indistinguishable on purpose: a 404, never a 403, so a caller can't use
 * the response to tell "not yours" apart from "doesn't exist". Maps to HTTP
 * 404 with code {@code QUOTE_NOT_FOUND} through the generic {@code ApiException}
 * handler in {@code shared.api.GlobalExceptionHandler} - no one-off catch block.
 */
public class QuoteNotFoundException extends ApiException {

    public QuoteNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND.value(), "QUOTE_NOT_FOUND", "Quote not found: " + id);
    }
}
