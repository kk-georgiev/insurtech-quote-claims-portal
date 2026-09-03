package com.motorinsurance.claim.api;

import static com.motorinsurance.shared.api.CurrentUser.currentUserId;

import com.motorinsurance.claim.application.ClaimQueryService;
import com.motorinsurance.claim.application.ClaimQueryService.AttachmentContent;
import com.motorinsurance.claim.application.ClaimSubmissionService;
import com.motorinsurance.claim.application.ClaimView;
import com.motorinsurance.claim.application.SubmitClaimCommand;
import com.motorinsurance.shared.api.CurrentUser;
import com.motorinsurance.shared.storage.AttachmentValidator;
import com.motorinsurance.shared.storage.AttachmentValidator.Candidate;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final ClaimQueryService claimQueryService;
    private final AttachmentValidator attachmentValidator;

    public ClaimController(
            ClaimSubmissionService claimSubmissionService,
            ClaimQueryService claimQueryService,
            AttachmentValidator attachmentValidator) {
        this.claimSubmissionService = claimSubmissionService;
        this.claimQueryService = claimQueryService;
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

    /**
     * A bare, newest-first JSON array of the same DTO {@link #getById}
     * returns (M4-AD-12, mirrors {@code PolicyController.list}): no
     * envelope, no page metadata, no limit parameter this milestone.
     */
    @GetMapping
    @PreAuthorize("hasRole('CLIENT')")
    public List<ClaimView> list(Authentication authentication) {
        return claimQueryService.listForCustomer(currentUserId(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ClaimView getById(@PathVariable("id") UUID id, Authentication authentication) {
        return claimQueryService.getById(id, currentUserId(authentication));
    }

    /**
     * The first byte-streaming endpoint in this codebase (Story 10.4). Only
     * {@code isAuthenticated()}, deliberately not a role check: this
     * endpoint's own AC forbids ever answering 403, including for an
     * authenticated AGENT or ADMINISTRATOR, so the CLIENT-owner-or-LIQUIDATOR
     * branch lives entirely inside {@link ClaimQueryService#downloadAttachment}
     * and every "you may not have this" case surfaces as the same 404 - see
     * {@code claim.application.ClaimAttachmentNotFoundException}'s own
     * Javadoc.
     *
     * <p>{@code Content-Type} is the content-sniffed type {@code
     * ImageContentSniffer} determined at upload time (Story 10.1), never
     * re-derived or trusted from the filename. {@code Content-Disposition:
     * inline} - not {@code attachment} - so the browser renders the image
     * rather than downloading it.
     */
    @GetMapping("/{claimId}/attachments/{attachmentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable("claimId") UUID claimId,
            @PathVariable("attachmentId") UUID attachmentId,
            Authentication authentication) {
        AttachmentContent content = claimQueryService.downloadAttachment(
                claimId, attachmentId, currentUserId(authentication), CurrentUser.hasRole(authentication, "LIQUIDATOR"));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.displayFilename()).build().toString())
                .body(content.bytes());
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
