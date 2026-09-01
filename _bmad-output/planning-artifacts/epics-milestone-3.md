---
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-motor-insurance-quote-claims-portal-milestone-3-2026-08-31/prd.md
  - _bmad-output/planning-artifacts/architecture/architecture-milestone-3-2026-08-31/ARCHITECTURE-SPINE.md
  - _bmad-output/planning-artifacts/ux-designs/ux-motor-insurance-quote-claims-portal-milestone-3-2026-08-31/DESIGN.md
  - _bmad-output/planning-artifacts/ux-designs/ux-motor-insurance-quote-claims-portal-milestone-3-2026-08-31/EXPERIENCE.md
---

# Motor Insurance Quote & Claims Portal — Milestone 3 - Epic Breakdown

## Overview

This document provides the epic and story breakdown for Milestone 3 (From Quote to Policy), decomposing the Milestone 3 PRD, its architecture spine, and its UX spines into implementable stories. It is scoped separately from `epics.md` (Milestone 1) and `epics-milestone-2.md` (Milestone 2), neither of which it modifies.

FR IDs are the PRD's own `FR-M3-*` identifiers, carried through unchanged — they are globally unambiguous, so unlike Milestone 2 there is no local renumbering. Epic numbering continues the project sequence: Epics 1–5 are complete, so Milestone 3 is **Epics 6–9**.

## Requirements Inventory

### Functional Requirements

FR-M3-01: Client quote history — a CLIENT lists their own quotes, newest first, with enough to choose between them.
FR-M3-02: Offer validity window — every quote carries a `validUntil` fixed at calculation time.
FR-M3-03: Quote status lifecycle — `CALCULATED` → `ACCEPTED` | `EXPIRED`; backend-controlled, never caller-supplied. `CANCELLED` reserved, no producer.
FR-M3-04: Coverage start date — the client chooses when coverage begins; it determines the policy period.
FR-M3-05: Accept a quote and issue a policy — one policy per quote, in one transaction.
FR-M3-06: Unique policy number — `MI-{year}-{8 digits}`, sequence-allocated.
FR-M3-07: Immutable policy snapshot — the policy stores its own copy of holder, vehicle, and breakdown.
FR-M3-08: Policyholder and vehicle identity captured at acceptance (holder name, registration number or VIN).
FR-M3-09: Policy status — `SCHEDULED` | `ACTIVE` | `EXPIRED`, derived from the coverage dates.
FR-M3-10: My policies — a CLIENT lists their own policies and opens one for its full detail.
FR-M3-11: An expired token is not a session — the frontend treats a token past `exp` as logged out.
FR-M3-12: A 401 ends the session cleanly — clears the token and returns the user to login.
FR-M3-13: Authenticated visitors hitting `/login` or `/register` are redirected to their role home.
FR-M3-14: OpenAPI/Swagger documentation (should-have).
FR-M3-15: Documentation and legacy-CSS cleanup (should-have).
FR-M3-16: Bonus-malus class as a rating factor, with its coefficients as seeded reference data.

### NonFunctional Requirements

NFR-1: Money stays `BigDecimal`/`NUMERIC`, HALF_UP to 2 decimals (M1 AD-5). A policy's premium is copied from the quote, never recalculated.
NFR-2: Every new read path is owner-scoped **in the query**; a resource belonging to someone else returns 404, never 403 (M3 AD-10).
NFR-3: New error codes are namespaced `MODULE_REASON` and ship with their `bg` + `en` translations in the same change; CI's error-code contract check is the gate (M1 AD-7, M3 AD-11).
NFR-4: Every new screen and message renders in Bulgarian and English with no untranslated fallback (M1 AD-8).
NFR-5: Every new screen is built from the Milestone 2 component library and is usable from ~375px up. No new one-off styling.
NFR-6: The acceptance transaction carries a Testcontainers integration test including the concurrent double-accept case (BA §16.2).
NFR-7: A story's `sprint-status.yaml` key moves to `done` when its PR merges (Epic 5 retro item 40).
NFR-8: The bonus-malus scale is presented everywhere as the project's own demo data — never as official or regulatorily determined Bulgarian market values (M3 PRD FR-M3-16 provenance constraint, M3 AD-8).

