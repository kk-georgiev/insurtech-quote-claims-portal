package com.motorinsurance.shared.storage;

import com.motorinsurance.shared.api.ApiError;
import com.motorinsurance.shared.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an uploaded file's own bytes say it is not one of the allowed
 * image types (Story 10.1, Epic 10's first release-blocking upload rule).
 * 400 with code {@code ATTACHMENT_UNSUPPORTED_TYPE} and a field error on the
 * photo input, so the FNOL form (Story 10.3) can point at the file the
 * client has to remove.
 *
 * <p>This is the answer for a PDF renamed {@code photo.jpg}, for a truncated
 * or empty upload, and for a {@code RIFF} container that is not WebP - all
 * of them are simply "the bytes are not a JPEG, PNG or WebP". A rejection is
 * always a specific translated 400, never a generic 500.
 *
 * <p>The display filename is carried into the message so a client with four
 * photos learns which one failed; it is display metadata here exactly as it
 * is in {@link StoredFile}, and never reaches a filesystem path.
 *
 * <p>{@code GlobalExceptionHandler}'s single generic {@code ApiException}
 * handler renders this - {@code shared} adds no handler per exception.
 */
public class UnsupportedAttachmentTypeException extends ApiException {

    /**
     * The multipart part name the claim endpoint (Story 10.2) reads and the
     * FNOL form's photo input (Story 10.3) posts under - the field a client
     * can actually act on.
     */
    private static final String ATTACHMENTS_FIELD = "attachments";

    private final String displayFilename;

    public UnsupportedAttachmentTypeException(String displayFilename) {
        super(
                HttpStatus.BAD_REQUEST.value(),
                "ATTACHMENT_UNSUPPORTED_TYPE",
                "Attachment content is not a supported image type: " + displayFilename,
                List.of(new ApiError.FieldError(
                        ATTACHMENTS_FIELD, "Only JPEG, PNG and WebP images are accepted: " + displayFilename)));
        this.displayFilename = displayFilename;
    }

    public String getDisplayFilename() {
        return displayFilename;
    }
}
