package com.motorinsurance.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Pins Epic 10's first release-blocking upload rule (Story 10.1): what a
 * file <em>is</em> comes from its bytes, and nothing else. A pure function,
 * so no Spring context, no servlet and no filesystem is involved - the same
 * shape as {@code policy.domain.PolicyNumberTest}.
 *
 * <p>Every row of the story's sniffing matrix has a case here, including the
 * renamed PDF, the truncated header, the empty file and the RIFF container
 * that is not WebP.
 */
class ImageContentSnifferTest {

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG_HEADER = {
        (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
    };

    @Test
    void sniff_jpegBytes_isJpeg() {
        assertThat(ImageContentSniffer.sniff(withTrailingPayload(JPEG_HEADER))).contains(ImageType.JPEG);
    }

    @Test
    void sniff_pngBytes_isPng() {
        assertThat(ImageContentSniffer.sniff(withTrailingPayload(PNG_HEADER))).contains(ImageType.PNG);
    }

    @Test
    void sniff_webpBytes_isWebp() {
        assertThat(ImageContentSniffer.sniff(riffContainer("WEBP"))).contains(ImageType.WEBP);
    }

    @Test
    void sniff_pdfRenamedAsAJpeg_isRejected() {
        // The whole point of sniffing: the filename is not even an input
        // here, so "photo.jpg" cannot make these bytes an image.
        byte[] pdf = "%PDF-1.7\nnot an image at all".getBytes(StandardCharsets.US_ASCII);

        assertThat(ImageContentSniffer.sniff(pdf)).isEmpty();
    }

    @Test
    void sniff_pngBytesRegardlessOfWhatTheCallerDeclares_isPng() {
        // A caller declaring image/gif changes nothing: the declared type is
        // not a parameter of this decision at all, which is what makes a
        // lying Content-Type header inert.
        assertThat(ImageContentSniffer.sniff(withTrailingPayload(PNG_HEADER))).contains(ImageType.PNG);
        assertThat(ImageType.PNG.mimeType()).isEqualTo("image/png");
    }

    @Test
    void sniff_truncatedHeader_isRejectedRatherThanThrowing() {
        // Three bytes: shorter than the PNG signature and shorter than the
        // WebP prefix. Must be an ordinary "no" - anything thrown here would
        // surface as a 500 for a malformed upload.
        byte[] truncated = {(byte) 0x89, (byte) 0x50, (byte) 0x4E};

        assertThat(ImageContentSniffer.sniff(truncated)).isEmpty();
    }

    @Test
    void sniff_aJpegSignatureOneByteShort_isRejected() {
        byte[] almost = {(byte) 0xFF, (byte) 0xD8};

        assertThat(ImageContentSniffer.sniff(almost)).isEmpty();
    }

    @Test
    void sniff_emptyFile_isRejected() {
        assertThat(ImageContentSniffer.sniff(new byte[0])).isEmpty();
    }

    @Test
    void sniff_nullContent_isRejected() {
        assertThat(ImageContentSniffer.sniff(null)).isEmpty();
    }

    @Test
    void sniff_riffContainerThatIsNotWebp_isRejected() {
        // RIFF alone is shared with AVI and WAV. Both markers are required,
        // so matching only the first four bytes would let this through.
        assertThat(ImageContentSniffer.sniff(riffContainer("AVI "))).isEmpty();
    }

    @Test
    void sniff_riffHeaderTruncatedBeforeTheFormMarker_isRejected() {
        byte[] justRiff = "RIFF".getBytes(StandardCharsets.US_ASCII);

        assertThat(ImageContentSniffer.sniff(justRiff)).isEmpty();
    }

    @Test
    void sniff_gifBytes_isRejectedBecauseGifIsNotOnTheAllowlist() {
        byte[] gif = "GIF89a....".getBytes(StandardCharsets.US_ASCII);

        assertThat(ImageContentSniffer.sniff(gif)).isEmpty();
    }

    @Test
    void sniff_everyAllowedTypeHasALowercaseImageMimeType() {
        for (ImageType type : ImageType.values()) {
            assertThat(type.mimeType()).isEqualTo(type.mimeType().toLowerCase()).startsWith("image/");
        }
    }

    @Test
    void sniff_returnsAnEmptyOptionalRatherThanNull() {
        Optional<ImageType> result = ImageContentSniffer.sniff(new byte[] {1, 2, 3});

        assertThat(result).isNotNull().isEmpty();
    }

    /** The signature followed by arbitrary body bytes, as a real file would be. */
    private static byte[] withTrailingPayload(byte[] header) {
        byte[] file = new byte[header.length + 64];
        System.arraycopy(header, 0, file, 0, header.length);
        return file;
    }

    /** A 12-byte RIFF header: {@code RIFF}, a length, then the form marker at offset 8. */
    private static byte[] riffContainer(String formMarker) {
        byte[] file = new byte[32];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, file, 0, 4);
        System.arraycopy(formMarker.getBytes(StandardCharsets.US_ASCII), 0, file, 8, 4);
        return file;
    }
}
