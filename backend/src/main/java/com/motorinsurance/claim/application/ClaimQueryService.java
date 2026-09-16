package com.motorinsurance.claim.application;

import com.motorinsurance.claim.domain.Attachment;
import com.motorinsurance.claim.domain.Claim;
import com.motorinsurance.claim.persistence.AttachmentRepository;
import com.motorinsurance.claim.persistence.ClaimRepository;
import com.motorinsurance.shared.storage.Storage;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code claim} module's read entry point (Story 10.4) - {@code
 * listForCustomer}/{@code getById} mirror {@code
 * policy.application.PolicyService}'s identically-named methods exactly:
 * ownership is enforced inside the repository query itself, never
 * fetched-then-checked in Java, so someone else's claim is a 404, never a
 * 403.
 *
 * <p>{@link #downloadAttachment} is this story's one departure from that
 * pattern: it is reachable by the claim's own CLIENT owner <em>or any
 * LIQUIDATOR</em> (a dual-role check its own AC forbids from ever answering
 * 403), so the role/ownership branch lives here rather than in a repository
 * query or a {@code @PreAuthorize} expression - see {@link
 * ClaimAttachmentNotFoundException}'s own Javadoc for why all three "you may
 * not have this" cases collapse into one exception.
 */
@Service
public class ClaimQueryService {

    private final ClaimRepository claimRepository;
    private final AttachmentRepository attachmentRepository;
    private final Storage storage;

    public ClaimQueryService(ClaimRepository claimRepository, AttachmentRepository attachmentRepository, Storage storage) {
        this.claimRepository = claimRepository;
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
    }

    /**
     * Owner-scoped list, newest first (Story 10.4, mirrors {@code
     * PolicyService.listForCustomer}) - the same {@link ClaimView} the
     * detail read returns, not a slimmed-down summary, so one shape serves
     * both screens.
     */
    @Transactional(readOnly = true)
    public List<ClaimView> listForCustomer(UUID customerId) {
        return claimRepository.findAllByCustomerIdOrderBySubmittedAtDesc(customerId).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * Owner-scoped detail read (Story 10.4, mirrors {@code
     * PolicyService.getById}). A claim that is not this customer's is
     * indistinguishable from one that does not exist.
     */
    @Transactional(readOnly = true)
    public ClaimView getById(UUID id, UUID customerId) {
        Claim claim = claimRepository.findByIdAndCustomerId(id, customerId).orElseThrow(() -> new ClaimNotFoundException(id));
        return toView(claim);
    }

    /**
     * The bytes, content type and display filename behind one attachment
     * (Story 10.4) - permitted only to the claim's own CLIENT owner or any
     * LIQUIDATOR (epic-10-context.md, Reading back). Every other caller,
     * including the wrong CLIENT or an AGENT/ADMINISTRATOR, gets the same
     * {@link ClaimAttachmentNotFoundException} a genuinely missing
     * attachment would - never a 403, per this endpoint's own AC.
     *
     * <p>Takes {@code customerId}/{@code isLiquidator} as plain values,
     * already resolved by the controller from the {@code Authentication}
     * (via {@code CurrentUser.currentUserId}/{@code CurrentUser.hasRole}),
     * rather than an {@code Authentication} itself - this module's
     * application layer stays a plain-value API, matching every other method
     * here and in {@code PolicyService}.
     *
     * <p>{@code claimId} is resolved with an <em>unscoped</em> lookup
     * deliberately: a LIQUIDATOR is allowed to reach any claim's attachment,
     * so the ownership check happens explicitly below rather than in the
     * query, unlike every other read in this class.
     */
    @Transactional(readOnly = true)
    public AttachmentContent downloadAttachment(UUID claimId, UUID attachmentId, UUID customerId, boolean isLiquidator) {
        Claim claim = claimRepository.findById(claimId).orElseThrow(() -> new ClaimAttachmentNotFoundException(claimId, attachmentId));

        boolean isOwner = claim.getCustomerId().equals(customerId);
        if (!isOwner && !isLiquidator) {
            throw new ClaimAttachmentNotFoundException(claimId, attachmentId);
        }

        Attachment attachment = attachmentRepository
                .findByIdAndClaimId(attachmentId, claimId)
                .orElseThrow(() -> new ClaimAttachmentNotFoundException(claimId, attachmentId));

        byte[] bytes = storage.read(attachment.getStorageKey());
        return new AttachmentContent(bytes, attachment.getContentType(), attachment.getDisplayFilename());
    }

    private ClaimView toView(Claim claim) {
        List<AttachmentView> attachments = attachmentRepository.findAllByClaimId(claim.getId()).stream()
                .map(ClaimQueryService::toAttachmentView)
                .toList();
        return new ClaimView(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getPolicyId(),
                claim.getPolicyNumber(),
                claim.getIncidentDate(),
                claim.getDescription(),
                claim.getLocation(),
                claim.getStatus(),
                claim.getSubmittedAt(),
                attachments,
                // Synthetic, one entry only - see ClaimView.StatusHistoryEntry's
                // own Javadoc. A read-back claim, same as a freshly submitted
                // one, has only ever had this one event so far.
                List.of(new ClaimView.StatusHistoryEntry(claim.getStatus(), claim.getSubmittedAt())));
    }

    private static AttachmentView toAttachmentView(Attachment attachment) {
        return new AttachmentView(
                attachment.getId(),
                attachment.getDisplayFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getUploadedAt());
    }

    /** The bytes and metadata a download response is built from (Story 10.4). */
    public record AttachmentContent(byte[] bytes, String contentType, String displayFilename) {
    }
}
