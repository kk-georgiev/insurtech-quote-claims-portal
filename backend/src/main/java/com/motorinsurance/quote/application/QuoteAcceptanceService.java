package com.motorinsurance.quote.application;

import com.motorinsurance.policy.application.PolicyAlreadyIssuedException;
import com.motorinsurance.policy.application.PolicyService;
import com.motorinsurance.policy.application.PolicyView;
import com.motorinsurance.quote.api.AcceptQuoteRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The acceptance use case's entry point (Architecture Spine AD-1, M3) -
 * what {@code quote.api} calls. Its whole job beyond delegating is the
 * concurrent-accept race: turning a lost race into the same successful
 * response an uncontended replay gets (AD-5).
 *
 * <p>Deliberately <strong>not</strong> {@code @Transactional}. Under a race
 * the losing insert violates {@code uq_policies_quote_id}, and a constraint
 * violation leaves both the JPA persistence context and the surrounding
 * transaction unusable - Hibernate marks it rollback-only, so nothing read
 * or written after that point would commit. The recovery read therefore has
 * to happen <em>after</em> the failed transaction has rolled back, in a new
 * one. That is why the transactional sequence lives in
 * {@link QuoteAcceptanceTransaction} and this class stays outside it: a
 * self-invocation would bypass Spring's proxy and get no transaction at
 * all, and wrapping this method in one would put the recovery read back
 * inside the poisoned transaction it is escaping.
 *
 * <p>Observable behaviour is exactly AD-5's contract - the winner gets 201,
 * the loser gets 200 with the same policy, and the database's unique
 * constraint, not any application-level check, is what guarantees only one
 * policy exists.
 */
@Service
public class QuoteAcceptanceService {

    private static final Logger log = LoggerFactory.getLogger(QuoteAcceptanceService.class);

    private final QuoteAcceptanceTransaction acceptanceTransaction;
    private final PolicyService policyService;

    public QuoteAcceptanceService(QuoteAcceptanceTransaction acceptanceTransaction, PolicyService policyService) {
        this.acceptanceTransaction = acceptanceTransaction;
        this.policyService = policyService;
    }

    public AcceptanceOutcome accept(UUID quoteId, UUID customerId, AcceptQuoteRequest request) {
        try {
            return acceptanceTransaction.acceptAndIssue(quoteId, customerId, request);
        } catch (PolicyAlreadyIssuedException ex) {
            // The winner has committed by now (its unique index entry is what
            // rejected us), so this read - in its own fresh transaction -
            // sees the policy it created. The loser's own work, including
            // its accepted_at write, rolled back with the failed transaction;
            // the winner's accepted_at stands.
            log.info("Concurrent acceptance of quote {}; returning the policy the winning request issued", quoteId);
            PolicyView winner = policyService
                    .findByQuoteId(quoteId, customerId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Policy for quote " + quoteId + " vanished after a unique-constraint violation"));
            return AcceptanceOutcome.existing(winner);
        }
    }
}