### Additional Requirements (from the Milestone 3 Architecture Spine)

- AD-1: `quote.application` owns the acceptance transaction and calls exactly one `policy.application` entry point with a fully-formed issuance command. `policy` never reads `quote` — no import, no repository, no query. Dependency is one-directional.
- AD-2: Acceptance is `POST /api/v1/quotes/{id}/accept` (served by `quote.api`) and returns the created policy. Policy reads are `GET /api/v1/policies` and `GET /api/v1/policies/{id}` (served by `policy.api`).
- AD-3: No `status` column on `quotes` or `policies` — status is derived on read, once per module, in the domain layer.
- AD-4: A policy copies every value it displays. `policies.quote_id` is the idempotency key only and is never dereferenced; no JPA association `Policy → Quote`.
- AD-5: `policies.quote_id UNIQUE` is the sole idempotency authority, and acceptance is **genuinely idempotent** — the first success creates and returns the policy (201); every later call for an already-accepted quote returns that same existing policy (200), never an error. On a race, `saveAndFlush` inside try/catch catches `DataIntegrityViolationException` from the unique constraint and the loser re-reads and returns the winner's policy (200) instead of propagating an error. `QUOTE_ALREADY_ACCEPTED` does not exist as an error code (AD-11) — a 409 on replay would be duplicate-detection, not idempotency.
- AD-6: Business zone `Europe/Sofia`, injected `Clock`, no production call to `Instant.now()`/`LocalDate.now()`. `LocalDate`/`DATE` for business dates, `Instant`/`TIMESTAMPTZ` for events. Boundaries inclusive both ends; `coverage_end = coverage_start.plusYears(1).minusDays(1)`. `coverage_start` is an acceptance input, not a quote input.
- AD-7: Policy number from a global PostgreSQL sequence + `UNIQUE`; no per-year reset; gaps expected.
- AD-8: Bonus-malus is tariff data owned by `pricing`. Order fixed: `one_time = round((base + age_surcharge) × factor, 2)`, then `+ installment_fee`. Unknown class fails closed.
- AD-9: Every new column on an existing table is `NOT NULL` with its backfill in the same migration; only `quotes.accepted_at` is nullable. Money backfills are arithmetically neutral.
- AD-10: Ownership in the query; 404 not 403; `@PreAuthorize("hasRole('CLIENT')")` on every M3 endpoint; customer id from the `SecurityContext`, never a request parameter.
- AD-11: New codes — `QUOTE_EXPIRED` (409), `POLICY_NOT_FOUND` (404), `PRICING_UNKNOWN_BONUS_MALUS_CLASS` (400). `QUOTE_ALREADY_ACCEPTED` is deliberately not a code this milestone — see AD-5.
- AD-12: List endpoints return a bare ordered JSON array of the same DTO the detail endpoint returns — no envelope, no pagination.
- AD-13: The quote response grows additively; no existing field is renamed or repurposed.
- Stack addition (should-have): `springdoc-openapi`, **version not yet verified against Spring Boot 4.1.1** — verify before binding; FR-M3-14 defers rather than pinning the framework backwards.
- Inherited and binding: M1 AD-1..AD-11, M2 AD-1..AD-6.

### UX Design Requirements

