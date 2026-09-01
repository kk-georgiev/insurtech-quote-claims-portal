package com.motorinsurance.shared.storage;

/**
 * The port every module uses to put bytes somewhere durable and get them
 * back (Story 10.1, Epic 10 technical decisions). {@code claim} depends on
 * this interface and never on {@link LocalFilesystemStorage}, so replacing
 * the local filesystem with an S3-compatible backend stays a substitution
 * rather than a rewrite.
 *
 * <p>Deliberately no {@code MultipartFile} anywhere in its signature: the
 * servlet type would tie {@code shared.storage} to a web request, make the
 * adapter untestable without a servlet context, and exclude Milestone 5's
 * generated policy PDF - output this port produces rather than receives -
 * from reusing it.
 *
 * <p>This port owns the byte-level concern only: what the file is called
 * where it lands, and how to get it back. <em>Who may read it</em> is the
 * claim's rule and lives in {@code claim} (Story 10.4); a storage key is an
 * identifier, not a capability.
 */
public interface Storage {

    /**
     * Writes {@code content} under a freshly generated storage key.
     *
     * <p>The key is produced by the implementation, never derived from
     * {@code displayFilename} and never supplied by a caller, so no
     * client-controlled string reaches a filesystem path. Two calls with
     * identical bytes get two different keys - the key identifies a stored
     * file, it does not fingerprint content.
     *
     * @param content the exact bytes to write; the caller has already
     *     validated them (see {@code AttachmentValidator} for uploads)
     * @param contentType the type the caller vouches for - for an upload the
     *     sniffed {@link ImageType#mimeType()}, never a client-declared header
     * @param displayFilename the client-supplied name, kept as metadata only
     * @return the storage key, echoed metadata, byte length and SHA-256 of
     *     what was written
     */
    StoredFile store(byte[] content, String contentType, String displayFilename);

    /**
     * The bytes previously stored under {@code storageKey}, byte-identical
     * to what {@link #store} was given.
     *
     * @throws java.io.UncheckedIOException if nothing is stored under that
     *     key or it cannot be read - unreachable through a client-facing
     *     path, where serving a file always requires a persisted row that
     *     was written only after a successful store
     */
    byte[] read(String storageKey);

    /**
     * Removes whatever is stored under {@code storageKey}, doing nothing if
     * it is already gone.
     *
     * <p>Used for best-effort cleanup after a failed claim transaction
     * (Story 10.2): correctness never depends on it, because serving a file
     * requires an attachments row, so bytes left behind are unreachable and
     * inert.
     */
    void delete(String storageKey);
}
