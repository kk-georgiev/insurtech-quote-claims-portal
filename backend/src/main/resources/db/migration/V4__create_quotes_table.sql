-- V4: quote module's `quotes` table (Story 1.6 - Quote Persistence and
-- Retrieval). Every calculated quote is persisted immediately as part of
-- calculation - there is no separate "save" step (epics.md Story 1.6 AC).
--
-- Stores the resolved breakdown flat (not a foreign key into pricing's
-- tariff_rate/age_surcharge/installment_plan rows): those reference tables
-- can change over time, but a quote must keep showing exactly what the
-- customer was quoted at the time, not today's tariff.
CREATE TABLE quotes (
    id                 UUID PRIMARY KEY,
    customer_id        UUID NOT NULL REFERENCES users(id),
    driver_age         INTEGER NOT NULL,
    region_code        VARCHAR(5) NOT NULL,
    engine_cc          INTEGER NOT NULL,
    zone_id            SMALLINT NOT NULL,
    base_premium       NUMERIC(10, 2) NOT NULL,
    age_surcharge      NUMERIC(6, 2) NOT NULL,
    one_time_premium   NUMERIC(10, 2) NOT NULL,
    installments       SMALLINT NOT NULL,
    installment_fee    NUMERIC(6, 2) NOT NULL,
    total_premium      NUMERIC(10, 2) NOT NULL,
    installment_amount NUMERIC(10, 2) NOT NULL,
    currency           VARCHAR(3) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL
);

-- Backs the ownership-scoped lookup in QuoteRepository - every retrieval is
-- by (id, customer_id) together, never id alone (IDOR protection, AC "never
-- shown someone else's data").
CREATE INDEX idx_quotes_customer_id ON quotes (customer_id);
