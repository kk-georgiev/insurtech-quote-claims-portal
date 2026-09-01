package com.motorinsurance.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motorinsurance.shared.storage.AttachmentValidator.Candidate;
import com.motorinsurance.shared.storage.AttachmentValidator.ValidatedAttachment;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The size cap, the count cap and the allowlist, each proved separately
 * (Story 10.1) - plus the fail-fast startup validation that keeps a
 * misconfigured cap from becoming a per-upload mystery. No Spring context:
 * the two {@code @Value}-injected caps are just constructor arguments here.
 */
class AttachmentValidatorTest {

    private static final long MAX_FILE_SIZE_BYTES = 1_024;
    private static final int MAX_COUNT = 3;

    private final AttachmentValidator validator = new AttachmentValidator(MAX_FILE_SIZE_BYTES, MAX_COUNT);

    @Test
    void validate_allowedImages_returnsThemInOrderWithTheirSniffedTypes() {
        List<ValidatedAttachment> validated = validator.validate(
                List.of(candidate("front.jpg", jpeg(10)), candidate("side.png", png(10)), candidate("rear", webp(10))));

        assertThat(validated).extracting(ValidatedAttachment::type).containsExactly(
                ImageType.JPEG, ImageType.PNG, ImageType.WEBP);
        assertThat(validated)
                .extracting(ValidatedAttachment::displayFilename)
                .containsExactly("front.jpg", "side.png", "rear");
    }

    @Test
    void validate_typeComesFromTheBytesNotTheExtension() {
        List<ValidatedAttachment> validated = validator.validate(List.of(candidate("photo.jpg", png(10))));

        assertThat(validated).singleElement().extracting(ValidatedAttachment::type).isEqualTo(ImageType.PNG);
    }

    @Test
    void validate_noAttachments_isAllowed() {
        // A claim may be filed with no photos at all.
        assertThat(validator.validate(List.of())).isEmpty();
        assertThat(validator.validate(null)).isEmpty();
    }

    @Test
    void validate_fileExactlyAtTheSizeCap_isAccepted() {
        // The cap is inclusive: "at most this many bytes".
        List<ValidatedAttachment> validated =
                validator.validate(List.of(candidate("big.jpg", jpeg((int) MAX_FILE_SIZE_BYTES))));

        assertThat(validated).singleElement().extracting(v -> v.content().length).isEqualTo((int) MAX_FILE_SIZE_BYTES);
    }

    @Test
    void validate_fileOverTheSizeCap_isRejectedWithItsNameAndTheCap() {
        byte[] oversized = jpeg((int) MAX_FILE_SIZE_BYTES + 1);

        assertThatExceptionOfType(AttachmentTooLargeException.class)
                .isThrownBy(() -> validator.validate(List.of(candidate("huge.jpg", oversized))))
                .satisfies(e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("ATTACHMENT_TOO_LARGE");
                    assertThat(e.getDisplayFilename()).isEqualTo("huge.jpg");
                    assertThat(e.getSizeBytes()).isEqualTo(MAX_FILE_SIZE_BYTES + 1);
                    assertThat(e.getMaxFileSizeBytes()).isEqualTo(MAX_FILE_SIZE_BYTES);
                    assertThat(e.getFieldErrors()).isNotEmpty();
                });
    }

    @Test
    void validate_batchExactlyAtTheCountCap_isAccepted() {
        List<Candidate> batch =
                List.of(candidate("a.jpg", jpeg(4)), candidate("b.jpg", jpeg(4)), candidate("c.jpg", jpeg(4)));

        assertThat(validator.validate(batch)).hasSize(MAX_COUNT);
    }

    @Test
    void validate_batchOverTheCountCap_rejectsTheWholeBatch() {
        List<Candidate> batch = List.of(
                candidate("a.jpg", jpeg(4)),
                candidate("b.jpg", jpeg(4)),
                candidate("c.jpg", jpeg(4)),
                candidate("d.jpg", jpeg(4)));

        assertThatExceptionOfType(TooManyAttachmentsException.class)
                .isThrownBy(() -> validator.validate(batch))
                .satisfies(e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("ATTACHMENT_TOO_MANY");
                    assertThat(e.getCount()).isEqualTo(4);
                    assertThat(e.getMaxCount()).isEqualTo(MAX_COUNT);
                });
    }

    @Test
    void validate_countCapIsCheckedBeforeAnythingElse() {
        // Every file in this batch is also invalid; the count is still the
        // answer, because it is a property of the submission as a whole.
        List<Candidate> batch = List.of(
                candidate("a.pdf", pdf()), candidate("b.pdf", pdf()), candidate("c.pdf", pdf()), candidate("d.pdf", pdf()));

        assertThatThrownBy(() -> validator.validate(batch)).isInstanceOf(TooManyAttachmentsException.class);
    }

    @Test
    void validate_oneBadFileInAnOtherwiseValidBatch_rejectsTheWholeBatch() {
        // Nothing is returned, so a caller that stores only what it is given
        // back writes nothing at all - the story's "three valid, one PDF" row.
        List<Candidate> batch =
                List.of(candidate("a.jpg", jpeg(8)), candidate("b.png", png(8)), candidate("evil.jpg", pdf()));

        assertThatExceptionOfType(UnsupportedAttachmentTypeException.class)
                .isThrownBy(() -> validator.validate(batch))
                .satisfies(e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("ATTACHMENT_UNSUPPORTED_TYPE");
                    assertThat(e.getDisplayFilename()).isEqualTo("evil.jpg");
                });
    }

    @Test
    void validate_emptyFile_isRejectedAsAnUnsupportedType() {
        assertThatThrownBy(() -> validator.validate(List.of(candidate("empty.jpg", new byte[0]))))
                .isInstanceOf(UnsupportedAttachmentTypeException.class);
    }

    @Test
    void validate_fileWithNoName_isStillNamedInTheRejection() {
        assertThatThrownBy(() -> validator.validate(List.of(candidate(null, pdf()))))
                .isInstanceOf(UnsupportedAttachmentTypeException.class)
                .hasMessageContaining("unnamed");
    }

    @Test
    void construction_nonPositiveSizeCap_failsStartupNamingThePropertyAndTheValue() {
        assertThatThrownBy(() -> new AttachmentValidator(0, MAX_COUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage.attachment.max-file-size-bytes")
                .hasMessageContaining("0");

        assertThatThrownBy(() -> new AttachmentValidator(-1, MAX_COUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void construction_nonPositiveCountCap_failsStartupNamingThePropertyAndTheValue() {
        assertThatThrownBy(() -> new AttachmentValidator(MAX_FILE_SIZE_BYTES, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage.attachment.max-count")
                .hasMessageContaining("0");
    }

    @Test
    void construction_positiveCaps_startsFine() {
        assertThatCode(() -> new AttachmentValidator(1, 1)).doesNotThrowAnyException();
    }

    private static Candidate candidate(String filename, byte[] content) {
        return new Candidate(content, filename);
    }

    private static byte[] jpeg(int totalLength) {
        return withHeader(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, totalLength);
    }

    private static byte[] png(int totalLength) {
        return withHeader(
                new byte[] {
                    (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                    (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
                },
                totalLength);
    }

    private static byte[] webp(int totalLength) {
        byte[] file = new byte[Math.max(totalLength, 12)];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, file, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, file, 8, 4);
        return file;
    }

    private static byte[] pdf() {
        return "%PDF-1.7 definitely not an image".getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] withHeader(byte[] header, int totalLength) {
        byte[] file = new byte[Math.max(totalLength, header.length)];
        System.arraycopy(header, 0, file, 0, header.length);
        return file;
    }
}
