package com.motorinsurance.policy.application;

import java.util.UUID;

/**
 * Signals that {@code uq_policies_quote_id} refused a second policy for a
 * quote that already has one - i.e. this caller lost a concurrent-accept
 * race (Architecture Spine AD-5, M3).
 *
 * <p>Deliberately <strong>not</strong> a {@code shared.api.ApiException}:
 * it must never reach a client. Losing the race is not an error from the
 * caller's point of view - the acceptance use case catches this, re-reads
 * the policy the winner committed, and returns it with 200, exactly as it
 * would for an uncontended replay. If this ever escaped as far as
 * {@code GlobalExceptionHandler}, a 500 would be the correct signal that
 * the handling above it is missing.
 *
 * <p>Throwing (rather than recovering in place) is required, not stylistic:
 * a constraint violation leaves the JPA persistence context and the
 * surrounding transaction unusable, so the recovery read has to happen in a
 * new transaction after this one has rolled back. See
 * {@code quote.application.QuoteAcceptanceService}.
 */
public class PolicyAlreadyIssuedException extends RuntimeException {

    public PolicyAlreadyIssuedException(UUID quoteId, Throwable cause) {
        super("A policy already exists for quote " + quoteId, cause);
    }
}
