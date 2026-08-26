-- V3: pricing module's reference-data tables (Story 1.5 - Quote Calculation
-- With Transparent Breakdown).
--
-- The GO (Гражданска отговорност) tariff lives as data here, not as
-- hardcoded coefficients in Java, so it can be corrected/extended without a
-- redeploy. Source: two teammate-provided tariff spreadsheets, reconciled
-- and verified against Bulgaria's 28 registration oblasti - see the PRD
-- addendum's "Quote Engine - Milestone 1 tariff" section for the full
-- derivation and the two open data-quality caveats (BA / CP / XX) that this
-- seed data resolves conservatively by omission.

CREATE TABLE tariff_zone (
    zone_id   SMALLINT PRIMARY KEY,
    zone_name VARCHAR(20) NOT NULL
);

CREATE TABLE region_zone_map (
    region_code VARCHAR(5) PRIMARY KEY,
    zone_id     SMALLINT NOT NULL REFERENCES tariff_zone(zone_id)
);

CREATE TABLE tariff_rate (
    id            BIGSERIAL PRIMARY KEY,
    zone_id       SMALLINT NOT NULL REFERENCES tariff_zone(zone_id),
    engine_cc_min INTEGER NOT NULL,
    engine_cc_max INTEGER NULL, -- NULL = no upper bound ("2501 and above")
    base_premium  NUMERIC(10, 2) NOT NULL,
    currency      VARCHAR(3) NOT NULL DEFAULT 'EUR', -- CHAR(3) would report as bpchar and fail Hibernate's ddl-auto:validate against the plain String-mapped entity field
    CONSTRAINT chk_tariff_rate_cc_range CHECK (engine_cc_max IS NULL OR engine_cc_max >= engine_cc_min)
);
-- One rate per zone/cc-band; also the index the range lookup in
-- TariffRateRepository relies on.
CREATE UNIQUE INDEX ux_tariff_rate_zone_cc_min ON tariff_rate (zone_id, engine_cc_min);

CREATE TABLE age_surcharge (
    id       BIGSERIAL PRIMARY KEY,
    min_age  INTEGER NOT NULL,
    max_age  INTEGER NULL, -- NULL = no upper bound (the 86+ band)
    surcharge NUMERIC(6, 2) NOT NULL,
    CONSTRAINT chk_age_surcharge_range CHECK (max_age IS NULL OR max_age >= min_age)
);

CREATE TABLE installment_plan (
    installments SMALLINT PRIMARY KEY,
    fee          NUMERIC(6, 2) NOT NULL,
    CONSTRAINT chk_installment_plan_value CHECK (installments IN (1, 2, 4))
);

INSERT INTO tariff_zone (zone_id, zone_name) VALUES
    (1, 'Zone 1'),
    (2, 'Zone 2'),
    (3, 'Zone 3'),
    (4, 'Zone 4'),
    (5, 'Zone 5');

-- All 28 Bulgarian registration oblasti, one code each, plus Sofia-city's
-- two confirmed overflow codes (CA, CB). Deliberately excludes: `BA` (that
-- code is Bulgaria's special military-vehicle plate, not a civilian Sofia
-- sub-code - see addendum.md) and `CP`/`XX` (further Sofia overflow codes
-- claimed by the source spreadsheet but not independently verifiable at
-- review time). An unmapped code fails closed as "unknown region" rather
-- than silently pricing at the wrong zone.
INSERT INTO region_zone_map (region_code, zone_id) VALUES
    ('KH', 1), ('PK', 1), ('T', 1), ('TX', 1), ('BH', 1), ('CC', 1), ('K', 1),
    ('EB', 1), ('CH', 1), ('P', 1), ('PA', 1), ('PP', 1), ('CM', 1),
    ('C', 2), ('PB', 2), ('CA', 2), ('CB', 2),
    ('E', 3), ('H', 3), ('BT', 3), ('BP', 3), ('M', 3), ('EH', 3),
    ('A', 4), ('OB', 4), ('X', 4), ('CT', 4), ('CO', 4), ('Y', 4),
    ('B', 5);

-- Base premium (25-85y baseline) by zone x engine-cc band, EUR, one-time
-- payment - see addendum.md's table for the source.
INSERT INTO tariff_rate (zone_id, engine_cc_min, engine_cc_max, base_premium) VALUES
    (1, 800, 1300, 131.91), (1, 1301, 2100, 141.12), (1, 2101, 2500, 144.18), (1, 2501, NULL, 166.17),
    (2, 800, 1300, 140.91), (2, 1301, 2100, 153.90), (2, 2101, 2500, 171.78), (2, 2501, NULL, 182.02),
    (3, 800, 1300, 128.85), (3, 1301, 2100, 135.49), (3, 2101, 2500, 143.67), (3, 2501, NULL, 166.17),
    (4, 800, 1300, 126.80), (4, 1301, 2100, 134.50), (4, 2101, 2500, 142.60), (4, 2501, NULL, 166.17),
    (5, 800, 1300, 140.09), (5, 1301, 2100, 148.79), (5, 2101, 2500, 156.97), (5, 2501, NULL, 169.24);

INSERT INTO age_surcharge (min_age, max_age, surcharge) VALUES
    (18, 24, 36.00),
    (25, 85, 0.00),
    (86, NULL, 10.00);

INSERT INTO installment_plan (installments, fee) VALUES
    (1, 0.00),
    (2, 2.00),
    (4, 4.00);
