package com.motorinsurance.shared.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The gate every uploaded file passes before anything reaches {@link
 * Storage} (Story 10.1, Epic 10's release-blocking upload rules). Three
 * independent rules, each with its own error code:
 *
 * <ul>
 *   <li>the batch is no longer than {@code storage.attachment.max-count};
 *   <li>each file is no larger than
 *       {@code storage.attachment.max-file-size-bytes};
 *   <li>each file's <em>bytes</em> are one of {@link ImageType}'s members,
 *       as decided by {@link ImageContentSniffer} - never the filename
 *       extension, never a client-supplied {@code Content-Type}.
 * </ul>
 *
 * <p>Both caps are resolved from one configured value each via constructor
 * {@code @Value} injection and validated fail-fast at startup (the {@code
 * PolicyService} idiom), so no call site carries a literal and a
 * misconfigured cap fails the deployment rather than every upload.
 *
 * <p><b>The whole batch is validated before any file is stored.</b> Three
 * valid photos and one PDF means nothing is written at all - the caller gets
 * the list back only once every member passed, so Story 10.2's claim
 * transaction never has to unwind bytes it wrote for a submission that was
 * going to fail anyway.
 *
 * <p>Takes plain bytes rather than {@code MultipartFile} for the same reason
 * {@link Storage} does: no servlet type in {@code shared.storage}, so this
 * is unit-testable without a web context.
 */
@Component
public class AttachmentValidator {

    /** Stands in for a client that sent no filename at all, so a message still names something. */
    private static final String UNNAMED_FILE = "(unnamed file)";

    /**
     * Matches {@code attachments.display_filename VARCHAR(255)}
     * ({@code V11__create_attachments_table.sql}, Story 10.2) - the display
     * name is client-controlled, so it is capped and stripped of control
     * characters at this boundary before it can reach that column, an
     * exception message, or a field error (Story 10.1 review finding: it
     * was previously "interpolated unbounded and unsanitized").
     */
    private static final int MAX_DISPLAY_FILENAME_LENGTH = 255;

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private final long maxFileSizeBytes;
    private final int maxCount;

    public AttachmentValidator(
            @Value("${storage.attachment.max-file-size-bytes}") long maxFileSizeBytes,
            @Value("${storage.attachment.max-count}") int maxCount) {
        if (maxFileSizeBytes < 1) {
            // Fails startup rather than every upload: a non-positive cap
            // would reject every file with ATTACHMENT_TOO_LARGE and look
            // like a bug in the sniffer. Same posture as PolicyService's
            // coverage-months check and auth.config.DeploymentSecretsGuard.
            throw new IllegalArgumentException(
                    "storage.attachment.max-file-size-bytes must be at least 1, but was " + maxFileSizeBytes);
        }
        if (maxCount < 1) {
            throw new IllegalArgumentException("storage.attachment.max-count must be at least 1, but was " + maxCount);
        }
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxCount = maxCount;
    }

    /**
     * Validates a whole submission's files, in order, and returns them with
     * their sniffed types resolved.
     *
     * @param candidates the files as they arrived; {@code null} or empty is
     *     valid - a claim may be filed with no photos
     * @return one {@link ValidatedAttachment} per candidate, in the same
     *     order, ready to be handed to {@link Storage#store}
     * @throws TooManyAttachmentsException if the batch is over the count cap
     * @throws AttachmentTooLargeException if any file is over the size cap
     * @throws UnsupportedAttachmentTypeException if any file's bytes are not
     *     an allowed image
     */
    public List<ValidatedAttachment> validate(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        // Also re-checked by validateCount below when a caller uses it, but
        // kept here too: a caller that skips that pre-check must still get
        // the same answer.
        validateCount(candidates.size());
        List<ValidatedAttachment> validated = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            validated.add(validateOne(candidate));
        }
        return List.copyOf(validated);
    }

    /**
     * The count check alone, usable before any candidate's bytes are read
     * into memory (Story 10.2, closing a Story 10.1 review finding: the
     * count cap was previously enforced only after every part was already
     * materialized as {@code byte[]}). A multipart controller can call this
     * against the raw part count first, then only build {@link Candidate}s -
     * which requires reading every file's bytes - once it is known the
     * batch is not already over the cap.
     */
    public void validateCount(int count) {
        if (count > maxCount) {
            throw new TooManyAttachmentsException(count, maxCount);
        }
    }

    private ValidatedAttachment validateOne(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        byte[] content = Objects.requireNonNull(candidate.content(), "candidate content must not be null");
        String displayFilename = displayName(candidate.filename());
        if (content.length > maxFileSizeBytes) {
            // Size before type: it is the cheaper answer, and an oversized
            // file is over the cap whether or not it is also an image.
            throw new AttachmentTooLargeException(displayFilename, content.length, maxFileSizeBytes);
        }
        ImageType type = ImageContentSniffer.sniff(content)
                .orElseThrow(() -> new UnsupportedAttachmentTypeException(displayFilename));
        return new ValidatedAttachment(content, type, displayFilename);
    }

    private static String displayName(String filename) {
        if (filename == null || filename.isBlank()) {
            return UNNAMED_FILE;
        }
        String sanitized = CONTROL_CHARACTERS.matcher(filename).replaceAll("");
        return sanitized.length() > MAX_DISPLAY_FILENAME_LENGTH
                ? sanitized.substring(0, MAX_DISPLAY_FILENAME_LENGTH)
                : sanitized;
    }

    /**
     * One file as it arrived: its bytes and the name the client called it.
     * The name is display metadata only - see {@link StoredFile} - so it is
     * never parsed, never trusted for a type decision and never resolved
     * against a path.
     */
    public record Candidate(byte[] content, String filename) {
    }

    /**
     * A candidate that passed every rule, with the type its bytes actually
     * carry. {@code type.mimeType()} is what the caller passes to {@link
     * Storage#store} and what Story 10.2 persists - the declared
     * {@code Content-Type} of the request part is discarded entirely.
     */
    public record ValidatedAttachment(byte[] content, ImageType type, String displayFilename) {
    }
}
