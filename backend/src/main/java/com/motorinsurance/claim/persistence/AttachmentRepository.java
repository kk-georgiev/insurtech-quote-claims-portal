package com.motorinsurance.claim.persistence;

import com.motorinsurance.claim.domain.Attachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Every attachment belonging to one claim (Story 10.4) - used by
     * {@code ClaimQueryService} to build a claim's detail view. Ownership of
     * the claim itself is checked by the caller before this ever runs; this
     * query only narrows by {@code claim_id}.
     */
    List<Attachment> findAllByClaimId(UUID claimId);

    /**
     * Claim-scoped attachment read (Story 10.4) - {@code id} alone is never
     * enough. An attachment that exists but belongs to a different claim
     * comes back empty here, exactly like one that does not exist, feeding
     * the download endpoint's uniform 404.
     */
    Optional<Attachment> findByIdAndClaimId(UUID id, UUID claimId);
}
