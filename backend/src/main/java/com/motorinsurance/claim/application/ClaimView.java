package com.motorinsurance.claim.application;

import com.motorinsurance.claim.domain.ClaimStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A claim as everything outside {@code claim} sees it (Story 10.2) - the
 * "created claim" the submission endpoint returns. Story 10.4's list and
 * detail endpoints return this same shape (M4-AD-12: the list is the same
 * DTO the detail endpoint returns), so it is defined in {@code application}
 * rather than {@code api} from the start.
 *
 * <p><strong>{@code statusHistory} is synthetic, not a new table.</strong>
 * {@code claim_status_history} does not exist until Story 11.1 (V12); this
 * story populates it with exactly one entry - the claim's current
 * {@code status} paired with {@code submittedAt} - representing only the
 * initial {@link ClaimStatus#SUBMITTED} event, the one fact this story
 * actually has. It is not a placeholder for a richer timeline and must never
 * be padded, inferred, or backfilled with anything else; Story 11.1 replaces
 * it with real persisted history once transitions exist.
 */
public record ClaimView(
        UUID id,
        String claimNumber,
        UUID policyId,
        String policyNumber,
        LocalDate incidentDate,
        String description,
        String location,
        ClaimStatus status,
        Instant submittedAt,
        List<AttachmentView> attachments,
        List<StatusHistoryEntry> statusHistory) {

    /**
     * One entry in a claim's status timeline (Story 10.4). This story's only
     * writer produces exactly one, built from {@code Claim.status}/
     * {@code Claim.submittedAt} - never a second entry, since nothing else is
     * known yet.
     */
    public record StatusHistoryEntry(ClaimStatus status, Instant occurredAt) {
    }
}
