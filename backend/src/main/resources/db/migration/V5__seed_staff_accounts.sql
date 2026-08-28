-- V5: the three staff demo accounts (Story 2.1 - Seeded Staff Demo Accounts).
--
-- Self-registration (Story 1.2) hardcodes Role.CLIENT, so it is the only
-- provisioning path and it can never produce a staff user. Epic 2 has to
-- demonstrate all four roles, which means AGENT, LIQUIDATOR and
-- ADMINISTRATOR accounts must exist before any of the role-aware routing,
-- shell or guard stories are demonstrable. V2's `role` CHECK constraint
-- already permits all four values, so this migration adds rows only - no DDL.
--
-- These accounts authenticate through Story 1.3's /api/v1/auth/login
-- completely unchanged: no code path special-cases a seeded user. That works
-- by construction only if every column below is written exactly as the
-- application would have written it:
--
--   * `email` is already normalized (trimmed, lower-case) because
--     AuthenticationService looks up by Emails.normalize(...) against
--     users.email's case-SENSITIVE UNIQUE column - a capitalized seed email
--     here would be unloggable-in.
--   * `role` is the exact Role enum constant name; User maps it
--     @Enumerated(EnumType.STRING).
--   * `id` and `created_at` are literal, since V2 deliberately gives neither
--     column a DEFAULT (the entity supplies both).
--   * `password_hash` is BCrypt cost 10 ($2a$), the output of the very
--     `new BCryptPasswordEncoder()` that PasswordEncoderConfig exposes as
--     the app's bean and that RegistrationService hashes with. Generated
--     out-of-band; only the hash was carried into this file.
--
-- NFR-2 (no real secrets in source control) holds: the plaintext appears in
-- exactly two places, both of them documentation-or-proof of these
-- demo-only accounts - README.md's "Demo accounts" table, and
-- SeededStaffAccountsTest, which logs in with those very credentials to
-- prove the table is not stale. It appears in no migration, no database
-- column, and no runtime configuration; same demo-only category as
-- .env.example's placeholders. The `.demo` TLD cannot collide with the
-- `auth-test-<uuid>@example.com` / `quote-test-<uuid>@example.com` addresses
-- the existing test suite registers, nor with any real mailbox.
--
-- ON CONFLICT DO NOTHING (untargeted, deliberately) makes re-applying this
-- migration to an already-seeded database a no-op. The targeted
-- `ON CONFLICT (email)` form would arbitrate only users_email_key, so a
-- database already holding one of these literal UUIDs under some other
-- address - the restored-dump case this clause exists for - would still
-- raise users_pkey and fail the migration at startup. Untargeted covers
-- every unique constraint on the table, which is the actual intent.
INSERT INTO users (id, email, password_hash, role, created_at) VALUES
    ('bd8a03c5-0f35-4103-b864-c8ff728ea476',
     'agent@motorinsurance.demo',
     '$2a$10$.2ygkeDpoX3vKiEy3Adwhelt9hKw6FPkL.MSPh1zNrvJ8O6iMf4Eu',
     'AGENT',
     TIMESTAMPTZ '2026-08-28 00:00:00+00'),
    ('f20ac9c9-c211-4e19-a61d-06b236969437',
     'liquidator@motorinsurance.demo',
     '$2a$10$157djUAnS1O2ocYc/tk1XOgcR3L3exrWP9KXQgXioJJ3rBcJfCDpu',
     'LIQUIDATOR',
     TIMESTAMPTZ '2026-08-28 00:00:00+00'),
    ('538a27f4-2e71-4c9e-b4f1-2a3f12d695e0',
     'administrator@motorinsurance.demo',
     '$2a$10$L8V0B0fVE5Fmf6j.5kKC.uti.3cgoO9OswSSanUqbYjMc586SzsM.',
     'ADMINISTRATOR',
     TIMESTAMPTZ '2026-08-28 00:00:00+00')
ON CONFLICT DO NOTHING;
