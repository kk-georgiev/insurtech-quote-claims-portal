---
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-motor-insurance-quote-claims-portal-2026-08-23/prd.md
  - _bmad-output/planning-artifacts/prds/prd-motor-insurance-quote-claims-portal-2026-08-23/addendum.md
  - _bmad-output/planning-artifacts/architecture/architecture-motor-insurance-quote-claims-portal-2026-08-23/ARCHITECTURE-SPINE.md
---

# Motor Insurance Quote & Claims Portal — Milestone 1 - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for Milestone 1 (runnable full-stack skeleton), decomposing the requirements from the PRD and Architecture Spine into implementable stories. No separate UX design document exists for this milestone — visual design was explicitly out of scope (PRD Non-Goals); the Architecture Spine was produced as build-substrate only, by team choice.

## Requirements Inventory

### Functional Requirements

FR-1: A prospective client can register with an email and password (CLIENT role only).
FR-2: Any user (self-registered or seeded) can log in and receive a JWT identifying their Role.
FR-3: Every protected API endpoint checks the caller's Role from their token before executing (403 wrong role, 401 missing/invalid token).
FR-4: One seeded demo account per staff Role (AGENT, LIQUIDATOR, ADMINISTRATOR) exists after a fresh migration.
FR-5: Immediately after login, the user is routed to the Navigation Shell matching their Role.
FR-6: Each of the four roles has at least one reachable, role-labeled placeholder screen.
FR-7: The frontend refuses to render another role's screens even on manual URL navigation.
FR-8: An authenticated CLIENT can submit driver/vehicle parameters and receive a calculated Premium.
FR-9: The Quote response includes the base premium and every individual Tariff Factor applied.
FR-10: Every calculated Quote is persisted with its inputs, applied factors, resulting Premium, and creation time.
FR-11: A CLIENT can retrieve a Quote they previously created by its ID.
FR-12: From a clean checkout with only Docker installed, one documented command brings up the full stack.
FR-13: A developer can still run backend and frontend natively against a containerized database.
FR-14: A user can switch the Display Language (Bulgarian default / English) at any time; the selection persists client-side across reloads.
FR-15: Backend API responses (including errors) use stable, language-independent codes — never embedded human-readable prose shown directly to the user.

### NonFunctional Requirements

NFR-1 (Money precision): Every monetary value is represented and calculated with exact decimal precision end-to-end; floating-point is never used for money.
NFR-2 (Security baseline): No password, token secret, or credential appears in source control, logs, or committed config — `.env`-style files only, with `.env.example` documenting required variables.
NFR-3 (Password hashing): Passwords — self-registered and seeded — are hashed, never stored or logged in plain text, including in the seed migration itself.
NFR-4 (Data integrity): Structural validation rules — required fields, uniqueness, and length/size caps — are enforced at both the API layer and the database layer (constraints). Rules with no DB-layer equivalent (password complexity, checked pre-hash and never persisted in raw form; email format, which the DB layer cannot mirror beyond `NOT NULL UNIQUE` + a length cap) are enforced at the API layer only.
NFR-5 (Startup reliability, [ASSUMPTION]): The full Docker stack should become usable within a couple of minutes on a typical dev laptop on first run (image pulls aside), near-instantly on subsequent runs.
NFR-6 (i18n maintainability): Adding a new screen or message later must not require backend changes purely to support translation.

### Additional Requirements (from Architecture Spine)

