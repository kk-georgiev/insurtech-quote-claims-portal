package com.motorinsurance.claim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code attachments} (mirrors {@code
 * V11__create_attachments_table.sql} exactly). Holds only the metadata
 * {@code shared.storage.StoredFile} already carries plus the {@code claimId}
 * it belongs to (FR-M4-03, BA §14) - never the bytes, which live on the
 * storage volume behind {@code shared.storage.Storage}.
 *
 * <p>{@code claimId} is a plain {@link UUID} rather than a JPA {@code
 * @ManyToOne}, matching this codebase's existing convention (see {@code
 * Policy.quoteId}) of not introducing entity associations - the {@code
 * claim_id} foreign key and {@code ON DELETE CASCADE} at the database level
 * are what actually enforce "an attachment has no life outside its claim"
 * (M4-AD-2), not object-graph navigation.
 */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256_hex", nullable = false)
    private String sha256Hex;

    @Column(name = "display_filename", nullable = false)
    private String displayFilename;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    /** JPA-only. */
    protected Attachment() {
    }

    public Attachment(
            UUID claimId,
            String storageKey,
            String contentType,
            long sizeBytes,
            String sha256Hex,
            String displayFilename,
            Instant uploadedAt) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256Hex = sha256Hex;
        this.displayFilename = displayFilename;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256Hex() {
        return sha256Hex;
    }

    public String getDisplayFilename() {
        return displayFilename;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
