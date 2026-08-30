package com.motorinsurance.quote.application;

import com.motorinsurance.shared.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown when persisting a quote violates {@code quotes.customer_id}'s
 * foreign key into {@code users} - the token identifies a customer id that
 * no longer has a row there. Unreachable through any current API path (no
 * account-deletion feature exists yet), but was previously an unhandled
 * {@code DataIntegrityViolationException} falling through to a bare 500 -
 * see {@code QuoteService#calculate} and {@code QuoteControllerTest
 * #registerClient}'s javadoc. Maps to HTTP 401 with the same
 * {@code AUTH_UNAUTHENTICATED} code the JWT gate itself uses (Story 1.4):
 * a token whose subject no longer identifies a real account is exactly the
 * same class of problem as a missing/invalid token, from the caller's point
 * of view (epic-1-retro-item-5).
 */
public class QuoteCustomerNotFoundException extends ApiException {

    public QuoteCustomerNotFoundException(UUID customerId) {
        super(HttpStatus.UNAUTHORIZED.value(), "AUTH_UNAUTHENTICATED", "No account exists for customer id: " + customerId);
    }
}
