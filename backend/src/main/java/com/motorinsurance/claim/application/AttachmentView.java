package com.motorinsurance.claim.application;

import java.time.Instant;
import java.util.UUID;

/**
 * One attachment as everything outside {@code claim} sees it (Story 10.2).
 * Deliberately carries no {@code storageKey} - a storage key is not a
 * capability, but it is still never the client's business, and Story 10.4's
 * download endpoint is reached by this record's own {@code id}, not by one.
 */
public record AttachmentView(UUID id, String displayFilename, String contentType, long sizeBytes, Instant uploadedAt) {
}
