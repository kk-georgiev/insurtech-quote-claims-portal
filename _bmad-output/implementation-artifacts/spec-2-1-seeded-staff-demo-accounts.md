---
title: 'Story 2.1: Seeded Staff Demo Accounts'
type: 'feature'
created: '2026-08-28'
status: 'done'
review_loop_iteration: 0
baseline_commit: 'a84397abf46f8d3aa1b7b3750af3c7b3d8f870be'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Only CLIENT accounts can exist today, because self-registration is the sole provisioning path and it hardcodes `Role.CLIENT`. Epic 2 must demonstrate all four roles, and with no staff accounts there is nothing to log in as — every later Epic 2 story (routing, shells, guards) is undemonstrable.

**Approach:** Ship a `V5` Flyway migration that inserts exactly one user per staff role (AGENT, LIQUIDATOR, ADMINISTRATOR) with pre-computed BCrypt hashes, so the accounts authenticate through Story 1.3's login endpoint completely unchanged, and document the working credentials in the README.

## Boundaries & Constraints

**Always:**
- Seeded rows satisfy the existing schema exactly: literal `id` UUIDs and explicit `created_at` (**no** DB defaults for either), `role` as the exact enum name, `email` already-normalized (lowercase, trimmed) so `Emails.normalize` matches on login.
- Hashes are BCrypt cost 10, matching `new BCryptPasswordEncoder()`. Generate out-of-band; paste only the hash.
- Seed emails must be collision-proof against emails a developer or test might register.
- `AuthenticationService`, `User`, `Role`, `SecurityConfig` and the login path stay untouched — seeded accounts work by construction, never by special-casing.

**Ask First:**
- Any change to an existing migration file (`V1`–`V4`). Flyway checksums make edits a destructive operation on already-migrated databases.
- Adding methods to `UserRepository` or any other production type solely to support this story's tests.
- Choosing a provisioning mechanism other than a Flyway migration (runtime seeder, `CommandLineRunner`, `data.sql`).

**Never:**
- No plaintext password in the migration file or in any database column.
- No new backend module; no staff-role self-registration path; no admin user-management endpoint.
- No frontend work — routing, shells, and guards are Stories 2.2–2.4.
- No shared test base class. The six existing DB test classes each declare Testcontainers inline; follow that, do not refactor them.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Seeded staff login | `POST /api/v1/auth/login` with a documented staff email + password, fresh migrated DB | 200 with `{"token": "..."}`; parsed token's `role` equals that account's role | N/A |
| Wrong password on seeded account | Documented staff email, any other password | 401, code `AUTH_INVALID_CREDENTIALS` | Generic message, no user enumeration |
| Staff token on CLIENT-only endpoint | Valid seeded AGENT token → `POST /api/v1/quotes` | 403 — Epic 1's `@PreAuthorize("hasRole('CLIENT')")` still governs | Existing error envelope |
| Self-register a seeded email | `POST /api/v1/auth/register` with a seeded staff email | 409, code `AUTH_EMAIL_TAKEN` — the `users.email` UNIQUE constraint holds | Existing duplicate-email path |
| Migration re-applied to seeded DB | `V5` runs against a database already holding the seed rows | No duplicate rows, no constraint violation | `ON CONFLICT (email) DO NOTHING` |

</frozen-after-approval>

## Code Map

Backend paths below are relative to `backend/src/main/java/com/motorinsurance/` unless shown in full.