UX-DR1: **`Badge` component** — the one new primitive. Four AD-6 status variants, control shape, small text, tinted background. Built like the other primitives (`cva`, native `<span>`, tokens only). Never interactive.
UX-DR2: **Status vocabulary** — four quote states and three policy states, each with one label per language and one variant, fixed so no screen invents an eighth. An expired *policy* renders neutral (a full term is the successful outcome); an expired *quote* renders `danger` (a lost opportunity).
UX-DR3: **Route additions** — `/quotes`, `/quotes/:id`, `/policies`, `/policies/:id`, all under the existing CLIENT `RoleGuard`. "My quotes" and "My policies" join the header nav; the nav wraps on a phone rather than collapsing behind a disclosure control.
UX-DR4: **List rows are `Card` + `Badge`, the whole row one link target** — not a card with a "View" button inside it. Single-column cards at every width; no table, no horizontal scroller.
UX-DR5: **Acceptance is a screen section, not a modal** — below the breakdown, in reading order: what you are buying → who you are → when it starts → commit. One `primary` button, labelled with its outcome.
UX-DR6: **Four states on every new surface** — loading (standalone or in-button `Spinner`), empty (named cause + the one action that fills it, never an error tone, never chained to a second empty screen), error (`Alert` `danger` keyed off the backend `code`, retry on load, values preserved on submit), and the two content states below.
UX-DR7: **Expired and accepted quotes replace the acceptance form rather than disabling it**, and keep the breakdown visible. Expired offers a fresh quote; accepted links to the resulting policy.
UX-DR8: **Expiry race** — a quote expiring while its detail screen is open is refused server-side; the client re-reads and re-renders it as expired in the same beat. The UI never asserts acceptability from a stale fetch.
UX-DR9: **Native date input** for coverage start — no custom picker. Past dates refused with a field-level message.
UX-DR10: **Bonus-malus is a select** of five word-labelled classes with plain-language meanings, defaulting to `NEUTRAL`, carrying the demo-data note inline.
UX-DR11: **Money and dates** — dates in the active language's convention; money identical in both languages with an explicit `EUR` from the API, never locale-derived.
UX-DR12: **Typography of value** — premium totals, policy numbers, and coverage periods render heavier and larger than their labels.
UX-DR13: **Header/`<main>` alignment fix** — the header's `max-w-5xl` inner container versus `<main>`'s `max-w-2xl`, a carried-over deferral that the new list screens make more visible.
UX-DR14: **No modal, no new color, no new radius, no motion, no optimistic UI.** The palette is closed for this milestone.
UX-DR15: **Accessibility floor, not an audit** — semantic elements, real labels, `role="alert"`, unsuppressed focus rings, status never signalled by colour alone, 44px tap targets below `sm`. All inherited free from the primitives; the deferred G-4 a11y items stay deferred.

### FR Coverage Map

FR-M3-16: Epic 6 — Story 6.1 (Bonus-malus rating factor)
FR-M3-02: Epic 6 — Story 6.2 (Offer validity and quote status)
FR-M3-03: Epic 6 — Story 6.2
FR-M3-01: Epic 6 — Story 6.3 (My Quotes)
FR-M3-11: Epic 7 — Story 7.1 (Server-Validated Authentication State)
FR-M3-12: Epic 7 — Story 7.1
FR-M3-13: Epic 7 — Story 7.2 (Authenticated visitors skip the auth screens)
FR-M3-04: Epic 8 — Story 8.1 (backend contract), Story 8.2 (client input)
FR-M3-05: Epic 8 — Story 8.1 (Accept a quote and issue a policy), Story 8.2 (acceptance UI)
FR-M3-06: Epic 8 — Story 8.1
FR-M3-07: Epic 8 — Story 8.1, verified visually in Story 8.3
FR-M3-08: Epic 8 — Story 8.1 (stored), Story 8.2 (captured)
FR-M3-09: Epic 8 — Story 8.3 (My Policies and policy details)
FR-M3-10: Epic 8 — Story 8.3
FR-M3-14: Epic 9 — Story 9.1 (should-have)
FR-M3-15: Epic 9 — Story 9.2 (should-have)

UX-DR1/UX-DR2 land in Story 6.3 (first consumer). UX-DR3 spans 6.3 and 8.3. UX-DR13 lands in Story 6.3. Every other UX-DR is a cross-cutting constraint on the story that renders the surface it governs.

## Epic List

### Epic 6: A Quote You Can Come Back To

A quote stops being a disposable calculation. It gets the rating factor the assignment asks for, a lifetime, a status, and a list the client can find it in again. Delivers FR-M3-16, FR-M3-02, FR-M3-03, FR-M3-01.

### Epic 7: A Session That Fails Safely

Before a click can issue a contract, the browser and the backend must agree on whether the user is logged in. Three narrow fixes, promoted from the deferred backlog because Epic 8 raises their stakes. Delivers FR-M3-11, FR-M3-12, FR-M3-13.

### Epic 8: Accept a Quote, Get a Policy

The milestone's core transaction: a valid quote becomes a real policy with a number, a coverage period, and an immutable record of what was agreed. Delivers FR-M3-04 … FR-M3-10.

### Epic 9: Documented and Tidy *(should-have)*

