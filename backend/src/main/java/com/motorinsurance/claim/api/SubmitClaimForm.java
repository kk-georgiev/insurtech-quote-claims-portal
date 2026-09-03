package com.motorinsurance.claim.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The scalar fields of {@code POST /api/v1/claims}'s {@code
 * multipart/form-data} body (Story 10.2, FR-M4-04). Photos travel as a
 * sibling {@code List<MultipartFile>} parameter on {@code
 * ClaimController#submit} rather than a field here, mirroring how {@code
 * shared.storage} keeps the servlet type out of validated data altogether.
 *
 * <p>{@code description}/{@code location} bounds (FR-M4-06: "the description
 * has an enforced minimum and maximum length") have no number specified
 * anywhere in the business analysis beyond "reasonable" - these are this
 * story's own implementation choice, the same kind of judgment call {@code
 * quote.api.CreateQuoteRequest} already documents for its own sanity
 * ceilings.
 *
 * <p>{@code incidentDate} is only checked for presence here: whether it is
 * in the future depends on the business zone's today, which is {@code
 * claim.application.ClaimIncidentDateInFutureException}'s job (Architecture
 * Spine AD-6), not Bean Validation's - the same split {@code
 * AcceptQuoteRequest.coverageStart} already uses for the identical reason.
 */
public record SubmitClaimForm(
        @NotNull UUID policyId,
        @NotNull LocalDate incidentDate,
        @NotBlank @Size(min = 10, max = 2000) String description,
        @NotBlank @Size(min = 2, max = 200) String location) {
}
