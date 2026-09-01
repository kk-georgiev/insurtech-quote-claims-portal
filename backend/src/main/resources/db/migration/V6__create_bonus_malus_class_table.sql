-- V6: pricing module's bonus-malus reference table (Story 6.1 - Bonus-Malus
-- Rating Factor). The class and its coefficient live as data here, like
-- every other tariff dimension (tariff_rate/age_surcharge/installment_plan,
-- V3__create_pricing_tables.sql) - not hardcoded in Java - so a coefficient
-- change is a data change, not a redeploy (Architecture Spine AD-8).
--
-- PROVENANCE (binding, product owner, 2026-08-31 - see the Milestone 3 PRD's
-- FR-M3-16 provenance constraint): these five classes and their coefficients
-- are this project's OWN DEMO MODEL, carried over from the team's earlier
-- prototype (feat/quote-engine-v1, docs/quote_pricing_v1.md, preserved in
-- the Milestone 1 PRD addendum's superseded-formula appendix). They are NOT
-- official, actuarially derived, or regulatorily mandated values for the
-- Bulgarian insurance market. Every place this scale is surfaced to a reader
-- (README, OpenAPI description, UI copy) must carry the same disclaimer.

CREATE TABLE bonus_malus_class (
    code   VARCHAR(20) PRIMARY KEY,
    factor NUMERIC(4, 3) NOT NULL
);

INSERT INTO bonus_malus_class (code, factor) VALUES
    ('BONUS_20', 0.800),
    ('BONUS_10', 0.900),
    ('NEUTRAL',  1.000),
    ('MALUS_25', 1.250),
    ('MALUS_50', 1.500);