- AD-1/AD-6: Modular monolith, package-by-feature backend; only `auth`, `quote`, `pricing`, `shared` modules exist this milestone — no other module package is pre-created.
- AD-2: Cross-module access only through the target module's `application`-layer service; no module imports another module's `domain`/`persistence` package. `pricing` exposes exactly one application-layer calculation service as its sole entry point.
- AD-3: Single stateless JWT (multi-hour expiry), no refresh-token mechanism this milestone.
- AD-4: Role authorization is backend-enforced everywhere; the frontend role-guard is UX-only, never the security boundary.
- AD-5: `BigDecimal` in Java / `NUMERIC` in PostgreSQL for all money, no floats.
- AD-7: Uniform API error envelope `{timestamp, status, code, message, fieldErrors}`; `code` is namespaced `MODULE_REASON` and every code has a matching i18n entry, added together.
- AD-8: i18n is 100% frontend-owned (`react-i18next`); backend never emits localized prose.
- AD-9: One `docker-compose.yml` (postgres + backend + frontend); `docker compose up` = full stack, `docker compose up postgres` = DB-only for local dev; frontend API base URL resolved via `VITE_API_URL` (never hardcoded), consistent across native dev and containerized run.
- AD-10: React Router v8; one role-guard wrapper component gates role-restricted routes; a single typed `fetch`-based API client module, no data-fetching library.
- Stack/version pins: Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 18, Flyway, React 19, TypeScript 6.x, Vite 8, React Router 8, react-i18next.
- Source tree: `backend/src/main/java/com/motorinsurance/{auth,quote,pricing,shared}`, each internally layered `api/application/domain/persistence`; `frontend/src/{app,features/{auth,quote,shells},i18n,api}`.
- Tariff to implement in `pricing`: a real GO (motor third-party liability) tariff, zone/engine-cc based, superseding the earlier placeholder multiplicative formula — fully recorded in the PRD addendum (`addendum.md`, "Quote Engine — Milestone 1 tariff" section, updated 2026-08-26). `one_time_premium = base_premium(zone, engine_cc) + age_surcharge`; `total_premium = one_time_premium + installment_fee(installments)`. Inputs: `driver_age`, `region_code` (vehicle plate prefix), `engine_cc`, `installments` (1/2/4) — no experience or vehicle-power factor in this model. Full base-premium table (5 zones × 4 cc bands), age surcharge, installment fee, and the region→zone mapping (28 Bulgarian oblasti) are reproduced in the addendum in full.
- Deferred item still requiring an implementation home: seed-data migration for the 3 staff demo accounts (hashed passwords), per Architecture Spine's Deferred section — assigned to the `auth` epic below.

### UX Design Requirements

Not applicable — no UX design document exists for this milestone (explicit team choice; PRD marks visual design out of scope).

### FR Coverage Map

FR-1: Epic 1 - Client self-registration
FR-2: Epic 1 - Login issuing role-bearing JWT
FR-3: Epic 1 - Backend-enforced role authorization (first exercised protecting Quote endpoints)
FR-4: Epic 2 - Seeded staff demo accounts
FR-5: Epic 2 - Role-based post-login routing
FR-6: Epic 2 - Placeholder screen per role
FR-7: Epic 2 - Frontend route guards
FR-8: Epic 1 - Quote calculation
FR-9: Epic 1 - Transparent price breakdown
FR-10: Epic 1 - Quote persistence
FR-11: Epic 1 - Quote retrieval
FR-12: Epic 4 - One-command full-stack startup
FR-13: Epic 4 - Local dev workflow preserved
FR-14: Epic 3 - Language toggle
FR-15: Epic 3 - Backend stays language-agnostic

NFR-1 (money precision): Epic 1
NFR-2 (security baseline / secrets): cross-cutting — established in Epic 1 Story 1 (.env.example), reinforced in Epic 4 (container secrets handling)
NFR-3 (password hashing): Epic 1 (registration) + Epic 2 (seed migration)
NFR-4 (validation both layers): Epic 1
NFR-5 (startup reliability): Epic 4
NFR-6 (i18n maintainability): Epic 3

## Epic List

### Epic 1: Client Gets an Instant Quote
A prospective client can register, log in, and receive a transparent, calculated insurance premium — the core vertical slice proving the architecture end-to-end (realizes PRD UJ-1). Includes the necessary project scaffolding (backend/frontend skeleton, base Flyway migration, minimal docker-compose with a `postgres` service for local dev) as its first story, since nothing exists yet.
**FRs covered:** FR-1, FR-2, FR-3, FR-8, FR-9, FR-10, FR-11

### Epic 2: Every Role Gets Their Own Workspace
Any of the four roles — including staff provisioned via seed data — logs in and lands on their own correctly role-guarded navigation shell, proving the role model is real, not cosmetic (realizes PRD UJ-2).
**FRs covered:** FR-4, FR-5, FR-6, FR-7