- `backend/src/main/resources/db/migration/` -- V1–V4 exist; **V5 is next**. `V3__create_pricing_tables.sql` is the precedent for seed data in a migration.
- `.../db/migration/V2__create_users_table.sql:8-14` -- `users` DDL. Its `role VARCHAR(50) CHECK (role IN (...))` **already permits all four values — V2 needs no change.** No `DEFAULT` on `id` or `created_at`.
- `auth/config/PasswordEncoderConfig.java:21-24` -- `new BCryptPasswordEncoder()` → cost 10, `$2a$`. The hash contract to match.
- `auth/domain/Emails.java:26-28` -- `trim().toLowerCase(Locale.ROOT)`, applied at login lookup (`auth/application/AuthenticationService.java:53`). The column is case-sensitive, so seeded emails must already be in this form.
- `auth/domain/Role.java:10-15` + `auth/domain/User.java:32-34` -- `@Enumerated(EnumType.STRING)`, so the DB string is the exact constant name.
- `auth/application/AuthenticationService.java:53-67` -- issues a token for whatever role the row carries. Evidence that seeded staff work through the unchanged path.
- `auth/api/AuthController.java:20,38-42` -- `POST /api/v1/auth/login` → 200 `{"token"}`; public per `auth/config/SecurityConfig.java:76`. `auth/api/LoginRequest.java:14-16` -- password `@Size(max = 100)` with **no minimum** (javadoc anticipates seeded staff); keep the demo password ≤ 100 chars.
- `backend/src/test/java/com/motorinsurance/auth/api/AuthControllerTest.java:40-50,98-114` -- the inline `@SpringBootTest(RANDOM_PORT)` + `@Testcontainers` + `PostgreSQLContainer<>("postgres:18")` + `@ServiceConnection` pattern, and the login → `jwtService.parseToken` → assert-role shape to copy. `.../pricing/application/PricingServiceTest.java:39-63` -- precedent for asserting on Flyway-seeded values.
- `README.md:23-50` -- "Getting started"; no demo-credentials home exists yet. `.gitignore:33-38` broad-ignores `.env*` (with a `!.env.example` negation), so credentials must live in a tracked doc file.
- Read-only evidence — **nothing breaks when `users` gains rows at startup**: no test asserts an empty table or row count; no `deleteAll`/`@Sql`/`TRUNCATE`/`@Rollback` anywhere in `backend/src/test`; existing DB tests use UUID-randomized emails (`AuthControllerTest.java:151`, `QuoteControllerTest.java:301`); `auth/persistence/UserRepository.java:13-15` exposes only `findByEmail`.

## Tasks & Acceptance

**Execution:**
- [x] Generate three BCrypt cost-10 hashes out-of-band -- run a throwaway `new BCryptPasswordEncoder().encode(...)` (scratch JUnit method or `jshell` with the Spring Security jar) -- the hashes must be produced by the same encoder the registration path uses, and only the hash is carried forward.
- [x] `backend/src/main/resources/db/migration/V5__seed_staff_accounts.sql` -- new migration inserting exactly three rows with literal UUIDs, already-lowercased emails, the generated hashes, roles `AGENT`/`LIQUIDATOR`/`ADMINISTRATOR`, an explicit `created_at`, and `ON CONFLICT (email) DO NOTHING` -- provisions staff without touching application code.
- [x] `backend/src/test/java/com/motorinsurance/auth/api/SeededStaffAccountsTest.java` -- new integration test covering the I/O matrix rows: login as each of the three seeded accounts and assert the parsed token's role; wrong-password 401; duplicate-registration 409; staff-token-on-quote-endpoint 403. Assert exactly one row per staff role and that each `password_hash` is a 60-char `$2`-prefixed BCrypt string (query via `JdbcTemplate`, not by extending `UserRepository`).
- [x] `README.md` -- add a "Demo accounts" subsection under Getting started listing the three emails, the shared password, and their roles -- FR-4 requires the credentials be documented for the team and mentor.

**Acceptance Criteria:**
- Given a fresh database, when migrations complete, then exactly one user exists for each of AGENT, LIQUIDATOR, and ADMINISTRATOR, and the CLIENT role is unaffected.
- Given the migration file, when inspected, then it contains no plaintext password — only BCrypt hashes — and the README's documented passwords are the only place plaintext appears.
- Given a seeded staff account, when it logs in via Story 1.3's endpoint, then authentication succeeds with no code path special-casing seeded users.
- Given the full backend test suite, when run, then every pre-existing test still passes — the new rows must not perturb them.

## Spec Change Log

## Design Notes

**Why literal hashes in SQL.** The Architecture Spine assigns staff seeding to a Flyway migration, and plain SQL keeps it deterministic and checksum-stable. A `JavaMigration` would need the Spring `PasswordEncoder` bean, which Flyway runs too early to have; a `CommandLineRunner` satisfies neither the epic's "fresh migration run" wording nor Epic 4's one-command startup.

**Resolving FR-4 against NFR-2.** FR-4 wants documented working credentials; NFR-2 forbids real secrets in source control. These are demo-only accounts — the same category as `.env.example`'s placeholders, which that file's own header calls insecure local-dev defaults. Migration and database hold hashes only; plaintext lives solely as README documentation, carrying the same one-line "not production credentials" warning.