The two cleanups that make the milestone legible to a reviewer and close Milestone 2's own open acceptance gap. Delivers FR-M3-14, FR-M3-15.

**Sequencing.** 6 → 7 → 8 → 9. Epic 7 sits deliberately before Epic 8: the contract-issuing screen should never be live while the UI can still believe an expired token is a session. Epic 9 is should-have and yields first if time runs short.

---

## Epic 6: A Quote You Can Come Back To

A quote stops being a disposable calculation. It gets the rating factor the assignment asks for, a lifetime, a status, and a list the client can find it in again.

### Story 6.1: Bonus-Malus Rating Factor

As a client requesting a quote,
I want my bonus-malus class to affect the premium I am quoted,
So that the price reflects my claims history the way a real motor policy does.

**Note on scope:** deliberately one end-to-end story rather than a backend/frontend split. The class is a required input, so a backend-only increment would break the existing quote form the moment it landed.

**Acceptance Criteria:**

**Given** a new reference table holding the five bonus-malus classes and their coefficients, seeded by migration (AD-8)
**When** a coefficient needs correcting
**Then** it is a data change, not a code change — no coefficient appears as a literal in Java
**And** given the migration's header, when a reader opens it, then it states plainly that these are the project's own demo coefficients inherited from the team's prototype and **not** official or regulatorily determined values for the Bulgarian market (NFR-8)

**Given** a quote request carrying a bonus-malus class
**When** `pricing` calculates the premium
**Then** `one_time_premium = round((base_premium + age_surcharge) × bonus_malus_factor, 2)` and `total_premium = one_time_premium + installment_fee` — the factor never scales the installment fee (AD-8)
**And** every value stays `BigDecimal`, HALF_UP to 2 decimals (NFR-1)

**Given** a request with an unknown or absent class
**When** it is validated
**Then** it is rejected as a field-level error with code `PRICING_UNKNOWN_BONUS_MALUS_CLASS` (400) — never silently defaulted to neutral (AD-8, AD-11)
**And** the code ships with its `bg` and `en` translations in the same change, and CI's error-code contract check passes (NFR-3)

**Given** the `quotes` table gaining its bonus-malus columns
**When** the migration runs against a database holding existing quotes
**Then** every existing row is backfilled to the neutral class with factor `1.000`, the columns are `NOT NULL`, and **every persisted premium is byte-identical to what it was before the migration** (AD-9)

**Given** the quote response
**When** it is returned
**Then** the bonus-malus class and factor appear as new fields, every existing field keeps its name and meaning, and the Milestone 2 breakdown component still renders without modification (AD-13)

**Given** the quote form
**When** a client fills it in
**Then** bonus-malus is a select of five word-labelled classes with their plain-language meanings, defaulting to `NEUTRAL`, carrying an inline note that the scale is the portal's own demo model (UX-DR10, NFR-8)
**And** the factor appears as its own line in the rendered breakdown, translated in both languages (UX-DR11, NFR-4)

### Story 6.2: Offer Validity and Quote Status

As a client,
I want each quote to show how long it stays valid and whether I have already acted on it,
So that I know which of my quotes I can still accept.

**Acceptance Criteria:**

**Given** the `quotes` table gaining `valid_until` (`DATE`) and `accepted_at` (`TIMESTAMPTZ`, nullable)
**When** a quote is calculated
**Then** `valid_until` is set to 14 days from the calculation date, resolved from a single configured offer-validity period, not a literal at a call site (FR-M3-02)
**And** existing rows are backfilled in the same migration under an explicit, documented rule, `valid_until` is `NOT NULL`, and `accepted_at` is the one permitted nullable column because null carries the domain meaning "not accepted" (AD-9)

**Given** no `status` column exists on `quotes` (AD-3)
**When** a quote is read
**Then** its status is derived: `accepted_at IS NOT NULL` → `ACCEPTED`; else `today > valid_until` → `EXPIRED`; else `CALCULATED`
**And** the derivation is implemented once, in the domain layer, and every read path uses it
**And** `CANCELLED` exists in the status type with no producer, no persisted representation, and no branch in the derivation

