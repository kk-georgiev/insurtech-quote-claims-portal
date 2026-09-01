package com.motorinsurance.policy.persistence;

import com.motorinsurance.policy.domain.Policy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    /**
     * The idempotent-replay lookup (Architecture Spine AD-5): the policy a
     * given quote was already accepted into. Ownership-scoped in the query
     * itself (AD-10) - no caller fetches by {@code quoteId} alone and
     * compares the customer in Java, so someone else's policy is
     * indistinguishable from one that does not exist.
     */
    Optional<Policy> findByQuoteIdAndCustomerId(UUID quoteId, UUID customerId);

    /**
     * Owner-scoped detail read (Story 8.3, AD-10) - {@code id} alone is
     * never enough. A policy that exists but belongs to someone else comes
     * back empty here, exactly like one that does not exist, and both
     * become the same 404 rather than a 403 that would confirm the id.
     */
    Optional<Policy> findByIdAndCustomerId(UUID id, UUID customerId);

    /**
     * Owner-scoped list, newest first (Story 8.3, AD-10/AD-12). Ordered by
     * {@code issued_at} - the policy's own creation fact, not the quote's -
     * and the query itself excludes every other customer's policies.
     */
    List<Policy> findAllByCustomerIdOrderByIssuedAtDesc(UUID customerId);

    /**
     * The quote-to-policy links for one customer, resolved in a single
     * query (Story 8.3). Feeds {@code QuoteResponse.policyId} for a whole
     * list of quotes at once rather than one lookup per row - and takes
     * quote ids as plain values, so nothing here joins or reads
     * {@code quotes} (AD-1, AD-4).
     */
    List<Policy> findAllByCustomerIdAndQuoteIdIn(UUID customerId, Collection<UUID> quoteIds);

    /**
     * Allocates the numeric part of the next policy number from the
     * dedicated sequence (AD-7, {@code V9__create_policies_table.sql}) -
     * never "the highest existing number plus one", which BA 7.4 rules out
     * and which duplicates under concurrency.
     *
     * <p>{@code nextval} is non-transactional: a value consumed by a
     * transaction that later rolls back is simply never used. Gaps are
     * expected and acceptable.
     */
    @Query(value = "SELECT nextval('policy_number_seq')", nativeQuery = true)
    long nextPolicyNumberValue();
}
