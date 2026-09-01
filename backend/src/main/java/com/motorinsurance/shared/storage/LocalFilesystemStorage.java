package com.motorinsurance.shared.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The only {@link Storage} implementation (Story 10.1): a directory on the
 * machine running the backend, chosen over MinIO deliberately (Epic 10
 * technical decisions) with the port in front of it so an S3-compatible
 * backend stays a substitution rather than a rewrite. In Compose that
 * directory is a named volume, so stored bytes survive a container restart.
 *
 * <p>Three properties this adapter is responsible for:
 *
 * <ul>
 *   <li><b>The key is ours.</b> A random {@link UUID} per stored file. The
 *       client-supplied filename never reaches a path, so traversal
 *       sequences and executable extensions are unrepresentable rather than
 *       filtered out. The key carries no extension at all: nothing here is
 *       ever executed or statically served, and the content type travels in
 *       the persisted metadata instead.
 *   <li><b>The path is checked anyway.</b> Every resolved path is verified
 *       to stay strictly under the configured base directory before any read
 *       or write - defence in depth, since a key that failed this check
 *       could only come from a bug or a corrupted persisted row, never from
 *       a request. Hence {@link IllegalStateException}, not a 400.
 *   <li><b>The write is atomic.</b> Bytes land in a temporary file in the
 *       same directory and are moved into place, so a crash mid-write leaves
 *       no half-written file readable under a valid key.
 * </ul>
 *
 * <p>The base directory is created on demand at the first store rather than
 * at startup, so a deployment with no pre-existing host path still starts
 * healthy.
 */
@Component
public class LocalFilesystemStorage implements Storage {

    /** Not a cryptographic commitment - an integrity check on the bytes read back. */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private static final String TEMP_FILE_SUFFIX = ".part";

    private final Path baseDir;

    public LocalFilesystemStorage(@Value("${storage.local.base-dir}") String baseDir) {
        if (baseDir == null || baseDir.isBlank()) {
            // Fails startup rather than the first upload, the same fail-fast
            // posture as PolicyService's coverage-months check: a blank base
            // directory would otherwise resolve to the process working
            // directory and scatter uploads through the deployment.
            throw new IllegalArgumentException("storage.local.base-dir must not be blank, but was \"" + baseDir + "\"");
        }
        try {
            this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException(
                    "storage.local.base-dir is not a usable path, but was \"" + baseDir + "\"", e);
        }
    }

    /** The absolute, normalized directory every stored file lives directly under. */
    public Path baseDirectory() {
        return baseDir;
    }

    @Override
    public StoredFile store(byte[] content, String contentType, String displayFilename) {
        Objects.requireNonNull(content, "content must not be null");
        String storageKey = UUID.randomUUID().toString();
        Path target = resolveWithinBase(storageKey);
        try {
            Files.createDirectories(baseDir);
            Path temporary = Files.createTempFile(baseDir, storageKey, TEMP_FILE_SUFFIX);
            try {
                Files.write(temporary, content);
                if (Files.exists(target)) {
                    // Cannot happen with a random UUID short of a bug in key
                    // generation; still never silently overwrite bytes some
                    // persisted row already points at.
                    throw new IllegalStateException("A file is already stored under generated key " + storageKey);
                }
                moveIntoPlace(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store a file under key " + storageKey, e);
        }
        return new StoredFile(storageKey, contentType, content.length, sha256Hex(content), displayFilename);
    }

    @Override
    public byte[] read(String storageKey) {
        Path source = resolveWithinBase(storageKey);
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the file stored under key " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveWithinBase(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete the file stored under key " + storageKey, e);
        }
    }

    /**
     * The path {@code storageKey} names, proven to sit strictly inside the
     * base directory. Equality with the base directory itself is rejected
     * too, so a key of {@code "."} cannot address the directory.
     */
    private Path resolveWithinBase(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey must not be blank");
        }
        Path resolved;
        try {
            resolved = baseDir.resolve(storageKey).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalStateException("Storage key is not a usable path segment: " + storageKey, e);
        }
        if (resolved.equals(baseDir) || !resolved.startsWith(baseDir)) {
            throw new IllegalStateException(
                    "Storage key resolves outside the configured base directory: " + storageKey);
        }
        return resolved;
    }

    private void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some filesystems (notably a few container-mounted ones) refuse
            // an atomic rename. A plain move within the same directory is
            // still the closest available approximation.
            Files.move(temporary, target);
        }
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM this project runs on is required to provide SHA-256.
            throw new IllegalStateException("This JVM does not provide " + DIGEST_ALGORITHM, e);
        }
    }
}
