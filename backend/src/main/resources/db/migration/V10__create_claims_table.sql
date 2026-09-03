-- V10: claim module's `claims` table and the claim-number sequence (Story
-- 10.2 - Claim Submission, Coverage Check and Claim Number). Adds no column
-- to an existing table, so this migration has nothing to backfill
-- (Architecture Spine AD-9 / M4-AD-13).
--
-- `claim_number_seq` mirrors `policy_number_seq` exactly (M4-AD-8): the one
-- allocator for the numeric part of a claim number, global, never reset per
-- year, gaps expected and acceptable - the "read the max and add one"
-- pattern is never used, same as V9's own policy-number sequence.
--
-- A claim REFERENCES its policy; it does not snapshot it (M4-AD-5) -
-- deliberately unlike a policy's relationship to the quote it came from
-- (V9, AD-4). The referent here is already immutable, so nothing underneath
-- a claim can drift, and there is no idempotency race to guard the way
-- `uq_policies_quote_id` guards policy issuance - multiple claims against
-- the same policy are allowed (D-9), so no uniqueness constraint on
-- `policy_id` exists. `policy_number` is copied only so a claim can be
-- listed and searched without a join.
--
-- `status` is a stored column (M4-AD-1), the one deliberate departure from
-- M3 AD-3: a claim's status records a human decision and cannot be derived
-- from persisted dates the way a policy's or a quote's can. This story
-- writes only SUBMITTED; the legal transition table (Story 11.1) is what
-- changes it after this, which is also why no `@Version` column exists yet
-- - Story 11.1 (V12) adds optimistic locking alongside the transition
-- history it protects.

CREATE SEQUENCE claim_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE claims (
    id             UUID PRIMARY KEY,
    customer_id    UUID NOT NULL REFERENCES users(id),
    policy_id      UUID NOT NULL REFERENCES policies(id),
    policy_number  VARCHAR(20) NOT NULL,
    claim_number   VARCHAR(20) NOT NULL,
    -- Coverage is validated on this date, not on "is the policy active
    -- now" (FR-M4-05); a future date is rejected before it ever reaches
    -- this column.
    incident_date  DATE NOT NULL,
    description    VARCHAR(2000) NOT NULL,
    location       VARCHAR(200) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    submitted_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_claims_claim_number UNIQUE (claim_number)
);

-- Backs the owner-scoped reads Story 10.4 adds (My Claims list/detail),
-- mirroring `idx_policies_customer_id` (AD-10).
CREATE INDEX idx_claims_customer_id ON claims (customer_id);

-- No caller this story needs it for, but the FK column is indexed now
-- rather than left for a retrofit once one does.
CREATE INDEX idx_claims_policy_id ON claims (policy_id);
