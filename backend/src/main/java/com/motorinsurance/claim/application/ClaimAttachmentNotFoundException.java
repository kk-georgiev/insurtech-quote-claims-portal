package com.motorinsurance.claim.application;

import com.motorinsurance.shared.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown by the attachment-download endpoint for every reason a caller may
 * not receive a file (Story 10.4): the attachment genuinely doesn't exist,
 * it belongs to a claim the caller (a CLIENT) doesn't own, or the caller's
 * role is neither the claim's own CLIENT owner nor a LIQUIDATOR. All three
 * are deliberately indistinguishable from the caller's side.
 *
 * <p>The AC this exists for is explicit: "anyone else receives 404, never
 * 403" - including an authenticated AGENT or ADMINISTRATOR, which a
 * {@code hasRole(...)}-style 403 would otherwise leak information to (that
 * the id is real, just the wrong role). {@code
 * claim.api.ClaimController}'s download endpoint therefore uses only
 * {@code @PreAuthorize("isAuthenticated()")}, and {@code
 * claim.application.ClaimQueryService} throws this uniformly for every
 * "you may not have this" branch. Unauthenticated stays a 401, produced by
 * {@code isAuthenticated()} itself before this class is ever reached.
 *
 * <p>Maps to HTTP 404 with code {@code ATTACHMENT_NOT_FOUND} through the
 * generic {@code ApiException} handler in {@code
 * shared.api.GlobalExceptionHandler} - no one-off catch block.
 */
public class ClaimAttachmentNotFoundException extends ApiException {

    public ClaimAttachmentNotFoundException(UUID claimId, UUID attachmentId) {
        super(
                HttpStatus.NOT_FOUND.value(),
                "ATTACHMENT_NOT_FOUND",
                "Attachment not found: " + attachmentId + " on claim " + claimId);
    }
}
