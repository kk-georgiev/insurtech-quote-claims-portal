CREATE TABLE quotes (
    id UUID PRIMARY KEY,
    status VARCHAR(30) NOT NULL,
    driver_age INTEGER NOT NULL CHECK (driver_age BETWEEN 18 AND 100),
    driving_experience_years INTEGER NOT NULL CHECK (driving_experience_years BETWEEN 0 AND 82),
    region_risk VARCHAR(30) NOT NULL,
    vehicle_power_kw INTEGER NOT NULL CHECK (vehicle_power_kw BETWEEN 20 AND 500),
    bonus_malus_level VARCHAR(30) NOT NULL,
    base_premium NUMERIC(12, 2) NOT NULL,
    age_factor NUMERIC(6, 3) NOT NULL,
    experience_factor NUMERIC(6, 3) NOT NULL,
    region_factor NUMERIC(6, 3) NOT NULL,
    power_factor NUMERIC(6, 3) NOT NULL,
    bonus_malus_factor NUMERIC(6, 3) NOT NULL,
    premium NUMERIC(12, 2) NOT NULL CHECK (premium >= 0),
    currency VARCHAR(3) NOT NULL,
    pricing_version VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_quote_status CHECK (status IN ('CREATED', 'ACCEPTED', 'EXPIRED')),
    CONSTRAINT chk_quote_region CHECK (region_risk IN ('SOFIA', 'LARGE_CITY', 'OTHER')),
    CONSTRAINT chk_quote_bonus_malus CHECK (
        bonus_malus_level IN ('BONUS_20', 'BONUS_10', 'NEUTRAL', 'MALUS_25', 'MALUS_50')
    ),
    CONSTRAINT chk_quote_experience CHECK (driving_experience_years <= driver_age - 17),
    CONSTRAINT chk_quote_validity CHECK (valid_until > created_at)
);

CREATE INDEX idx_quotes_created_at ON quotes (created_at DESC);
CREATE INDEX idx_quotes_status ON quotes (status);