**Proposed values** (change freely — they are documentation, not derived from anything):
```
agent@motorinsurance.demo          / DemoPass123!   → AGENT
liquidator@motorinsurance.demo     / DemoPass123!   → LIQUIDATOR
administrator@motorinsurance.demo  / DemoPass123!   → ADMINISTRATOR
```
The `.demo` TLD cannot collide with the `auth-test-<uuid>@example.com` / `quote-test-<uuid>@example.com` emails existing tests generate. One shared password keeps the mentor demo friction-free; the accounts are distinguished by role, not by secret.

## Verification

**Commands:**
- `cd backend; mvn test` -- (no Maven wrapper in this repo; use the system `mvn`) expected: all tests green, including the new `SeededStaffAccountsTest`, with no regressions in the 63 pre-existing tests. Requires a running Docker daemon for Testcontainers, and `JAVA_HOME` exported per-shell (it is deliberately not set globally on this machine; JDK 21 lives at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` — the machine's default JDK is 25, which this build does not target).
- `docker compose down -v; docker compose up postgres` then start the backend -- expected: Flyway applies V5 cleanly against a genuinely empty volume, and the app starts with `ddl-auto: validate` passing.

**Manual checks:**
- `SELECT email, role, left(password_hash, 4) FROM users ORDER BY role;` -- expected: three staff rows, each hash beginning `$2a$` (or `$2b$`), no plaintext.
- Log in through the frontend or curl with each documented credential -- expected: 200 and a token whose `role` claim matches.

## Suggested Review Order

**The seed itself**

- Entry point: the three rows, with literal ids and `created_at` because V2 defaults neither.
  [`V5__seed_staff_accounts.sql:45`](../../backend/src/main/resources/db/migration/V5__seed_staff_accounts.sql#L45)

- Untargeted on purpose: the `(email)` form leaves a `users_pkey` clash able to fail startup.
  [`V5__seed_staff_accounts.sql:61`](../../backend/src/main/resources/db/migration/V5__seed_staff_accounts.sql#L61)

**Proof the accounts work through Story 1.3's unchanged login**

- Each seeded login asserts both the role and the seeded UUID in the token.
  [`SeededStaffAccountsTest.java:103`](../../backend/src/test/java/com/motorinsurance/auth/api/SeededStaffAccountsTest.java#L103)

- All three staff tokens still 403 on the CLIENT-only endpoint; no accidental authority.
  [`SeededStaffAccountsTest.java:153`](../../backend/src/test/java/com/motorinsurance/auth/api/SeededStaffAccountsTest.java#L153)

- A seeded email is not privileged — self-registering it still returns 409.
  [`SeededStaffAccountsTest.java:142`](../../backend/src/test/java/com/motorinsurance/auth/api/SeededStaffAccountsTest.java#L142)

**Row shape and the no-plaintext guarantee**

- One row per staff role, no CLIENT row, and the literal ids pinned.
  [`SeededStaffAccountsTest.java:174`](../../backend/src/test/java/com/motorinsurance/auth/api/SeededStaffAccountsTest.java#L174)

- Stored emails must already be canonical, or the account is unloggable-in.
  [`SeededStaffAccountsTest.java:194`](../../backend/src/test/java/com/motorinsurance/auth/api/SeededStaffAccountsTest.java#L194)

- Hashes are BCrypt cost 10 with distinct salts, never the plaintext.
  [`SeededStaffAccountsTest.java:206`](../../backend/src/test/java/com/motorinsurance/auth/api/SeededStaffAccountsTest.java#L206)

- Re-application is a genuine no-op; Flyway alone would never exercise this.
  [`SeededStaffAccountsTest.java:256`](../../backend/src/test/java/com/motorinsurance/auth/api/SeededStaffAccountsTest.java#L256)

**Documentation as deliverable (FR-4)**

- The credential table itself — the only place the plaintext is published.
  [`README.md:52`](../../README.md#L52)

- Removal command, plus why Flyway will not re-seed afterwards.
  [`README.md:84`](../../README.md#L84)

- Pins the README to the seeded values so table and hashes cannot drift.
  [`SeededStaffAccountsTest.java:237`](../../backend/src/test/java/com/motorinsurance/auth/api/SeededStaffAccountsTest.java#L237)

- A pointer only, next to the Flyway description; credentials deliberately not duplicated.
  [`backend/README.md:51`](../../backend/README.md#L51)

- Story and epic moved off `backlog` by the build's sprint sync.
  [`sprint-status.yaml:48`](sprint-status.yaml#L48)
