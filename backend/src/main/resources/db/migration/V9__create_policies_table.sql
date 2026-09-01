-- V9: policy module's `policies` table and the policy-number sequence
-- (Story 8.1 - Accept a Quote and Issue a Policy). Adds no column to an
-- existing table, so this migration has nothing to backfill (Architecture
-- Spine AD-9); the `quotes` columns acceptance writes to (`accepted_at`)
-- already exist from V8.
--
-- `policy_number_seq` is the one allocator for the numeric part of a policy
-- number (AD-7): the "read the max and add one" pattern the business
-- analysis rules out (BA 7.4) is never used. The sequence is global and
-- never resets per year - the year is already in the formatted string, so a
-- per-year reset would need coordination and buy nothing. Gaps are expected
-- and acceptable: nextval is non-transactional, so a rolled-back acceptance
-- consumes a value.
--
-- A policy COPIES; it never references (AD-4). Every rating input and every
-- breakdown component the quote carried is stored flat here, with the same
-- column types V4/V7 use on `quotes`, so an issued contract keeps showing
-- exactly what was agreed even if the tariff tables - or the quote itself -
-- change later. `quote_id` deliberately carries NO foreign key: it exists
-- solely as the idempotency key and is never dereferenced, and a policy row
-- must stay readable and complete with the `quotes` table empty.
-- `bonus_malus_code` likewise has no FK into `bonus_malus_class` (unlike
-- `quotes`, see V7): the code is copied from an already-validated quote, so
-- a write-time check adds nothing and would couple an immutable contract to
-- mutable reference data.
--
-- `uq_policies_policy_number` is a backstop, not a path the application
-- recovers from: with the sequence as the only allocator, it can only fire
-- if the sequence is behind the table's contents (a restored dump whose
-- sequence was not advanced, or a hand-inserted row). That is an operator
-- error to fix by resetting the sequence - retrying around it would paper
-- over a database that can no longer allocate numbers, so acceptance fails
-- loudly instead.
--
-- `uq_policies_quote_id` is the sole authority that exactly one policy
-- exists per quote (AD-5). The application-level pre-check in
-- quote.application is an optimization for the uncontended path, never the
-- guarantee - under a genuine race this constraint is what stops the second
-- insert.

CREATE SEQUENCE policy_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE policies (
    id                   UUID PRIMARY KEY,
    customer_id          UUID NOT NULL REFERENCES users(id),
    quote_id             UUID NOT NULL,
    policy_number        VARCHAR(20) NOT NULL,
    holder_name          VARCHAR(120) NOT NULL,
    -- Exactly one vehicle identity, enforced below: a registered vehicle
    -- has a plate, an unregistered one is identified by its VIN (FR-M3-08).
    -- Null carries real domain meaning here, which is what makes these the
    -- permitted nullable columns under AD-9.
    vehicle_registration VARCHAR(16),
    vehicle_vin          VARCHAR(17),
    coverage_start       DATE NOT NULL,
    coverage_end         DATE NOT NULL,
    issued_at            TIMESTAMPTZ NOT NULL,
    -- Snapshot of the rating inputs and the full breakdown, copied from the
    -- accepted quote (AD-4). No `status` column: a policy's status is
    -- derived from the coverage dates on read (AD-3).
    driver_age           INTEGER NOT NULL,
    region_code          VARCHAR(5) NOT NULL,
    engine_cc            INTEGER NOT NULL,
    zone_id              SMALLINT NOT NULL,
    zone_name            VARCHAR(20) NOT NULL,
    base_premium         NUMERIC(10, 2) NOT NULL,
    age_surcharge        NUMERIC(6, 2) NOT NULL,
    bonus_malus_code     VARCHAR(20) NOT NULL,
    bonus_malus_factor   NUMERIC(4, 3) NOT NULL,
    one_time_premium     NUMERIC(10, 2) NOT NULL,
    installments         SMALLINT NOT NULL,
    installment_fee      NUMERIC(6, 2) NOT NULL,
    total_premium        NUMERIC(10, 2) NOT NULL,
    installment_amount   NUMERIC(10, 2) NOT NULL,
    currency             VARCHAR(3) NOT NULL,
    CONSTRAINT uq_policies_quote_id UNIQUE (quote_id),
    CONSTRAINT uq_policies_policy_number UNIQUE (policy_number),
    CONSTRAINT ck_policies_vehicle_identity CHECK (num_nonnulls(vehicle_registration, vehicle_vin) = 1),
    -- Coverage runs inclusive at both ends (AD-6), so a one-day policy is
    -- the shortest legal period, never an inverted one.
    CONSTRAINT ck_policies_coverage_period CHECK (coverage_end >= coverage_start)
);

-- Backs the owner-scoped reads every client request goes through (AD-10) -
-- this story's replay lookup by (quote_id, customer_id), and Story 8.3's
-- My Policies list.
CREATE INDEX idx_policies_customer_id ON policies (customer_id);
