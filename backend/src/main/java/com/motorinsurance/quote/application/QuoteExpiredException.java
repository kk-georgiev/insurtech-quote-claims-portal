package com.motorinsurance.quote.application;

import com.motorinsurance.shared.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a client tries to accept a quote whose offer-validity window
 * has closed - {@code today} is past {@code valid_until} with no acceptance
 * (Story 8.1, FR-M3-05). The one genuine conflict the accept endpoint
 * reports: 409 with code {@code QUOTE_EXPIRED} (Architecture Spine AD-11,
 * M3).
 *
 * <p>An already-accepted quote is deliberately <strong>not</strong> a
 * conflict - it is a successful 200 carrying the existing policy (AD-5), so
 * no {@code QUOTE_ALREADY_ACCEPTED} counterpart to this class exists or
 * should be added.
 *
 * <p>The boundary is inclusive (AD-6): a quote accepted <em>on</em> its
 * {@code valid_until} date is still valid, which is what {@code
 * Quote#status} already encodes and this exception simply reports.
 */
public class QuoteExpiredException extends ApiException {

    public QuoteExpiredException(UUID id) {
        super(HttpStatus.CONFLICT.value(), "QUOTE_EXPIRED", "Quote offer has expired: " + id);
    }
}
