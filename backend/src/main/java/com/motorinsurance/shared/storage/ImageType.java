package com.motorinsurance.shared.storage;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The complete allowlist of image types a claim photo may be (Story 10.1,
 * Epic 10 release-blocking upload rules), each carrying the MIME string it
 * is stored and served as, plus the magic-byte signature(s) that identify
 * it. One place to read, and one place to change, "what is an acceptable
 * image here".
 *
 * <p>Membership is decided by {@link ImageContentSniffer} reading the bytes
 * themselves - never the filename extension and never the client-supplied
 * {@code Content-Type} header, so a PDF renamed {@code photo.jpg} is not a
 * member.
 *
 * <p>Constant names are deliberately single-segment ({@code JPEG}, not
 * {@code IMAGE_JPEG}) and the MIME strings are lowercase, because
 * {@code scripts/check-error-code-contract.mjs} scrapes every quoted
 * {@code MODULE_REASON}-shaped literal in any {@code .java} file as an error
 * code needing i18n entries in both catalogs - the quotes are what it keys
 * on, so a two-segment all-caps name written as a string literal anywhere in
 * this package would fail CI as an unmatched error code.
 */
public enum ImageType {

    /** {@code FF D8 FF} - the start of every JFIF/EXIF JPEG stream. */
    JPEG("image/jpeg", List.of(Signature.at(0, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF))),

    /** The 8-byte PNG signature from the PNG spec, high bit set in the first byte. */
    PNG(
            "image/png",
            List.of(Signature.at(
                    0,
                    (byte) 0x89,
                    (byte) 0x50,
                    (byte) 0x4E,
                    (byte) 0x47,
                    (byte) 0x0D,
                    (byte) 0x0A,
                    (byte) 0x1A,
                    (byte) 0x0A))),

    /**
     * A RIFF container whose form type is WebP - both markers are required.
     * {@code RIFF} alone is shared with AVI and WAV, so matching only the
     * first four bytes would let an {@code AVI } file through as an image.
     */
    WEBP("image/webp", List.of(Signature.ascii(0, "RIFF"), Signature.ascii(8, "WEBP")));

    private final String mimeType;
    private final List<Signature> signatures;

    ImageType(String mimeType, List<Signature> signatures) {
        this.mimeType = mimeType;
        this.signatures = signatures;
    }

    public String mimeType() {
        return mimeType;
    }

    /**
     * Whether {@code content} carries every signature this type requires.
     * Content shorter than a signature simply does not match - a truncated
     * or empty upload is "not a recognised image", not an exception.
     */
    boolean matches(byte[] content) {
        for (Signature signature : signatures) {
            if (!signature.matches(content)) {
                return false;
            }
        }
        return true;
    }

    /** One magic-byte marker: an offset into the file and the bytes expected there. */
    record Signature(int offset, byte[] bytes) {

        static Signature at(int offset, byte... bytes) {
            return new Signature(offset, bytes);
        }

        static Signature ascii(int offset, String marker) {
            return new Signature(offset, marker.getBytes(StandardCharsets.US_ASCII));
        }

        boolean matches(byte[] content) {
            if (content == null || content.length < offset + bytes.length) {
                return false;
            }
            for (int i = 0; i < bytes.length; i++) {
                if (content[offset + i] != bytes[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