**Given** the business zone `Europe/Sofia` and an injected `Clock` (AD-6)
**When** any expiry comparison runs
**Then** no production code calls `Instant.now()`, `LocalDate.now()`, or `LocalDate.now(ZoneId.systemDefault())`
**And** a quote is acceptable **on** its `valid_until` date — the boundary is inclusive
**And** a test proves the boundary by fixing the clock to `valid_until` and to the following day

**Given** the quote response
**When** it is returned
**Then** it additively carries the validity date, the derived status, and the acceptance timestamp; no existing field changes (AD-13)

### Story 6.3: My Quotes — List and Detail

As a client,
I want to see the quotes I have already requested and open any one of them,
So that I can compare them and come back to the one I want.

**Acceptance Criteria:**

**Given** `GET /api/v1/quotes`
**When** an authenticated CLIENT calls it
**Then** it returns a bare JSON array of the same DTO the detail endpoint returns, ordered newest-first, with no envelope and no pagination (AD-12)
**And** the query is owner-scoped in the repository method itself — a second client's quotes never appear under any parameter (NFR-2, AD-10)
**And** the endpoint carries `@PreAuthorize("hasRole('CLIENT')")` and takes the customer id from the `SecurityContext`, never from a request parameter (AD-10)

**Given** a new `Badge` component in `frontend/src/components/ui/` (UX-DR1)
**When** it renders a status
**Then** it uses `cva` variants over the four AD-6 status tokens, renders a native `<span>`, is never interactive, and its **text carries the meaning** — the status survives being read in grayscale (UX-DR1, UX-DR15)

**Given** the routes `/quotes` and `/quotes/:id` nested under the existing CLIENT `RoleGuard` (UX-DR3)
**When** an anonymous or wrong-role visitor requests either
**Then** the existing guard behaviour applies unchanged — no new guard logic is written
**And** "My quotes" appears in the header nav for an authenticated CLIENT, and the nav wraps rather than collapsing behind a disclosure control on a phone

**Given** the My Quotes screen
**When** it renders
**Then** each quote is a `Card` + `Badge` row showing total premium, vehicle, date, and status, and **the whole row is one link target** — not a card with a button inside it (UX-DR4)
**And** rows are a single column at every width from 375px up, with no table and no horizontal scroller (UX-DR4, NFR-5)
**And** the four states are all handled: loading, empty ("Calculate your first quote", pointing at the quote form, in an informational not an error tone), error (`Alert` `danger` keyed off the backend `code`, with a retry), and populated (UX-DR6)

**Given** the quote detail screen
**When** a client opens a quote
**Then** it renders the full breakdown — including the bonus-malus line — reusing the existing breakdown presentation rather than a new one
**And** an **expired** quote still renders its breakdown in full, with an explanation and a "Calculate a new quote" action in place of any acceptance affordance (UX-DR7)
**And** an **accepted** quote renders the same way with a link to its policy instead — implemented in Story 8.3 once policies exist, and stubbed here as the accepted state with no link

**Given** the header and `<main>` containers (UX-DR13)
**When** the new list screens render
**Then** the header's inner container and `<main>` share a left edge — the carried-over `max-w-5xl` versus `max-w-2xl` misalignment is fixed

**Given** every string this story adds
**When** the language is switched
**Then** all of it renders in both Bulgarian and English with no untranslated fallback, under the new `quotes.*` namespace, and dates follow the active language's convention while money renders identically in both with an explicit `EUR` from the API (NFR-4, UX-DR11)

---

## Epic 7: A Session That Fails Safely

Before a click can issue a contract, the browser and the backend must agree on whether the user is logged in.

### Story 7.1: Server-Validated Authentication State

As a client who left the tab open overnight,
I want the portal to notice my session ended rather than pretending it is still live,
So that I am not left pressing a button that silently does nothing.

**Acceptance Criteria:**

**Given** a stored token whose `exp` claim has passed
**When** any guarded route or the header nav evaluates the session
**Then** the user is treated as logged out — `RoleGuard` redirects to `/login` and the header shows anonymous navigation (FR-M3-11)
**And** the check reads `exp` only; signature verification stays the backend's job and no verification is attempted client-side

**Given** any API call returning 401
**When** the shared API client handles the response
**Then** it clears the stored token and returns the user to login (FR-M3-12)
**And** this is handled once in the shared client, not per screen — no screen carries its own 401 branch

