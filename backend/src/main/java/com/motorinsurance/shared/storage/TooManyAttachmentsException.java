package com.motorinsurance.shared.storage;

import com.motorinsurance.shared.api.ApiError;
import com.motorinsurance.shared.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a batch carries more files than the configured count cap
 * (Story 10.1). 400 with code {@code ATTACHMENT_TOO_MANY} and a field error
 * on the photo input.
 *
 * <p>The whole batch is rejected and nothing is written: the count is a
 * property of the submission, so there is no "first N are fine" reading of
 * it. The cap comes from {@code storage.attachment.max-count}, the single
 * configured value {@link AttachmentValidator} reads.
 */
public class TooManyAttachmentsException extends ApiException {

    /** Same photo input as the other attachment rejections. */
    private static final String ATTACHMENTS_FIELD = "attachments";

    private final int count;
    private final int maxCount;

    public TooManyAttachmentsException(int count, int maxCount) {
        super(
                HttpStatus.BAD_REQUEST.value(),
                "ATTACHMENT_TOO_MANY",
                "Received " + count + " attachments, over the limit of " + maxCount,
                List.of(new ApiError.FieldError(
                        ATTACHMENTS_FIELD, "At most " + maxCount + " files may be attached, but " + count + " were")));
        this.count = count;
        this.maxCount = maxCount;
    }

    public int getCount() {
        return count;
    }

    public int getMaxCount() {
        return maxCount;
    }
}
