-- V7: adds the bonus-malus rating factor to quotes (Story 6.1). Every new
-- column arrives with its backfill in the same migration (Architecture
-- Spine AD-9) - existing quotes are backfilled to the neutral class with
-- factor 1.000, which leaves every persisted premium byte-identical (the
-- one_time_premium formula multiplies by this factor - see
-- PricingService#calculate - and x1.000 changes nothing).
--
-- bonus_malus_code/bonus_malus_factor are stored flat on the quote, like
-- every other resolved breakdown component (V4__create_quotes_table.sql's
-- own header) - not a foreign key dereferenced at read time - so a later
-- coefficient change never rewrites what a customer was already quoted.
-- The FK to bonus_malus_class exists only to catch a bad code at write time,
-- matching tariff_rate/age_surcharge's own referential pattern.

ALTER TABLE quotes
    ADD COLUMN bonus_malus_code   VARCHAR(20) REFERENCES bonus_malus_class(code),
    ADD COLUMN bonus_malus_factor NUMERIC(4, 3);

UPDATE quotes
SET bonus_malus_code = 'NEUTRAL',
    bonus_malus_factor = 1.000
WHERE bonus_malus_code IS NULL;

ALTER TABLE quotes
    ALTER COLUMN bonus_malus_code SET NOT NULL,
    ALTER COLUMN bonus_malus_factor SET NOT NULL;
