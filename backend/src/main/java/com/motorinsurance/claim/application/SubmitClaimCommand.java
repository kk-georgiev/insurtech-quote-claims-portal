package com.motorinsurance.claim.application;

import com.motorinsurance.shared.storage.AttachmentValidator.Candidate;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The one, fully-formed submission command {@code claim} accepts (mirrors
 * {@code policy.application.IssuePolicyCommand}'s role). Attachments arrive
 * as {@link Candidate}s, not {@code MultipartFile}s: the servlet type never
 * crosses below {@code claim.api}, matching the same boundary {@code
 * shared.storage} already draws around itself.
 */
public record SubmitClaimCommand(
        UUID policyId, LocalDate incidentDate, String description, String location, List<Candidate> attachments) {
}