**Given** the existing token-decoding and role-resolution helpers
**When** this story lands
**Then** the duplicated inline decode in `LoginForm` is folded into the shared helper rather than a fourth copy being added (Epic 2 retro item 14, escalated by Epic 3 retro item 35)

**Given** the new behaviour
**When** tests run
**Then** an expired-token case, a valid-token case, and a 401-response case each have a test, and no existing auth test is weakened to accommodate them

### Story 7.2: Authenticated Visitors Skip the Auth Screens

As a logged-in user,
I want `/login` and `/register` to take me to my own workspace,
So that I never land on a sign-in form I do not need.

**Acceptance Criteria:**

**Given** an authenticated visitor with a valid, unexpired token
**When** they navigate to `/login` or `/register`
**Then** they are redirected to their own role home via the existing `roleHome()` helper (FR-M3-13)
**And** an anonymous visitor, or one holding an expired token, still reaches both screens normally

**Given** a visitor who is already logged in as one identity and logs in as another
**When** the second login completes
**Then** they land on the second identity's role home, with no stale role from the first — the identity-swap path has a test (Epic 2 retro item 13)

**Given** `/health`
**When** anyone visits it
**Then** it remains unguarded and unaffected, as it is today

---

## Epic 8: Accept a Quote, Get a Policy

A valid quote becomes a real policy with a number, a coverage period, and an immutable record of what was agreed.

### Story 8.1: Accept a Quote and Issue a Policy (Backend)

As a client,
I want accepting my quote to produce exactly one policy,
So that I get the contract I asked for and never a duplicate.

**Acceptance Criteria:**

**Given** a new `policy` module created by this story (M1 AD-6)
**When** its dependencies are inspected
**Then** `quote.application` owns the acceptance transaction and calls exactly one `policy.application` entry point with a fully-formed issuance command, and **`policy` imports no quote type, holds no reference to `QuoteRepository`, and issues no query against `quotes`** (AD-1)

**Given** `POST /api/v1/quotes/{id}/accept`, served by `quote.api` (AD-2)
**When** an authenticated CLIENT accepts their own valid, not-yet-accepted quote with a coverage start date, holder name, and vehicle registration or VIN
**Then** one transaction validates ownership and expiry, sets `accepted_at`, allocates the number, and inserts the policy, returning the created policy representation with **201** (FR-M3-05, FR-M3-08)
**And** a coverage start date in the past is rejected with a field-level validation error (FR-M3-04)
**And** identity validation is format-level only — no external registry lookup

**Given** a quote that is not the caller's
**When** acceptance is attempted
**Then** the response is **404, not 403** — a resource belonging to someone else is indistinguishable from one that does not exist (NFR-2, AD-10)

**Given** an expired quote
**When** acceptance is attempted
**Then** the response is 409 with code `QUOTE_EXPIRED` (AD-11) — this stays the one genuine conflict this endpoint reports

**Given** a quote that has already been accepted (`accepted_at IS NOT NULL`)
**When** acceptance is attempted again — the endpoint is **genuinely idempotent, not merely duplicate-protected**
**Then** the application-level pre-check finds the existing policy by `quote_id`, attempts no insert, and returns **that same policy with 200** — never an error and never a second policy (AD-5)
**And** `QUOTE_ALREADY_ACCEPTED` does not exist as an error code; a 409 here would be duplicate-detection, not idempotency, and is explicitly rejected as the wrong contract (AD-5, AD-11)

**Given** `policies.quote_id UNIQUE` (AD-5)
**When** two accept requests for the same quote arrive concurrently and both pass the pre-check together
**Then** exactly one policy is inserted; the insert is flushed inside its own try/catch so `DataIntegrityViolationException` from the unique constraint surfaces where it can be handled, matching the pattern `QuoteService.calculate` already uses
**And** the loser catches that exception, re-reads the policy by `quote_id` (now visible, the winner having committed), and **returns it with 200** — the same response shape as the uncontended replay path, not an error
**And** the winner returns 201; **the guarantee that exactly one policy exists is the database constraint, never the application-level pre-check**
**And** a Testcontainers integration test exercises the concurrent double-accept case explicitly, asserting one 201 + one 200 (or two 200s if both lose to a third writer), never two 201s and never an error response (NFR-6)

