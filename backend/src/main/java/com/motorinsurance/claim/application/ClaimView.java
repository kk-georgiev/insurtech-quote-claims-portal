package com.motorinsurance.claim.application;

import com.motorinsurance.claim.domain.ClaimStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A claim as everything outside {@code claim} sees it (Story 10.2) - the
 * "created claim" the submission endpoint returns. Story 10.4's list and
 * detail endpoints are expected to return this same shape (M4-AD-12: the
 * list is the same DTO the detail endpoint returns), so it is defined in
 * {@code application} rather than {@code api} from the start.
 *
 * <p>Carries no status history - {@code claim_status_history} does not exist
 * until Story 11.1 (V12); a freshly submitted claim has nothing to show
 * beyond its current {@link ClaimStatus#SUBMITTED} status and this
 * {@code submittedAt} timestamp.
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
        List<AttachmentView> attachments) {
}
