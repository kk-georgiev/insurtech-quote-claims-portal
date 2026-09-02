package com.motorinsurance.claim.api;

import static com.motorinsurance.shared.api.CurrentUser.currentUserId;

import com.motorinsurance.claim.application.ClaimSubmissionService;
import com.motorinsurance.claim.application.ClaimView;
import com.motorinsurance.claim.application.SubmitClaimCommand;
import com.motorinsurance.shared.storage.AttachmentValidator;
import com.motorinsurance.shared.storage.AttachmentValidator.Candidate;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code claim} module's public endpoint for filing a claim (Story 10.2,
 * FR-M4-04). Mirrors {@code policy.api.PolicyController}'s shape: a thin
 * controller that converts the transport-level request into a command and
 * delegates everything else to {@code claim.application}.
 *
 * <p>{@code multipart/form-data} rather than JSON, since a claim and its
 * photos arrive in one request (M4-AD-3). {@link SubmitClaimForm}'s scalar
 * fields bind and validate via {@code @Valid @ModelAttribute} - a failure
 * there routes through the same {@code MethodArgumentNotValidException}
 * handler {@code @Valid @RequestBody} DTOs already use, so no new exception
 * handling is needed for a missing or malformed field.
 *
 * <p>{@code attachments} is a sibling parameter, not a {@link
 * SubmitClaimForm} field, and its count is checked via {@link
 * AttachmentValidator#validateCount(int)} <em>before</em> any file's bytes
 * are read - closing a Story 10.1 review finding that the count cap was
 * previously enforced only after every part was already materialized.
 */
@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {

    private final ClaimSubmissionService claimSubmissionService;
    private final AttachmentValidator attachmentValidator;

    public ClaimController(ClaimSubmissionService claimSubmissionService, AttachmentValidator attachmentValidator) {
        this.claimSubmissionService = claimSubmissionService;
        this.attachmentValidator = attachmentValidator;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public ClaimView submit(
            @Valid @ModelAttribute SubmitClaimForm form,
            @RequestParam(name = "attachments", required = false) List<MultipartFile> attachments,
            Authentication authentication) {
        attachmentValidator.validateCount(attachments == null ? 0 : attachments.size());

        SubmitClaimCommand command = new SubmitClaimCommand(
                form.policyId(), form.incidentDate(), form.description(), form.location(), toCandidates(attachments));
        return claimSubmissionService.submit(currentUserId(authentication), command);
    }

    private static List<Candidate> toCandidates(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream().map(ClaimController::toCandidate).toList();
    }

    private static Candidate toCandidate(MultipartFile file) {
        try {
            return new Candidate(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded attachment " + file.getOriginalFilename(), e);
        }
    }
}
