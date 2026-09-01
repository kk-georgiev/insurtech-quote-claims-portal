package com.motorinsurance.shared.storage;

import com.motorinsurance.shared.api.ApiError;
import com.motorinsurance.shared.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown when one uploaded file is larger than the configured per-file cap
 * (Story 10.1). 400 with code {@code ATTACHMENT_TOO_LARGE} and a field error
 * on the photo input.
 *
 * <p>The cap this reports is {@code storage.attachment.max-file-size-bytes},
 * the single configured value {@link AttachmentValidator} reads - not a
 * literal at a call site, and not Spring's own multipart limit, which is
 * deliberately set higher in {@code application.yml} so that our cap is the
 * one a client ever meets. Without that, the servlet layer would reject a
 * large upload before any application code ran and surface an opaque 500
 * instead of this.
 */
public class AttachmentTooLargeException extends ApiException {

    /** Same photo input as the other attachment rejections. */
    private static final String ATTACHMENTS_FIELD = "attachments";

    private final String displayFilename;
    private final long sizeBytes;
    private final long maxFileSizeBytes;

    public AttachmentTooLargeException(String displayFilename, long sizeBytes, long maxFileSizeBytes) {
        super(
                HttpStatus.BAD_REQUEST.value(),
                "ATTACHMENT_TOO_LARGE",
                "Attachment " + displayFilename + " is " + sizeBytes + " bytes, over the "
                        + maxFileSizeBytes + " byte limit",
                List.of(new ApiError.FieldError(
                        ATTACHMENTS_FIELD,
                        "This file is larger than the " + maxFileSizeBytes + " byte limit: " + displayFilename)));
        this.displayFilename = displayFilename;
        this.sizeBytes = sizeBytes;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public String getDisplayFilename() {
        return displayFilename;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }
}
