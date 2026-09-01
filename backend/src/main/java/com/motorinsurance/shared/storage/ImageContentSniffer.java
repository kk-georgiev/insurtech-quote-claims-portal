package com.motorinsurance.shared.storage;

import java.util.Arrays;
import java.util.Optional;

/**
 * Decides what a file actually <em>is</em> by reading its leading bytes
 * (Story 10.1, Epic 10's first release-blocking upload rule). The filename
 * extension and the client-supplied {@code Content-Type} header are never
 * consulted - neither is an input here at all - so a PDF renamed
 * {@code photo.jpg} and a PNG declared as {@code image/gif} are both decided
 * the same way: by their bytes.
 *
 * <p>A pure function of its argument, like {@code policy.domain.PolicyNumber}
 * - no Spring context, no clock, no I/O - so every row of the story's
 * sniffing matrix is a plain unit test over a byte array.
 *
 * <p>Anything unrecognised, truncated or empty returns {@link
 * Optional#empty()} rather than throwing: "not an allowed image" is an
 * ordinary answer that the caller turns into a translated 400, never a 500
 * escaping from a malformed upload.
 *
 * <p>Hand-rolled rather than delegated to a library on purpose: Apache Tika
 * is neither on the classpath nor in the Spring Boot 4.1.1 BOM, and the
 * JDK's {@code URLConnection.guessContentTypeFromStream} does not recognise
 * WebP, which this allowlist requires. Three signatures is a dozen lines and
 * the story's acceptance criteria demand direct unit tests for them anyway.
 */
public final class ImageContentSniffer {

    /**
     * Bytes needed to decide every {@link ImageType}: WebP's second marker
     * ends at offset 12, the longest prefix any signature inspects.
     */
    private static final int SIGNATURE_PREFIX_LENGTH = 12;

    private ImageContentSniffer() {
    }

    /**
     * The allowed type {@code content} carries, or empty if it carries none.
     *
     * @param content the file's bytes; may be shorter than any signature,
     *     empty or {@code null} - all three are simply "unrecognised"
     */
    public static Optional<ImageType> sniff(byte[] content) {
        if (content == null || content.length == 0) {
            return Optional.empty();
        }
        // Only the prefix is examined, so a large upload is never scanned in
        // full and a caller cannot mutate our view of it mid-decision.
        byte[] prefix = Arrays.copyOf(content, Math.min(content.length, SIGNATURE_PREFIX_LENGTH));
        for (ImageType type : ImageType.values()) {
            if (type.matches(prefix)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
