-- V2: auth module's `users` table (Story 1.2 - Client Self-Registration).
--
-- Mirrors auth/domain/User.java exactly. `role` is CHECK-constrained to all
-- four Role enum values now (CLIENT, AGENT, LIQUIDATOR, ADMINISTRATOR) per
-- the Architecture Spine, even though this story only ever inserts CLIENT
-- rows via self-registration - Epic 2 seeds the other three into this same
-- table without a further migration change.
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('CLIENT', 'AGENT', 'LIQUIDATOR', 'ADMINISTRATOR')),
    created_at TIMESTAMPTZ NOT NULL
);
