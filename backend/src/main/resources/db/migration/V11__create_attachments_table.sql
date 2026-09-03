-- V11: claim module's `attachments` table (Story 10.2 - Claim Submission,
-- Coverage Check and Claim Number; M4-AD-2, M4-AD-4). New table, nothing to
-- backfill.
--
-- Bytes live on the storage volume behind `shared.storage.Storage` (Story
-- 10.1); this table holds only the metadata `StoredFile` already carries:
-- storage key, content type, size, hash and upload time (FR-M4-03). The
-- volume is never statically served by any handler (M4-AD-4) - a storage
-- key alone grants nothing without a matching row here plus the claim's own
-- permission check (Story 10.4).
--
-- `claim_id` is a real foreign key with ON DELETE CASCADE: an attachment
-- has no life outside the claim it belongs to (M4-AD-2). No claim-delete
-- path exists yet anywhere in this application, so this is defensive
-- schema design rather than a behavior exercised today.
--
-- `display_filename` is client-controlled display metadata only (never a
-- filesystem path); the application layer caps it to this column's width
-- and strips control characters before it ever reaches an insert
-- (`shared.storage.AttachmentValidator`).

CREATE TABLE attachments (
    id                UUID PRIMARY KEY,
    claim_id          UUID NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    storage_key       VARCHAR(64) NOT NULL,
    content_type      VARCHAR(20) NOT NULL,
    size_bytes        BIGINT NOT NULL,
    sha256_hex        VARCHAR(64) NOT NULL,
    display_filename  VARCHAR(255) NOT NULL,
    uploaded_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_attachments_storage_key UNIQUE (storage_key)
);

-- Backs Story 10.4's claim-detail read (attachment metadata for one claim).
CREATE INDEX idx_attachments_claim_id ON attachments (claim_id);