**Given** any failure at any step
**When** the transaction rolls back
**Then** neither an accepted quote nor a policy remains

**Given** the policy number (FR-M3-06, AD-7)
**When** a policy is issued
**Then** the numeric part comes from a dedicated PostgreSQL sequence, the format is `MI-{year}-{8 digits, zero-padded}` with the year resolved in the business zone, and `policy_number` carries a `UNIQUE` constraint
**And** the sequence is global and never resets per year; gaps from rolled-back transactions are expected and acceptable

**Given** the policy snapshot (FR-M3-07, AD-4)
**When** a policy is stored
**Then** it holds its own copy of the holder identity, the vehicle identity, the rating inputs, and every breakdown component — and `policies.quote_id` exists solely as the idempotency key, is never dereferenced, and has no JPA association to `Quote`
**And** a test mutates the source tariff data and re-reads the policy, proving every stored figure is unchanged
**And** the premium is copied from the quote, never recalculated (NFR-1)

**Given** coverage dates (AD-6)
**When** a policy is issued
**Then** `coverage_end = coverage_start.plusYears(1).minusDays(1)` — inclusive at both ends — and both are `LocalDate`/`DATE` while `issued_at` is `Instant`/`TIMESTAMPTZ`

### Story 8.2: Accepting a Quote From the Detail Screen

As a client looking at a quote I like,
I want to enter who I am and which car it is, and commit,
So that I walk away holding a policy.

**Acceptance Criteria:**

**Given** the quote detail screen from Story 6.3
**When** the quote is valid and unaccepted
**Then** an acceptance section appears **below the breakdown**, in reading order — what you are buying → who you are → when it starts → commit — and **not** in a modal (UX-DR5)
**And** the form is single-column at every width, built from `FormField` + `Input` + `Button`, with `FormField` owning every field error (M2 AD-5, NFR-5)

**Given** the coverage start field
**When** a client picks a date
**Then** it is a native date input with no custom picker, and a past date is refused with a field-level message (UX-DR9, FR-M3-04)

**Given** the commit control
**When** the client presses it
**Then** it is the single `primary` button on the screen and its label names the outcome — not a generic "Submit" or "Confirm" (UX-DR5)
**And** while in flight it keeps its label, disables, and shows an inline `Spinner`, matching the Story 5.6 pattern (UX-DR6)
**And** nothing is shown as done before the backend confirms it — no optimistic UI anywhere (UX-DR14)

**Given** a double-press
**When** two submissions are attempted
**Then** the UI guard prevents the second where it can, and the outcome is one policy and one success screen regardless — the guarantee is Story 8.1's constraint, not this guard

**Given** a failed acceptance
**When** the error returns
**Then** it renders as an `Alert` `danger` keyed off the backend `code`, never raw backend prose, and **every value the client entered stays in place** (UX-DR6)

**Given** a quote that expires while its detail screen is open (UX-DR8)
**When** the client presses accept
**Then** the server refuses, the client re-reads the quote, and the screen re-renders as expired with the refusal explained in the same beat — the UI never asserts acceptability from a stale fetch

**Given** a session that has expired while the screen was open
**When** the client presses accept
**Then** Story 7.1's behaviour applies — token cleared, returned to login — and **nothing was half-created**: no stray policy exists and the quote is exactly as it was

**Given** a successful acceptance
**When** the policy is issued
**Then** the client is taken to that policy's detail screen, not back to a list

**Given** this milestone's second and third new forms
**When** they are built
**Then** the duplicated `cancelledRef` / `FormPhase` / double-submit-guard logic is extracted into a shared hook rather than copied a fourth time (Epic 5 retro item 41), with behaviour unchanged and the existing form tests still passing

**Given** every string this story adds
**When** the language is switched
**Then** all of it renders in both languages with no untranslated fallback (NFR-4)

### Story 8.3: My Policies — List and Detail

As a client,
I want to see the policies I hold and open one to check what I am covered for,
So that I can confirm my cover without asking anyone.

**Acceptance Criteria:**

**Given** `GET /api/v1/policies` and `GET /api/v1/policies/{id}`, served by `policy.api` (AD-2)
**When** an authenticated CLIENT calls them
**Then** the list returns a bare JSON array of the same DTO the detail endpoint returns, ordered newest-first, unpaginated and unwrapped (AD-12)
**And** both are owner-scoped in the query, return 404 for someone else's policy, and carry `@PreAuthorize("hasRole('CLIENT')")` (NFR-2, AD-10, AD-11)

