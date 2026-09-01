package com.motorinsurance.policy.application;

import com.motorinsurance.shared.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a policy id doesn't resolve to a policy the requesting
 * customer owns - either it truly doesn't exist, or it belongs to someone
 * else (Story 8.3, Architecture Spine AD-10). Both cases are
 * indistinguishable on purpose: a 404, never a 403, so a caller can't use
 * the response to tell "not yours" apart from "doesn't exist". 403 stays
 * reserved for a role mismatch, which is a different failure (M1 AD-4).
 *
 * <p>Maps to HTTP 404 with code {@code POLICY_NOT_FOUND} through the
 * generic {@code ApiException} handler in {@code
 * shared.api.GlobalExceptionHandler} - no one-off catch block, exactly as
 * {@code quote.application.QuoteNotFoundException} does.
 */
public class PolicyNotFoundException extends ApiException {

    public PolicyNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND.value(), "POLICY_NOT_FOUND", "Policy not found: " + id);
    }
}
