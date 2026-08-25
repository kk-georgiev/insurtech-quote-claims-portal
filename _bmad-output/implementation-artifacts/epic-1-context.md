# Epic 1 Context: Client Gets an Instant Quote

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

A prospective client can register, log in, and receive a transparent, calculated insurance premium — the core vertical slice that proves the chosen architecture (auth, roles, persistence, deployment) end-to-end before any other module is built on top of it. This is also the first code in the repo: nothing exists yet, so the epic starts with scaffolding a runnable backend/frontend skeleton wired to local Postgres. The demo pricing formula is an explicit, swappable placeholder — the real goal is proving the mechanics (input → calculation → persistence → retrieval → transparent breakdown), not the formula's business accuracy.

## Stories

- Story 1.1: Project Scaffolding — Runnable Backend and Frontend Skeleton
- Story 1.2: Client Self-Registration
- Story 1.3: Login Issuing a Role-Bearing JWT
- Story 1.4: Backend-Enforced Access to the Quote Endpoints
- Story 1.5: Quote Calculation With Transparent Breakdown
- Story 1.6: Quote Persistence and Retrieval

## Requirements & Constraints

- Self-registration creates a User with Role=CLIENT only — staff roles are never self-registrable (would be a privilege-escalation bug, not a convenience).
- Duplicate-email registration is rejected with a specific, non-generic error, not a generic failure.
- Login accepts any account (self-registered or seeded) and returns a token carrying user id + Role. Wrong password or unknown email returns one generic "invalid credentials" error — never reveals whether the email exists (no user enumeration).
- Every protected endpoint independently checks the caller's Role: 401 for missing/invalid token, 403 for wrong role, regardless of frontend behavior.
- Quote calculation inputs: driver age 18–100; driving experience 0–82 years and never greater than age−17; region one of SOFIA/LARGE_CITY/OTHER; vehicle power 20–500 kW; bonus-malus one of BONUS_20/BONUS_10/NEUTRAL/MALUS_25/MALUS_50. Logically inconsistent input (e.g. experience exceeding age−17) produces a field-level validation error, not a generic one.
- Validation rules are enforced identically at the API layer and as DB constraints — invalid data must not enter through any path, including direct DB access.
- Every monetary value (premium, base premium, factors) uses exact decimal precision end-to-end (calculation, API, persistence) — floating-point is never used for money.
- A calculated Quote always persists its inputs, applied factors, resulting premium, and creation time; retrieval by ID returns the full original quote. A client can only retrieve their own quotes — another client's quote ID is rejected, never returned.
- Passwords (registration path) are hashed and never stored, logged, or returned in plain text.
- No password, token secret, or credential appears in source control, logs, or committed config — only `.env`-style files, with `.env.example` documenting every required variable with no real values.
- Out of scope for this epic: email verification, password-strength UI, forgot-password flow, accepting a quote into a policy, listing/browsing past quotes (single-ID retrieval only).

## Technical Decisions

- Modular monolith, package-by-feature backend under `com.motorinsurance`; this epic touches only the `auth`, `quote`, `pricing`, and `shared` modules — no other module package exists yet.
- Cross-module access happens only through a module's `application`-layer service; no module reaches into another's `domain`/`persistence` package. `quote` reads the current user's identity/Role from the Spring Security context set by `auth`'s JWT filter — never by calling `auth` directly. `pricing` exposes exactly one application-layer calculation service as its sole entry point; its domain classes are never imported directly.
- Auth is stateless JWT: one token per login, carrying user id + Role, multi-hour expiry, no refresh-token mechanism this milestone. Signed with a single symmetric HMAC key (e.g. HS256) read from an environment variable — never hardcoded. One shared filter validates every protected endpoint; no per-controller token parsing.
- All money is `BigDecimal` in Java and `NUMERIC` in PostgreSQL, never float/double, at every layer including JSON serialization.
- Error responses use one uniform envelope `{timestamp, status, code, message, fieldErrors}` from a single centralized exception handler in `shared`. `code` is a stable, namespaced `MODULE_REASON` key (e.g. `AUTH_EMAIL_TAKEN`, `AUTH_INVALID_CREDENTIALS`, `QUOTE_VALIDATION_ERROR`); `message` is developer/log-facing only, never shown to the user directly.
- Demo tariff formula (implemented as `pricing`'s one service): `premium = base_premium × age_factor × experience_factor × region_factor × power_factor × bonus_malus_factor`; base premium 180.00 EUR; result rounded to 2 decimals and bounded 120.00–1500.00 EUR. Full factor bands (age, experience, region, power, bonus-malus) are recorded in the PRD addendum and must be sourced from there, not from the unused prototype branch's `docs/quote_pricing_v1.md`.
- Repo/source tree for this epic: `backend/src/main/java/com/motorinsurance/{auth,quote,pricing,shared}`, each internally layered `api/application/domain/persistence`; `frontend/src/{app,features/{auth,quote},i18n,api}`. Flyway migrations named `V{n}__{description}.sql`; DB tables `snake_case`, plural; IDs are `UUID`; timestamps `Instant` stored UTC.
- Local dev: `docker compose up postgres` runs only the database; backend/frontend run natively (`mvn spring-boot:run` / `npm run dev`) against it. Frontend API base URL is always injected via `VITE_API_URL`, never hardcoded, so both native and containerized runs resolve the backend the same way.
- Stack pins: Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 18, Flyway, React 19, TypeScript 6.x, Vite 8, React Router 8. REST API versioned under `/api/v1`.

## UX & Interaction Patterns

Core flow (unauthenticated landing → register with email+password → log in → open "New Quote" → enter driver age, driving experience, region, vehicle power, bonus-malus → submit → see total premium with full breakdown within seconds → can revisit the same quote later by ID). Accepting a quote into a policy is explicitly not part of this flow. No visual design/UI polish is required this milestone — screens must be functional and correctly gated, not finished-looking.

## Cross-Story Dependencies

- Story 1.1 (scaffolding) is a prerequisite for every other story in this epic — nothing else can start until backend/frontend/DB are wired.
- Story 1.4 (backend-enforced Quote access) depends on Story 1.3 (JWT issuance) for a Role-bearing token to check.
- Story 1.5 (quote calculation) depends on Story 1.4's auth gate and on `pricing`'s calculation service being in place.
- Story 1.6 (persistence/retrieval) depends on Story 1.5 producing a calculated quote to persist.
- Epic 2 depends on this epic's login/JWT mechanism (Story 1.3) for role-based post-login routing, and its seed-migration story reuses Story 1.2's exact password-hashing path (no plaintext, including in the migration itself).
- Epic 3 (i18n) depends on every screen and message this epic introduces (registration, login, quote form/result, validation/error codes) existing and using the AD-7 error-code contract, so they can be fully translated.
