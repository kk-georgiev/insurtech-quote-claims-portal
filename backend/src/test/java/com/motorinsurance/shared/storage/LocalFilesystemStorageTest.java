package com.motorinsurance.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The adapter's four promises (Story 10.1): bytes come back exactly as they
 * went in, keys are identifiers rather than content fingerprints, the base
 * directory appears on demand, and no key can address anything outside it.
 * A real directory via JUnit's {@code @TempDir} - no Spring context and no
 * servlet, which is the point of keeping {@code MultipartFile} out of the
 * port.
 */
class LocalFilesystemStorageTest {

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5};

    /** SHA-256 of the empty input - the value any correct implementation produces for no bytes. */
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @TempDir
    Path tempDir;

    @Test
    void store_thenRead_returnsByteIdenticalContent() {
        LocalFilesystemStorage storage = storageIn(tempDir);

        StoredFile stored = storage.store(JPEG_BYTES, ImageType.JPEG.mimeType(), "front.jpg");

        assertThat(storage.read(stored.storageKey())).isEqualTo(JPEG_BYTES);
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(stored.sizeBytes()).isEqualTo(JPEG_BYTES.length);
        assertThat(stored.displayFilename()).isEqualTo("front.jpg");
    }

    @Test
    void store_computesTheSha256OfTheBytesItWrote() {
        LocalFilesystemStorage storage = storageIn(tempDir);

        // Pinned against a known-answer digest rather than one this code
        // computes a second time, which would prove only self-consistency.
        assertThat(storage.store(new byte[0], "application/octet-stream", "empty.bin").sha256Hex())
                .isEqualTo(EMPTY_SHA256);
    }

    @Test
    void store_sameBytesTwice_producesTwoDifferentKeys() {
        // The key identifies a stored file; it does not fingerprint content.
        // Two clients uploading the same photo must not collide.
        LocalFilesystemStorage storage = storageIn(tempDir);

        StoredFile first = storage.store(JPEG_BYTES, ImageType.JPEG.mimeType(), "same.jpg");
        StoredFile second = storage.store(JPEG_BYTES, ImageType.JPEG.mimeType(), "same.jpg");

        assertThat(first.storageKey()).isNotEqualTo(second.storageKey());
        assertThat(first.sha256Hex()).isEqualTo(second.sha256Hex());
        assertThat(storage.read(first.storageKey())).isEqualTo(storage.read(second.storageKey()));
    }

    @Test
    void store_manyTimes_neverRepeatsAKey() {
        LocalFilesystemStorage storage = storageIn(tempDir);
        Set<String> keys = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            keys.add(storage.store(JPEG_BYTES, ImageType.JPEG.mimeType(), "photo.jpg").storageKey());
        }

        assertThat(keys).hasSize(200);
    }

    @Test
    void store_keyNeverCarriesTheClientSuppliedFilename() {
        // The filename is display metadata only: nothing a client typed may
        // appear in a path, which is what makes traversal sequences and
        // executable extensions unrepresentable rather than filtered.
        LocalFilesystemStorage storage = storageIn(tempDir);

        StoredFile stored = storage.store(JPEG_BYTES, ImageType.JPEG.mimeType(), "../../etc/passwd.jpg");

        assertThat(stored.storageKey()).doesNotContain("passwd").doesNotContain("..").doesNotContain("/");
        assertThat(stored.displayFilename()).isEqualTo("../../etc/passwd.jpg");
        assertThat(filesDirectlyUnder(tempDir)).containsExactly(stored.storageKey());
    }

    @Test
    void store_baseDirectoryDoesNotExistYet_createsItOnDemand() {
        // A clean deployment has no pre-created host path; the container
        // must still come up healthy and accept its first upload.
        Path absent = tempDir.resolve("not").resolve("created").resolve("yet");
        assertThat(absent).doesNotExist();

        StoredFile stored = storageIn(absent).store(JPEG_BYTES, ImageType.JPEG.mimeType(), "first.jpg");

        assertThat(absent).isDirectory();
        assertThat(absent.resolve(stored.storageKey())).exists();
    }

    @Test
    void store_leavesNoTemporaryFileBehind() {
        LocalFilesystemStorage storage = storageIn(tempDir);

        StoredFile stored = storage.store(JPEG_BYTES, ImageType.JPEG.mimeType(), "front.jpg");

        assertThat(filesDirectlyUnder(tempDir)).containsExactly(stored.storageKey());
    }

    @Test
    void delete_removesTheBytesAndIsSafeToRepeat() {
        LocalFilesystemStorage storage = storageIn(tempDir);
        StoredFile stored = storage.store(JPEG_BYTES, ImageType.JPEG.mimeType(), "front.jpg");

        storage.delete(stored.storageKey());

        assertThat(tempDir.resolve(stored.storageKey())).doesNotExist();
        // Best-effort cleanup after a failed claim transaction may run twice
        // or against bytes that were never written (Story 10.2).
        storage.delete(stored.storageKey());
    }

    @Test
    void read_unknownKey_failsRatherThanReturningNothing() {
        LocalFilesystemStorage storage = storageIn(tempDir);

        assertThatThrownBy(() -> storage.read("11111111-2222-3333-4444-555555555555"))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void anyKeyEscapingTheBaseDirectory_isRefusedBeforeTouchingTheFilesystem() throws Exception {
        Path base = tempDir.resolve("attachments");
        LocalFilesystemStorage storage = storageIn(base);
        Path outside = tempDir.resolve("secret.txt");
        Files.writeString(outside, "not yours");

        for (String traversal : List.of(
                "../secret.txt",
                "../../secret.txt",
                "nested/../../secret.txt",
                outside.toAbsolutePath().toString(),
                ".")) {
            assertThatThrownBy(() -> storage.read(traversal))
                    .as("read %s", traversal)
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> storage.delete(traversal))
                    .as("delete %s", traversal)
                    .isInstanceOf(IllegalStateException.class);
        }

        assertThat(outside).exists();
        assertThat(Files.readString(outside)).isEqualTo("not yours");
    }

    @Test
    void aBlankKey_isRefused() {
        LocalFilesystemStorage storage = storageIn(tempDir);

        assertThatThrownBy(() -> storage.read("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.read(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construction_blankBaseDirectory_failsFastNamingTheProperty() {
        assertThatThrownBy(() -> new LocalFilesystemStorage("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage.local.base-dir");
        assertThatThrownBy(() -> new LocalFilesystemStorage(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construction_relativeBaseDirectory_isResolvedToAnAbsoluteNormalizedPath() {
        LocalFilesystemStorage storage = new LocalFilesystemStorage("./data/./attachments");

        // Resolved once at construction, so a later change of working
        // directory cannot move where uploads land.
        assertThat(storage.baseDirectory()).isAbsolute();
        assertThat(storage.baseDirectory().toString()).doesNotContain("./").endsWith("attachments");
    }

    private static LocalFilesystemStorage storageIn(Path baseDir) {
        return new LocalFilesystemStorage(baseDir.toString());
    }

    private static List<String> filesDirectlyUnder(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(Path::getFileName).map(Path::toString).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
