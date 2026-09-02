package com.motorinsurance.claim.persistence;

import com.motorinsurance.claim.domain.Claim;
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
}
