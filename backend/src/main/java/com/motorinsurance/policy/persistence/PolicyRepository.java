package com.motorinsurance.policy.persistence;

import com.motorinsurance.policy.domain.Policy;
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