### Epic 3: The Portal Speaks Bulgarian and English
Every screen delivered by Epic 1 and Epic 2 is fully usable in Bulgarian (default) or English, switchable at any time.
**FRs covered:** FR-14, FR-15

### Epic 4: The Team Demos From a Clean Machine
Anyone can clone the repository and bring up the entire working system (Epic 1–3's functionality) with one command, alongside a preserved native dev workflow — realizes PRD UJ-3, the mentor-demo success metric.
**FRs covered:** FR-12, FR-13

## Epic 1: Client Gets an Instant Quote

A prospective client can register, log in, and receive a transparent, calculated insurance premium — the core vertical slice proving the architecture end-to-end (realizes PRD UJ-1). Includes the necessary project scaffolding as its first story, since nothing exists yet.

### Story 1.1: Project Scaffolding — Runnable Backend and Frontend Skeleton

As a developer,
I want a minimal but running Spring Boot backend and React frontend wired to local Postgres,
So that later stories have a foundation to build on.

**Acceptance Criteria:**

**Given** a clean checkout
**When** I run `docker compose up postgres` and start backend/frontend natively
**Then** the backend connects to Postgres and the frontend dev server reaches the backend (trivial health round-trip)
**And** Flyway's initial baseline migration succeeds against a clean database
**And** the repo layout matches the Architecture Spine's Structural Seed — only the `shared` module package exists yet (AD-1/AD-6)
**And** `.env.example` documents every required variable with no real values (NFR-2)

### Story 1.2: Client Self-Registration

As a prospective client,
I want to register with an email and password,
So that I can access the portal as a CLIENT.

**Acceptance Criteria:**

**Given** a new email
**When** I submit registration with valid email+password
**Then** a User is created with Role=CLIENT, password hashed, never plain text
**And** given an already-registered email, when I try again, then I get a specific duplicate-email error (AD-7 envelope, e.g. `AUTH_EMAIL_TAKEN`)
**And** given invalid input, when submitted, then field-level validation errors are enforced at both API and DB layer wherever a DB-layer equivalent exists — required fields, email uniqueness, length caps (NFR-4); password complexity and email format are API-layer only, since only the bcrypt hash is persisted

### Story 1.3: Login Issuing a Role-Bearing JWT

As any user,
I want to log in with email and password,
So that I receive a token proving my identity and Role.

**Acceptance Criteria:**

**Given** valid credentials
**When** I log in
**Then** I receive a JWT with user id + Role, multi-hour expiry (AD-3)
**And** given an incorrect password or unknown email, when I try to log in, then I get a generic "invalid credentials" error (no user enumeration)

### Story 1.4: Backend-Enforced Access to the Quote Endpoints

As the system,
I want every Quote endpoint to require a valid JWT and reject the wrong Role,
So that quote data is never reachable without authorization.

**Acceptance Criteria:**

**Given** a request with no token
**When** it hits a quote endpoint
**Then** it's rejected 401
**And** given a non-CLIENT token on a CLIENT-only action, when received, then it's rejected 403
**And** given a valid CLIENT token, when used, then the request proceeds normally

### Story 1.5: Quote Calculation With Transparent Breakdown

As an authenticated client,
I want to submit driver/vehicle/payment parameters and see the calculated premium and its full breakdown,
So that I understand what I'd pay and why.

**Acceptance Criteria:**

**Given** valid inputs (`driverAge`, `regionCode`, `engineCc`, `installments`)
**When** I submit a quote
**Then** I get the zone, base premium, age surcharge (if any), one-time premium, installment fee, total premium, and per-installment amount — every component the total is built from, not just the total
**And** given an unknown `regionCode`, an `engineCc` below 800, a `driverAge` under 18, or an `installments` value other than 1/2/4, when submitted, then I get a specific field-level error
**And** given any calculation, when performed, then all arithmetic uses exact decimal precision, never floating point (AD-5)
**And** this story implements `pricing`'s one application-layer service (AD-2/AD-6), using the tariff recorded in `_bmad-output/planning-artifacts/prds/prd-motor-insurance-quote-claims-portal-2026-08-23/addendum.md` ("Quote Engine — Milestone 1 tariff" section — driving experience and vehicle power are **not** rating factors in this model, unlike the superseded placeholder formula also kept in that file)

### Story 1.6: Quote Persistence and Retrieval

As an authenticated client,
I want my calculated quote saved and retrievable by ID,
So that I can revisit it.

**Acceptance Criteria:**

**Given** a successfully calculated quote
**When** it completes
**Then** it's persisted with inputs, factors, premium, and creation time
**And** given a quote ID I own, when I request it, then I get the full original quote
**And** given a quote ID belonging to another client, when I request it, then I'm rejected — never shown someone else's data

### Story 1.7: Client Quote Flow — Submit and See the Breakdown

As an authenticated client,
I want to submit my driver and vehicle details and see the calculated premium breakdown on screen,
So that I actually receive the quote Epic 1 promised (FR-8/FR-9), not just a backend response nobody can see.

**Acceptance Criteria:**

**Given** I am logged in as CLIENT and viewing my shell
**When** I submit valid driver_age/region_code/engine_cc/installments
**Then** I see the full breakdown from `POST /api/v1/quotes` (base premium, age surcharge, one-time premium, installment fee, total premium, installment amount) rendered on screen, not just a raw JSON response
**And** given invalid or rejected input (unknown region code, unsupported installment count, out-of-range values), when submitted, then I see the field-level error message from the API's error envelope (AD-7), consistent with how `RegisterForm`/`LoginForm` already surface errors

## Epic 2: Every Role Gets Their Own Workspace

Any of the four roles — including staff provisioned via seed data — logs in and lands on their own correctly role-guarded navigation shell, proving the role model is real, not cosmetic (realizes PRD UJ-2).

### Story 2.1: Seeded Staff Demo Accounts

As the team,
I want one demo account per staff Role (AGENT, LIQUIDATOR, ADMINISTRATOR) seeded automatically,
So that all four roles can be demonstrated without manual setup.

**Acceptance Criteria:**

**Given** a fresh database migration run
**When** it completes
**Then** exactly one User per staff Role exists
**And** given those seeded accounts, when I log in with their documented credentials (Story 1.3's login), then I authenticate successfully
**And** given the seed migration, when inspected, then passwords are hashed exactly as Story 1.2's path hashes them — no plaintext anywhere, including in the migration file itself

### Story 2.2: Role-Based Post-Login Routing

As any authenticated user,
I want to land automatically on the navigation shell matching my Role,
So that I never have to pick manually where I belong.

**Acceptance Criteria:**

**Given** a successful login as CLIENT
**When** redirected
**Then** I land on the Client shell (Epic 1's quote flow entry point)
**And** given a successful login as AGENT, LIQUIDATOR, or ADMINISTRATOR, when redirected, then I land on that role's own shell

### Story 2.3: Placeholder Screens for Agent, Liquidator, and Administrator

As a staff user,
I want to see a role-labeled placeholder screen for my area,
So that role separation is visibly proven even before real staff functionality exists.

**Acceptance Criteria:**

**Given** I am logged in as AGENT
**When** I view my shell
**Then** I see a static, Agent-labeled screen (e.g. "Agent workspace — coming soon"), non-interactive per architecture decision
**And** given the same for LIQUIDATOR or ADMINISTRATOR, when viewed, then I see my own distinctly labeled screen, not another role's

### Story 2.4: Frontend Route Guards Per Role

As the system,
I want the frontend to refuse rendering another role's screens even on manual navigation,
So that the UI never even attempts to show data a role shouldn't see.

**Acceptance Criteria:**

**Given** I am logged in as CLIENT
**When** I manually navigate to the Agent shell's URL
**Then** I am redirected back to my own shell, never shown Agent content
**And** given this holds symmetrically for every role against every other role's URL, when tested, then the same redirect applies — implemented as the single role-guard wrapper component (AD-10), not per-screen checks

## Epic 3: The Portal Speaks Bulgarian and English

Every screen delivered by Epic 1 and Epic 2 is fully usable in Bulgarian (default) or English, switchable at any time.

### Story 3.1: i18n Infrastructure and Language Toggle

As a user,
I want to switch the display language between Bulgarian and English at any time,
So that I can use the portal in my preferred language.

**Acceptance Criteria:**

**Given** a first-time, unauthenticated visit
**When** the app loads
**Then** it renders in Bulgarian by default
**And** given I toggle the language, when I do, then all text within this story's translated surface switches immediately — no reload, no navigation, no lost route state (full screen coverage is Story 3.2)
**And** given I reload the page after toggling, when the app loads again, then my selected language persists (client-side only, per AD-8)

### Story 3.2a: Screen Copy Translation

> Story 3.2 was split into 3.2a and 3.2b during planning (2026-08-29). The
> static copy is mechanical, roughly 38 strings; the error messaging carries
> the whole architectural decision. Reviewing them together would have buried
> the risky half under the rote half. Together they still deliver the original
> Story 3.2 acceptance criteria, unchanged in substance.

As a user,
I want every screen built so far (login, registration, quote form and breakdown, backend health, all four role shells) to read in my chosen language,
So that the app is not a Bulgarian header over an English product.

**Acceptance Criteria:**

**Given** every screen delivered in Epic 1 and Epic 2
**When** viewed with Bulgarian active
**Then** no English static copy is visible on any of them — headings, form labels, buttons, busy states, shell copy, and accessible names alike
**And** given English is active, when viewed, then the copy reads exactly as it did before this story
**And** given the existing test suites, when updated, then they assert the same behaviour through catalog-backed queries — never weakened to `data-testid` to dodge language-sensitive assertions

### Story 3.2b: Error and Validation Message Translation

As a user,
I want every failure message — backend error codes, field-level validation, and the tariff zone label — to appear in my chosen language,
So that no part of the experience falls back to English exactly when something has gone wrong.

**Acceptance Criteria:**

**Given** a backend error response
**When** it is displayed to the user
**Then** the frontend maps its `code` (AD-7) to a translated message — the raw backend `message` is never shown directly
**And** given a field-level validation failure, when shown beside the input, then it is a translated per-field message, never Bean Validation's English text
**And** given a new `code` is introduced by the backend, when it ships, then its i18n entry ships in the same change (AD-7's naming contract)
**And** given a successful quote in Bulgarian, when the breakdown renders, then the tariff zone is labeled from `zoneId`, leaving no English on the screen (PRD SM-4)

## Epic 4: The Team Demos From a Clean Machine

Anyone can clone the repository and bring up the entire working system (Epic 1–3's functionality) with one command, alongside a preserved native dev workflow — realizes PRD UJ-3, the mentor-demo success metric.

### Story 4.1: Backend and Frontend Dockerfiles

As a developer,
I want the backend and frontend each packaged as a Docker image,
So that they can run without a locally installed JDK or Node.

**Acceptance Criteria:**

**Given** the backend Dockerfile
**When** built
**Then** it produces a working image that starts the Spring Boot app and connects to Postgres over the compose network
**And** given the frontend Dockerfile, when built, then it produces a working image serving the built SPA, configured with `VITE_API_URL` (AD-9) at build/run time — never hardcoded

### Story 4.2: One-Command Full-Stack Startup

As anyone setting up the project on a new machine,
I want a single command to bring up the entire stack,
So that I can demo the product without manual setup.

**Acceptance Criteria:**

**Given** a clean checkout with only Docker installed and `.env` copied from `.env.example`
**When** I run `docker compose up`
**Then** Postgres, backend, and frontend all start, migrations apply automatically, and the app is usable end-to-end (Epic 1–3's flows) within a couple of minutes on first run (near-instant thereafter)
**And** given this same command, when the browser opens the frontend's exposed port, then it successfully calls the backend via the browser-reachable `VITE_API_URL` — closing exactly the gap the architecture reviewer flagged

### Story 4.3: Local Dev Workflow Preserved Alongside Docker

As a developer actively iterating,
I want to run backend and frontend natively against a containerized database only,
So that I keep a fast dev loop without full-stack rebuilds.

**Acceptance Criteria:**

**Given** `docker compose up postgres`
**When** only the database container is running
**Then** `mvn spring-boot:run` and `npm run dev` both start successfully and talk to it exactly as Story 1.1 established
**And** given this dual-mode setup, when documented in `backend/README.md`/`frontend/README.md`, then both modes are independently runnable with no undocumented manual step
