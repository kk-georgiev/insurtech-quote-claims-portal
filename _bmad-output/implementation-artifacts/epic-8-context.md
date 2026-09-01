# Epic 8 Context: Accept a Quote, Get a Policy

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic delivers the milestone's core transaction: a client's valid quote becomes a real policy — a contract with a number, a coverage period, and an immutable record of exactly what was agreed. Everything before it produced numbers a client could look at; this is the first thing that produces something they hold. It is also where correctness stops being cosmetic: a double-clicked button must not issue two contracts, an expired offer must not be honoured, and a policy must never silently change because a tariff row was edited later. The epic spans a new backend module, the acceptance UI on the existing quote detail screen, and the client's own policy list.

## Stories

- Story 8.1: Accept a Quote and Issue a Policy (Backend)
- Story 8.2: Accepting a Quote From the Detail Screen
- Story 8.3: My Policies — List and Detail

## Requirements & Constraints

- A CLIENT accepts one of **their own** valid quotes; the system issues **exactly one** policy in a single transaction. Acceptance captures a coverage start date, the holder's name, and the vehicle's registration number or VIN — identity is validated format-only, with no external registry lookup.
- A coverage start date in the past is refused as a field-level validation error. The coverage period is 12 months, derived from the start date by one documented rule.
- Every policy carries a unique, human-readable number of the form `MI-{year}-{8 digits}`. Uniqueness comes from a database sequence plus a `UNIQUE` constraint — no path reads the highest number and increments it.
- A policy stores its own copy of the holder details, vehicle details, and the full premium breakdown as of issuance. Editing the source data or the tariff afterwards leaves an issued policy's figures byte-identical. The premium is copied, never recalculated.
- Ownership is enforced inside the query, and a resource belonging to someone else is indistinguishable from one that does not exist: **404, never 403**. 403 stays reserved for a role mismatch. Every endpoint in this epic is CLIENT-only and takes the customer id from the security context, never from a request parameter.
- An expired quote cannot be accepted — that is the one genuine conflict (409) this epic reports.
- A client can list their own policies and open one to see its number, coverage period, premium, vehicle, and the breakdown it was issued with.
- Money stays exact-decimal, HALF_UP to two decimals. Every new backend error code ships with its Bulgarian and English translations in the same change — CI's error-code contract check is the gate. Every new screen string renders in both languages with no untranslated fallback.
- A double-clicked accept produces exactly one policy, demonstrable against a live database — a named milestone success measure, not a nice-to-have.

## Technical Decisions

- **Module ownership.** The acceptance use case lives in `quote`'s application layer and calls exactly one entry point on the new `policy` module's application layer, passing a fully-formed issuance command with every value already resolved. `policy` never reads `quote`: no imported quote type, no repository reference to quotes, no query against that table. The dependency is one-directional so a later agent-issues-on-behalf-of-a-client flow reuses `policy` unchanged.
- **REST surface.** Acceptance is a command on a quote — `POST /api/v1/quotes/{id}/accept`, served by `quote`'s api layer — and it returns the created policy representation, so one round trip both commits and renders the result. Policy reads live under `/api/v1/policies`, served by `policy`'s api layer.
- **Idempotency, not duplicate-detection.** The unique constraint on the policy's quote reference is the sole authority that one policy exists per quote; the application-level pre-check is an optimization for the uncontended path and must never be presented as the guarantee. The first call creates and returns 201. A replay for an already-accepted quote returns **that same policy with 200** — never an error, and no `QUOTE_ALREADY_ACCEPTED` code exists. Under a genuine race the losing insert is flushed inside its own try/catch so the constraint violation surfaces where it can be handled; the loser re-reads the policy and returns it with 200.
- **Derived status, never stored.** No status column exists on quotes or policies. A policy is `SCHEDULED` before its coverage starts, `ACTIVE` during, `EXPIRED` after — derived once in the owning module's domain layer and used by every read path. `CANCELLED` is reserved in the type with no producer and no branch.
- **Time.** Business dates are evaluated in the `Europe/Sofia` business zone through the single injected clock; no production code resolves "today" any other way. Business dates are `LocalDate`/`DATE`, event timestamps are `Instant`/`TIMESTAMPTZ`. Boundaries are inclusive at both ends, so coverage end is one year minus one day from the start.
- **Snapshot, not reference.** The policy's quote reference exists solely as the idempotency key: never dereferenced, no JPA association, and a policy row must remain complete and readable with the quotes table empty.
- **Migrations.** Numbering continues the existing Flyway sequence. A new column on an existing table arrives `NOT NULL` with its backfill in the same migration; each migration's header states which story it belongs to and what it backfills.
- **Response shape.** The quote response only ever grows additively — existing fields keep their name, type, and meaning. List endpoints return a bare, newest-first JSON array of the same DTO the detail endpoint returns: no envelope, no pagination this milestone.

## UX & Interaction Patterns

- Acceptance is an inline section **below the breakdown** on the quote detail screen, never a modal, and reads in the order: what you are buying → who you are → when it starts → commit. Single column at every width, built from the existing form primitives, with the shared form-field component owning every field error.
- The coverage start field is a native date input — no custom picker. The commit control is the only primary button on the screen and its label names the outcome; in flight it keeps that label, disables, and shows an inline spinner.
- Nothing is shown as done before the backend confirms it — no optimistic UI. On failure the alert is keyed off the backend error code, never raw backend prose, and every value the client entered stays in place.
- A quote that expires while its screen sits open is refused by the server, re-read, and re-rendered as expired in the same beat — the UI never asserts acceptability from a stale fetch. Successful acceptance takes the client to the new policy's detail screen, not back to a list.
- Policy rows reuse the card-plus-badge row pattern, the whole row one link target. An **expired policy renders neutral/muted, not danger** — a policy that ran its full term is the successful outcome, unlike an expired quote. The empty state points at My Quotes, or at the quote form when the client has no quotes either, so nobody is sent to a second empty screen.
- The policy detail screen presents the breakdown **identically** to the quote's, so a client comparing the two can see they match; number, premium total, and coverage period render heavier and larger than their labels.

## Cross-Story Dependencies

- 8.2 and 8.3 both consume 8.1's endpoint and its policy representation; 8.1 must land first.
- 8.2 builds on the quote detail screen delivered by Epic 6's My Quotes story, and depends on Epic 7's session handling for the expired-session path.
- 8.3 completes a state Epic 6 deliberately stubbed: an accepted quote's detail screen gains a link to its resulting policy, which only becomes possible once policies exist.
- 8.2 is where the duplicated form-phase / double-submit-guard logic across this project's forms is extracted into a shared hook rather than copied a fourth time.
