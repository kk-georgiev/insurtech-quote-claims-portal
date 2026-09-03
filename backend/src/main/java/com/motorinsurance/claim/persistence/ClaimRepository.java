package com.motorinsurance.claim.persistence;

import com.motorinsurance.claim.domain.Claim;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    /**
     * Allocates the numeric part of the next claim number from the
     * dedicated sequence (M4-AD-8, {@code V10__create_claims_table.sql}) -
     * never "the highest existing number plus one", mirroring {@code
     * PolicyRepository.nextPolicyNumberValue()} exactly.
     *
     * <p>{@code nextval} is non-transactional: a value consumed by a
     * transaction that later rolls back is simply never used. Gaps are
     * expected and acceptable.
     */
    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimNumberValue();

    /**
     * Owner-scoped detail read (Story 10.4, mirrors {@code
     * PolicyRepository.findByIdAndCustomerId} exactly) - {@code id} alone is
     * never enough. A claim that exists but belongs to someone else comes
     * back empty here, exactly like one that does not exist, and both become
     * the same 404 rather than a 403 that would confirm the id.
     */
    Optional<Claim> findByIdAndCustomerId(UUID id, UUID customerId);

    /**
     * Owner-scoped list, newest first (Story 10.4, mirrors {@code
     * PolicyRepository.findAllByCustomerIdOrderByIssuedAtDesc}). Ordered by
     * {@code submitted_at} - the claim's own creation fact - and the query
     * itself excludes every other customer's claims.
     */
    List<Claim> findAllByCustomerIdOrderBySubmittedAtDesc(UUID customerId);
}
