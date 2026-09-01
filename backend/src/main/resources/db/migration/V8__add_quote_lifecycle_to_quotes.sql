-- V8: quote offer-validity and acceptance state (Story 6.2 - Offer
-- Validity and Quote Status). No `status` column exists (Architecture
-- Spine AD-3, M3) - a quote's status is derived on read from these two
-- facts: `accepted_at` (set) means ACCEPTED, `valid_until` in the past
-- means EXPIRED, otherwise CALCULATED. See quote.domain.Quote#status.
--
-- `valid_until` is NOT NULL with its backfill in the same migration
-- (Architecture Spine AD-9): existing quotes are backfilled to 14 days
-- past their own `created_at` date - the same offer-validity period this
-- story applies going forward (quote.offer-validity-days in
-- application.yml), just anchored to when each quote was actually
-- calculated rather than to "today". `accepted_at` is the one nullable
-- column this migration adds - null carries real domain meaning ("not
-- accepted") and no story before 8.1 (Accept a Quote and Issue a Policy)
-- ever sets it, so every quote through this milestone's Stories 6.1-6.3
-- stays NULL here by construction.

ALTER TABLE quotes
    ADD COLUMN valid_until  DATE,
    ADD COLUMN accepted_at  TIMESTAMPTZ;

-- date + integer = date in Postgres (unlike date + interval, which widens
-- to timestamp) - no implicit-cast reliance needed for the DATE column.
UPDATE quotes
SET valid_until = (created_at AT TIME ZONE 'UTC')::date + 14
WHERE valid_until IS NULL;

ALTER TABLE quotes
    ALTER COLUMN valid_until SET NOT NULL;