**Given** no `status` column on `policies` (AD-3)
**When** a policy is read
**Then** its status is derived from the coverage dates — `today < coverage_start` → `SCHEDULED`; `today > coverage_end` → `EXPIRED`; else `ACTIVE` — using the injected clock and business zone (FR-M3-09, AD-6)
**And** the derivation is implemented once, in `policy`'s domain layer

**Given** the routes `/policies` and `/policies/:id` under the CLIENT `RoleGuard`, and "My policies" in the header nav (UX-DR3)
**When** the list renders
**Then** each policy is a `Card` + `Badge` row showing the policy number, vehicle, and status, the whole row one link target, single-column at every width (UX-DR4)
**And** an **expired policy renders in the neutral muted treatment, not `danger`** — a policy running its full term is the successful outcome, unlike an expired quote (UX-DR2)
**And** the empty state reads "Accept one of your quotes to get a policy" and points at My Quotes — **unless the client also has no quotes, in which case it points at the quote form instead**, so nobody is sent to a second empty screen (UX-DR6)

**Given** the policy detail screen
**When** a client opens a policy
**Then** it shows the number, the coverage period, the premium, the vehicle, and the full breakdown it was issued with — presented **identically** to the quote's breakdown, so a client comparing the two can see they match (FR-M3-10, FR-M3-07)
**And** the policy number, premium total, and coverage period render heavier and larger than their labels (UX-DR12)

**Given** an accepted quote (completing Story 6.3's stubbed state)
**When** its detail screen renders
**Then** the acceptance section is replaced by a link to the resulting policy (UX-DR7)

**Given** loading, empty, and error states on both new surfaces
**When** each occurs
**Then** it is handled per UX-DR6, and no state renders as a blank screen

**Given** every string this story adds
**When** the language is switched
**Then** all of it renders in both languages under the new `policies.*` namespace, with dates in the active language's convention and money identical in both with an explicit `EUR` (NFR-4, UX-DR11)

---

## Epic 9: Documented and Tidy *(should-have)*

Neither story here is release-blocking. If time runs short, this epic yields first.

### Story 9.1: OpenAPI Documentation *(should-have)*

As a reviewer or a teammate,
I want the REST API browsable as generated documentation,
So that I can see the contract without reading controllers.

**Acceptance Criteria:**

**Given** `springdoc-openapi` as a candidate dependency
**When** it is added
**Then** its compatibility with Spring Boot 4.1.1 is **verified against the current release, not assumed** — and if no compatible release exists, this story is deferred rather than pinning the framework backwards (FR-M3-14)

**Given** a compatible version
**When** the app runs
**Then** every `/api/v1` endpoint appears in the generated documentation with its request and response shapes
**And** wherever the bonus-malus scale is described, the description states it is the project's own demo data, not official Bulgarian market values (NFR-8)

### Story 9.2: Documentation and Legacy-CSS Cleanup *(should-have)*

As a reviewer opening this repository for the first time,
I want the README to describe what actually exists,
So that I am not misled about the project's state.

**Acceptance Criteria:**

**Given** the root README's "Status" section, currently three epics stale
**When** it is updated
**Then** it reflects the real state of the project (FR-M3-15)
**And** it records that driving experience is deliberately not a rating factor — a documented choice, not an oversight
**And** it states that the bonus-malus scale is the project's own demo model, not official or regulatorily determined Bulgarian market values (NFR-8)

**Given** the surviving `@layer legacy` rules in `frontend/src/index.css` (Epic 5 retro items 36–38)
**When** they are retired
**Then** every rule with no live consumer is deleted, the survivors are migrated to tokens or component props, and the `#e2e8f0` border above the quote breakdown no longer comes from a hardcoded hex
**And** the stale legacy-layer comment is corrected to state what the block actually still provides
**And** Milestone 2's FR-1 ("no hardcoded hex in touched screens") is then literally true, closing that milestone's open acceptance gap

**Given** the full existing test suite
**When** it runs after the cleanup
**Then** it passes unmodified — this story changes no behaviour
