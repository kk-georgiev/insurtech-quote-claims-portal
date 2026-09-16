package com.motorinsurance.claim.application;

import com.motorinsurance.claim.domain.Attachment;
import com.motorinsurance.claim.domain.Claim;
import com.motorinsurance.claim.domain.ClaimNumber;
import com.motorinsurance.claim.persistence.AttachmentRepository;
import com.motorinsurance.claim.persistence.ClaimRepository;
import com.motorinsurance.policy.application.PolicyService;
import com.motorinsurance.policy.application.PolicyView;
import com.motorinsurance.shared.storage.AttachmentValidator;
import com.motorinsurance.shared.storage.AttachmentValidator.ValidatedAttachment;
import com.motorinsurance.shared.storage.Storage;
import com.motorinsurance.shared.storage.StoredFile;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code claim} module's entry point for filing a claim (Story 10.2,
 * FR-M4-04). Reaches {@code policy} only through {@code policy.application}
 * (M4-AD-5, NFR-9) - {@link PolicyService#getById} is already owner-scoped
 * and already throws {@code PolicyNotFoundException} for a policy that does
 * not exist or belongs to someone else, so no new "not found" code is needed
 * here.
 *
 * <p>{@code clock} is the shared business-zone clock (AD-6): both the
 * future-incident-date check and the claim number's year come from it, never
 * from {@code LocalDate.now()} / {@code Instant.now()} directly.
 */
@Service
public class ClaimSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(ClaimSubmissionService.class);

    private final PolicyService policyService;
    private final ClaimRepository claimRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentValidator attachmentValidator;
    private final Storage storage;
    private final Clock clock;

    public ClaimSubmissionService(
            PolicyService policyService,
            ClaimRepository claimRepository,
            AttachmentRepository attachmentRepository,
            AttachmentValidator attachmentValidator,
            Storage storage,
            Clock clock) {
        this.policyService = policyService;
        this.claimRepository = claimRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentValidator = attachmentValidator;
        this.storage = storage;
        this.clock = clock;
    }

    /**
     * Validates the whole submission, stores its photos, and inserts the
     * claim plus its attachment rows in one transaction (M4-AD-3): if
     * anything fails, neither a claim row nor an attachment row exists
     * afterwards, and any bytes already written are best-effort deleted.
     * Correctness never depends on that delete succeeding - a byte left
     * behind is unreachable because serving one requires an {@code
     * attachments} row (M4-AD-4), which this method only creates on success.
     */
    @Transactional
    public ClaimView submit(UUID customerId, SubmitClaimCommand command) {
        PolicyView policy = policyService.getById(command.policyId(), customerId);

        LocalDate today = LocalDate.now(clock);
        if (command.incidentDate().isAfter(today)) {
            throw new ClaimIncidentDateInFutureException(command.incidentDate(), today);
        }
        if (command.incidentDate().isBefore(policy.coverageStart()) || command.incidentDate().isAfter(policy.coverageEnd())) {
            throw new ClaimIncidentOutsideCoverageException(
                    command.incidentDate(), policy.coverageStart(), policy.coverageEnd());
        }

        List<ValidatedAttachment> validated = attachmentValidator.validate(command.attachments());

        List<StoredFile> storedFiles = new ArrayList<>(validated.size());
        try {
            for (ValidatedAttachment attachment : validated) {
                storedFiles.add(storage.store(attachment.content(), attachment.type().mimeType(), attachment.displayFilename()));
            }
            return persist(customerId, command, policy, storedFiles);
        } catch (RuntimeException ex) {
            for (StoredFile stored : storedFiles) {
                try {
                    storage.delete(stored.storageKey());
                } catch (RuntimeException cleanupEx) {
                    log.warn(
                            "Failed to clean up orphaned attachment {} after a failed claim submission",
                            stored.storageKey(),
                            cleanupEx);
                }
            }
            throw ex;
        }
    }

    private ClaimView persist(UUID customerId, SubmitClaimCommand command, PolicyView policy, List<StoredFile> storedFiles) {
        Instant submittedAt = Instant.now(clock);
        int year = LocalDate.ofInstant(submittedAt, clock.getZone()).getYear();
        String claimNumber = ClaimNumber.format(year, claimRepository.nextClaimNumberValue());

        Claim claim = new Claim(
                customerId,
                command.policyId(),
                policy.policyNumber(),
                claimNumber,
                command.incidentDate(),
                command.description(),
                command.location(),
                submittedAt);
        Claim savedClaim = claimRepository.saveAndFlush(claim);

        List<Attachment> attachments = storedFiles.stream()
                .map(f -> new Attachment(
                        savedClaim.getId(), f.storageKey(), f.contentType(), f.sizeBytes(), f.sha256Hex(), f.displayFilename(), submittedAt))
                .toList();
        List<Attachment> savedAttachments = attachmentRepository.saveAll(attachments);

        return toView(savedClaim, savedAttachments);
    }

    private static ClaimView toView(Claim claim, List<Attachment> attachments) {
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
                attachments.stream().map(ClaimSubmissionService::toAttachmentView).toList(),
                // Synthetic, one entry only - see ClaimView.StatusHistoryEntry's
                // own Javadoc (Story 10.4). A freshly submitted claim has never
                // transitioned, so its history is only the SUBMITTED event.
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
}
