package com.motorinsurance.claim.application;

import com.motorinsurance.shared.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a claim id doesn't resolve to a claim the requesting customer
 * owns - either it truly doesn't exist, or it belongs to someone else (Story
 * 10.4, mirrors {@code policy.application.PolicyNotFoundException} exactly).
 * Both cases are indistinguishable on purpose: a 404, never a 403, so a
 * caller can't use the response to tell "not yours" apart from "doesn't
 * exist". 403 stays reserved for a role mismatch, which is a different
 * failure.
 *
 * <p>Maps to HTTP 404 with code {@code CLAIM_NOT_FOUND} through the generic
 * {@code ApiException} handler in {@code shared.api.GlobalExceptionHandler} -
 * no one-off catch block.
 */
public class ClaimNotFoundException extends ApiException {

    public ClaimNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND.value(), "CLAIM_NOT_FOUND", "Claim not found: " + id);
    }
}
