---
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-motor-insurance-quote-claims-portal-milestone-4-2026-09-01/prd.md
  - _bmad-output/planning-artifacts/architecture/architecture-motor-insurance-quote-claims-portal-2026-08-23/ARCHITECTURE-SPINE.md
  - _bmad-output/planning-artifacts/architecture/architecture-milestone-2-2026-08-30/ARCHITECTURE-SPINE.md
  - _bmad-output/planning-artifacts/architecture/architecture-milestone-3-2026-08-31/ARCHITECTURE-SPINE.md
  - _bmad-output/planning-artifacts/ux-designs/ux-motor-insurance-quote-claims-portal-milestone-3-2026-08-31/DESIGN.md
  - _bmad-output/planning-artifacts/ux-designs/ux-motor-insurance-quote-claims-portal-milestone-3-2026-08-31/EXPERIENCE.md
  - docs/motor_insurance_portal_business_analysis.md
  - assignment.md
---

# Motor Insurance Quote & Claims Portal — Milestone 4 - Epic Breakdown

## Overview

This document provides the epic and story breakdown for Milestone 4 (File a Claim and Get a Decision), decomposing the Milestone 4 PRD into implementable stories. It is scoped separately from `epics.md` (Milestone 1), `epics-milestone-2.md` (Milestone 2) and `epics-milestone-3.md` (Milestone 3), none of which it modifies.

FR IDs are the PRD's own `FR-M4-*` identifiers, carried through unchanged. Epic numbering continues the project sequence: Epics 1–9 are complete, so Milestone 4 is **Epics 10–13**.

**Milestone 4 has no architecture spine or UX design of its own** — a deliberate, time-boxed choice made with the product owner on 2026-09-01. The structural decisions that would otherwise live in a spine are settled inline in **Additional Requirements** below (`M4-AD-1 … M4-AD-14`), and the UX conventions are carried forward from Milestone 3's spine pair rather than re-derived (`UX-DR1 … UX-DR12`). The `M1 AD-*`, `M2 AD-*` and `M3 AD-*` decisions remain binding and are not restated except where Milestone 4 deliberately departs from one — which happens exactly once, in `M4-AD-1`.

## Requirements Inventory

### Functional Requirements

FR-M4-01: Validated photo upload — allowlist JPEG/PNG/WebP by content sniffing, size cap, count cap, random storage key, no client string on the filesystem path.
FR-M4-02: Permission-checked download — the claim's CLIENT owner or any LIQUIDATOR; anyone else 404, never 403.
FR-M4-03: Bytes on a storage volume, metadata (`storageKey`, content type, size, hash, uploaded-at) in Postgres.
FR-M4-04: File an FNOL against your own policy — incident date, description, location, photos → a claim with a unique number and status `SUBMITTED`.
FR-M4-05: Coverage validated on the incident date, not on "is the policy active now"; inclusive boundaries.
FR-M4-06: FNOL input validation — no future incident date; description length bounded.
FR-M4-07: Unique claim number `CL-{year}-{8 digits}`, sequence-allocated.
FR-M4-08: My claims — owner-scoped list and detail, including status history and photos.
FR-M4-09: Backend-enforced status lifecycle `SUBMITTED → UNDER_REVIEW → APPROVED | REJECTED → PAID`; `REJECTED` and `PAID` terminal.
FR-M4-10: Business operations, not a status setter — `start-review`, `approve`, `reject`, `mark-paid`, each LIQUIDATOR-gated.
FR-M4-11: A decision carries its justification — reject requires a reason; approve requires `approvedAmount > 0` with at most 2 decimals.
FR-M4-12: The liquidator queue — claims across all clients, deliberately not owner-scoped, role-gated instead.
FR-M4-13: Concurrent decisions do not silently overwrite — optimistic locking, a distinct conflict error for the loser.
FR-M4-14: Every transition appends an immutable history row: from, to, actor, timestamp, reason or amount.
FR-M4-15: A status change publishes an application event; `notification` persists a row. Delivery never fails the transition.
FR-M4-16: Unread notifications in the client's workspace, marked read persistently, owner-scoped, polled.
FR-M4-17: The tracker stops lying — reconcile the six already-fixed action items; add the two-tier sprint-status CI check *(should-have)*.
FR-M4-18: Fold in the carry-forward items this milestone would otherwise worsen — Epic 8 items 50, 51, 52 *(should-have)*.
FR-M4-19: Playwright end-to-end scenario over BA §16.5's seven steps *(should-have, yields first)*.
FR-M4-20: A minimal, test-only ArchUnit guard on module dependencies.

### NonFunctional Requirements

NFR-1: `approvedAmount` is `BigDecimal`/`NUMERIC`, HALF_UP to 2 decimals, strictly positive, at most 2 decimal places. The only upper bound is the column's own precision, surfaced as a validation error and never as a database exception (M1 AD-5, D-10).
NFR-2: Every client-facing read path is owner-scoped **in the query**; a resource belonging to someone else returns 404, never 403 (M3 AD-10). The single deliberate exception is the liquidator queue (FR-M4-12), which is role-gated instead and is documented as such.
NFR-3: New error codes are namespaced `CLAIM_*` / `ATTACHMENT_*` / `NOTIFICATION_*` and ship with their `bg` + `en` translations in the same change; CI's error-code contract check is the gate (M1 AD-7, M3 AD-11).
NFR-4: Every new screen and message renders in Bulgarian and English with no untranslated fallback (M1 AD-8).
NFR-5: Every new screen is built from the Milestone 2 component library and is usable from ~375px up. No new one-off styling.
NFR-6: The transition rules and the concurrent-decision race each carry a Testcontainers integration test against a real PostgreSQL (BA §16.3, §16.4).
NFR-7: A story's `sprint-status.yaml` key moves to `done` in the PR that closes it, now CI-enforced per D-11 (Epic 5 retro item 40, escalated four times since).
NFR-8: Business dates are evaluated in `Europe/Sofia` through an injected `Clock`; `LocalDate`/`DATE` for business dates, `Instant`/`TIMESTAMPTZ` for events; boundaries inclusive at both ends (M3 AD-6).
NFR-9: `claim` and `notification` are created by the stories that first need them; `claim` reaches `policy` only through `policy.application`; `notification` never imports a `claim` type. Enforced by the FR-M4-20 guard, not by convention (M1 AD-2, AD-6).
NFR-10: No limit of liability is modelled. The absence is stated plainly in the root README and the OpenAPI description as something addable later as separate configuration (D-10).
NFR-11: The project is never described — in the README, the demo, the API documentation, or any story's own copy — as a full implementation of the legal motor third-party-liability claims process. Filing is the authenticated policyholder's own journey against their own policy (M3 PRD Q-4, binding).
NFR-12: Epic 13 never blocks Epics 10–12. Nothing in the must-have chain waits on a should-have epic (D-5 addendum).

### Additional Requirements (Milestone 4 architecture decisions, settled here)

- **M4-AD-1 — Claim status is *stored*, not derived. This is the one deliberate departure from M3 AD-3.** Quote and policy status are pure functions of dates, so deriving them keeps a column from drifting. A claim's status is the record of a *human decision* and cannot be recomputed from anything persisted. `claims.status` is therefore a `NOT NULL` column whose only writer is a transition operation. M3 AD-3 stays fully binding for `quote` and `policy`; nothing about them changes. The legal-transition rule is implemented **once**, in `claim.domain`, and every path uses it — the same "one place per rule" discipline M3 AD-3 was protecting.
- **M4-AD-2 — `claim` owns attachments; the storage port lives in `shared`.** There is no separate `attachment` module: an attachment has no life outside the claim it belongs to, and its permission rule *is* the claim's. `claim` owns the `attachments` table and every ownership check. The byte-level concern is a `Storage` port in `shared.storage` with a local-filesystem adapter beside it, so Milestone 5's PDF policy (A-9) can reuse the port rather than inventing a second one. `claim` depends on the port, never on the adapter.
- **M4-AD-3 — A claim and its photos arrive in one request.** `POST /api/v1/claims` is `multipart/form-data`: the FNOL fields and the files together, in one transaction. Every file is fully validated **before** anything is written; if any file fails, the whole request fails and neither a claim row nor a stored file exists. **The invariant that makes this safe: serving a file requires an `attachments` row (M4-AD-4), so a byte written and then orphaned by a failure is unreachable and inert.** A best-effort delete of orphaned bytes runs on the failure path and is logged, but correctness does not depend on it succeeding.
- **M4-AD-4 — Download is nested under its claim.** `GET /api/v1/claims/{claimId}/attachments/{attachmentId}`, served by `claim.api`, because permission is a property of the claim rather than of the file. The storage volume is **never** statically served — not by nginx, not by Spring's resource handlers. A storage key is not a capability: knowing one grants nothing.
- **M4-AD-5 — A claim references its policy; it does not snapshot it.** This is a deliberate difference from M3 AD-4, and the reason is that the referent is *already* immutable: a policy copies rather than references (M3 AD-4), so there is nothing underneath a claim that can drift. `claims.policy_id` is a real foreign key. The only value copied is `policy_number`, and only so a claim can be listed and searched without a join. Coverage dates are read from the policy.
- **M4-AD-6 — Optimistic locking is JPA `@Version` on `Claim`.** Every transition is one `@Transactional` method: load, check the transition is legal, apply it, append the history row, save. A lost update surfaces as `OptimisticLockingFailureException` and maps to `CLAIM_MODIFIED` (409) — never a 500, never a silent success. The database is the authority, exactly as `policies.quote_id UNIQUE` is the authority in M3 AD-5.
- **M4-AD-7 — Transitions are named use cases over one domain rule.** `POST /api/v1/claims/{id}/start-review | approve | reject | mark-paid`, each a distinct `claim.application` use case. The legality table lives once in `claim.domain` (`ClaimStatus.canTransitionTo`). No endpoint anywhere accepts a caller-supplied status, and no generic `PATCH /claims/{id}` exists.
- **M4-AD-8 — Claim numbers come from a sequence.** A dedicated global PostgreSQL sequence plus a `UNIQUE` constraint on `claims.claim_number`. Format `CL-{year}-{8 digits, zero-padded}`, year in the business zone. No per-year reset, gaps expected and acceptable. Mirrors M3 AD-7 exactly — deliberately, so a reader who knows one knows both.
- **M4-AD-9 — The event contract lives in `shared`, so neither module imports the other.** `ClaimStatusChanged` is a record in `shared.event`, published by `claim.application` and consumed by `notification.application`. This is what makes NFR-9's "`notification` never imports a `claim` type" actually achievable rather than aspirational. The listener is `@TransactionalEventListener(phase = AFTER_COMMIT)`, which is what delivers FR-M4-15's guarantee that a notification failure cannot roll back a decision.
- **M4-AD-10 — Notification endpoints are owner-scoped and polled.** `GET /api/v1/notifications` (the caller's own, newest first) and `POST /api/v1/notifications/{id}/read`. The poll interval is one frontend constant. No Server-Sent Events, no WebSocket, no broker (BA §9 lists all three as future work).
- **M4-AD-11 — New error codes and their HTTP mapping.** `CLAIM_NOT_FOUND` (404), `CLAIM_INCIDENT_OUTSIDE_COVERAGE` (409), `CLAIM_ILLEGAL_TRANSITION` (409), `CLAIM_MODIFIED` (409), `ATTACHMENT_UNSUPPORTED_TYPE` (400), `ATTACHMENT_TOO_LARGE` (400), `ATTACHMENT_TOO_MANY` (400), `ATTACHMENT_NOT_FOUND` (404), `NOTIFICATION_NOT_FOUND` (404). Each ships with its `bg` and `en` entry in the same change. **Note the split:** a future incident date is pure input validation and is a **400 with a field-level error** on `incidentDate`; an incident outside coverage is a conflict with the *policy's* state and is a **409**, consistent with how M3 treated `QUOTE_EXPIRED`.
- **M4-AD-12 — List endpoints stay bare ordered arrays, unpaginated** (M3 AD-12), including the liquidator queue. **Recorded consequence:** the queue is the first list in this product that is not bounded by one person's own data, so it is the first that can grow without limit. Pagination becomes a real decision in Milestone 5 when filtering (BA-13) lands — not a quiet envelope grown by one endpoint.
- **M4-AD-13 — Migrations continue from `V10`, each with its backfill in the same migration** (M3 AD-9). Expected shape: `V10` claims + the claim-number sequence, `V11` attachments, `V12` claim_status_history, `V13` notifications. Each header states its story and what it backfills, matching the convention `V3`/`V4` set.
- **M4-AD-14 — The ArchUnit guard asserts only already-accepted rules** (FR-M4-20, D-12): no cycles between modules; `policy` must not depend on `quote` (M3 AD-1); `notification` must not depend on `claim` (M4-AD-9); cross-module access through the target's `application` package only (M1 AD-2). Test-only, `src/test/java`, no build plugin, no runtime dependency. **Spring Modulith is out of scope, and a violation of a rule the architecture never stated is logged to `deferred-work.md` rather than asserted.**
- Inherited and binding: M1 AD-1…AD-11, M2 AD-1…AD-6, M3 AD-1…AD-13 (M3 AD-3 as narrowed by M4-AD-1, M3 AD-4 as distinguished by M4-AD-5).

### UX Design Requirements

Carried forward from the Milestone 3 UX spine pair rather than re-derived. Milestone 4 introduces **no new primitive** — the `Badge` M3 built is the last one this project needs.

UX-DR1: **Route additions** — `/claims` and `/claims/:id` under the CLIENT `RoleGuard`; the FNOL form is reached from a policy (`/policies/:id` → "File a claim") so a claim always starts from the policy it belongs to. `/liquidator/claims` and `/liquidator/claims/:id` under the LIQUIDATOR guard. "My claims" joins the client header nav.
UX-DR2: **Claim status vocabulary, fixed** — five states, one label per language, one `Badge` variant each, so no screen invents a sixth: `SUBMITTED` → neutral, `UNDER_REVIEW` → info, `APPROVED` → success, `REJECTED` → danger, `PAID` → success. Defined once in a `claimStatusPresentation.ts`, exactly as `quoteStatusPresentation.ts` and `policyStatusPresentation.ts` already do.
UX-DR3: **List rows are `Card` + `Badge`, the whole row one link target** — not a card with a "View" button inside it. Single-column at every width; no table, no horizontal scroller (M3 UX-DR4).
UX-DR4: **Four states on every new surface** — loading, empty (named cause + the one action that fills it), error (`Alert` keyed off the backend `code`), content (M3 UX-DR6).
UX-DR5: **The FNOL is a screen section, not a modal**, in reading order: which policy → what happened → when → where → photos → submit. One `primary` button labelled with its outcome (M3 UX-DR5). Native date input, past-date rule inverted from M3's: here a **future** date is refused with a field-level message (M3 UX-DR9's mechanism, opposite direction).
UX-DR6: **Photo selection is a native `<input type="file" multiple accept="image/jpeg,image/png,image/webp">`** — no drag-and-drop library, no upload widget. Chosen files are listed with name and size before submit; a rejected file names itself and its reason, so a user with four photos knows which one failed.
UX-DR7: **Photos render as a plain responsive thumbnail grid** linking to the full image in a new tab. No lightbox, no carousel, no gallery dependency.
UX-DR8: **The liquidator's actions render only the legal transitions**, as buttons that reveal the single field each needs — a reason for reject, an amount for approve — rather than four always-visible forms. A claim in a terminal state renders its outcome and no actions at all.
UX-DR9: **Notifications are a count in the header and a list screen.** No toast, no dropdown panel, no animation. The count is the unread number; opening the list is how you read them.
UX-DR10: **Money and dates** — money identical in both languages with an explicit `EUR` from the API, never locale-derived; dates through the existing UTC-pinned `formatDate` in the active language's convention (M3 UX-DR11).
UX-DR11: **No modal, no new colour, no new radius, no motion, no optimistic UI.** The palette closed in Milestone 3 and stays closed (M3 UX-DR14).
UX-DR12: **Accessibility floor, not an audit** — semantic elements, real labels, `role="alert"`, unsuppressed focus rings, status never signalled by colour alone, 44px tap targets below `sm`. Inherited free from the primitives; the deferred G-4 items stay deferred (M3 UX-DR15, M3 PRD Q-3).

### FR Coverage Map

FR-M4-18: Milestone prerequisite (ahead of Story 10.1), then opportunistically in Epic 10
FR-M4-01: Epic 10 — Story 10.1 (Attachment storage and validated upload)
FR-M4-03: Epic 10 — Story 10.1
FR-M4-04: Epic 10 — Story 10.2 (Claim submission), Story 10.3 (the form)
FR-M4-05: Epic 10 — Story 10.2
FR-M4-06: Epic 10 — Story 10.2 (backend), Story 10.3 (field-level surfacing)
FR-M4-07: Epic 10 — Story 10.2
FR-M4-02: Epic 10 — Story 10.4 (download consumed by the claim detail screen)
FR-M4-08: Epic 10 — Story 10.4 (My Claims list and detail)
FR-M4-09: Epic 11 — Story 11.1 (Transition rules, history and locking)
FR-M4-13: Epic 11 — Story 11.1
FR-M4-14: Epic 11 — Story 11.1
FR-M4-10: Epic 11 — Story 11.1 (endpoints), Story 11.3 (the actions)
FR-M4-11: Epic 11 — Story 11.1 (enforced), Story 11.3 (captured)
FR-M4-12: Epic 11 — Story 11.2 (The liquidator queue and claim detail)
FR-M4-15: Epic 12 — Story 12.1 (`notification` module and the event)
FR-M4-20: Epic 12 — Story 12.1
FR-M4-16: Epic 12 — Story 12.2 (In-app notifications)
FR-M4-17: Milestone prerequisite (reconciliation half), Epic 13 — Story 13.1 (CI half)
FR-M4-19: Epic 13 — Story 13.2 (Playwright end-to-end scenario)

## Milestone Prerequisites

Two items run **before Story 10.1**, not inside Epic 13, because both are cheap now and get more expensive the longer they wait (M4 PRD §5.6).

**P-1 — Reconcile the tracker** *(FR-M4-17, first half).* Move the six action items already fixed in code to `done` in `sprint-status.yaml`: items 13, 14, 35 (`GuestGuard`, `LoginForm` reusing `getCurrentRole`), 36, 37, 38 (legacy CSS retired by Story 9.2) and 46 (`formatDate` UTC-pinned in PR #67). A data fix, not a code change. Without it the milestone starts from a tracker that disagrees with the tree.

**P-2 — Extract `currentUserId(Authentication)` once** *(FR-M4-18).* It is duplicated byte-for-byte between `QuoteController` and `PolicyController` (Epic 8 retro item 50). Story 10.2 adds `ClaimController` and Story 12.2 adds `NotificationController` — the third and fourth copies. Extract before writing the third, not after the fourth.

## Epic List

### Epic 10: File a Claim With Photos

A policy stops being a receipt. A client selects one of their own policies, describes what happened, attaches photos, and gets back a claim with a real number — the first time this system accepts a file from a user and serves it back. Delivers FR-M4-01 … FR-M4-08.

### Epic 11: The Liquidator Decides

The one staff role the assignment names outright gets a job. A queue across all clients, four named decisions with their preconditions enforced by the backend, an append-only history, and two liquidators who cannot silently overwrite each other. Delivers FR-M4-09 … FR-M4-14.

### Epic 12: Told What Happened

A client finds out without asking. A status change publishes an event, a notification row is persisted, and the client sees it on their next visit — because it is a row, not a toast that fired while they were away. Carries the ArchUnit guard, since this is the epic where the last module boundary appears. Delivers FR-M4-15, FR-M4-16, FR-M4-20.

### Epic 13: The Demo Runs Itself *(should-have)*

The two things that make the milestone repeatable rather than merely finished. **Neither story blocks anything in Epics 10–12**, and if time runs short this epic yields whole — Story 13.2 before Story 13.1. Delivers FR-M4-17 (CI half), FR-M4-19.

---

## Epic 10: File a Claim With Photos

A policy stops being a receipt. Realizes UJ-6.

### Story 10.1: Attachment Storage and Validated Upload

As a **backend developer**,
I want a storage port with a validated local-filesystem adapter,
So that the claim submission story can accept photos without inventing file handling under time pressure.

**Acceptance Criteria:**

**Given** a `Storage` port in `shared.storage` (M4-AD-2)
**When** it is defined
**Then** it exposes store, read and delete operations keyed by an opaque storage key, and the local-filesystem adapter beside it is the only implementation; `claim` depends on the port and never on the adapter (M4-AD-2, NFR-9)

**Given** an uploaded file (FR-M4-01)
**When** it is validated
**Then** its type is determined by **sniffing the actual content**, never from the filename extension or the client-supplied `Content-Type`
**And** only JPEG, PNG and WebP pass
**And** a PDF renamed to `.jpg` is rejected with `ATTACHMENT_UNSUPPORTED_TYPE` (400)

**Given** the size and count caps (FR-M4-01)
**When** they are enforced
**Then** both are resolved from a single configured value, not a literal at a call site
**And** an oversized file is rejected with `ATTACHMENT_TOO_LARGE` and too many files with `ATTACHMENT_TOO_MANY` (both 400, both with `bg` + `en` per NFR-3)

**Given** a file that passes validation (FR-M4-01, FR-M4-03)
**When** it is stored
**Then** it is written under a **randomly generated storage key**, and the client-supplied filename is retained as display metadata only
**And** no client-supplied string reaches the filesystem path, so traversal sequences and executable extensions are unrepresentable by construction
**And** Postgres holds only `storageKey`, content type, size, hash and uploaded-at — never the bytes (FR-M4-03, BA §14)

**Given** the storage volume
**When** the application is configured
**Then** it is mounted as a Docker volume and is **not** statically served by any handler (M4-AD-4)

**Given** the test suite
**When** it runs
**Then** the sniffing, allowlist, size-cap, count-cap and key-generation rules each have a direct unit test, including the renamed-PDF case (BA §16.3)

---

### Story 10.2: Claim Submission, Coverage Check and Claim Number

As a **client**,
I want to file a claim against one of my own policies,
So that the insurer knows what happened and can start processing it.

**Acceptance Criteria:**

**Given** `P-2` is done (FR-M4-18)
**When** `ClaimController` is written
**Then** it uses the extracted shared `currentUserId(Authentication)` helper rather than a third byte-for-byte copy

**Given** `POST /api/v1/claims` as `multipart/form-data` (M4-AD-3, FR-M4-04)
**When** a CLIENT submits a policy id, incident date, description, location and photos
**Then** one transaction validates everything, stores the files, inserts the claim and its attachment rows, and returns **201** with the created claim
**And** the initial status is `SUBMITTED`, set by the backend and never accepted from the caller (M4-AD-1)
**And** the endpoint carries `@PreAuthorize("hasRole('CLIENT')")`

**Given** the selected policy (FR-M4-04, NFR-2)
**When** ownership is checked
**Then** the policy is loaded owner-scoped in the query; a policy belonging to someone else yields **404, never 403** (M3 AD-10)
**And** a client may file more than one claim against the same policy — no uniqueness constraint (D-9)

**Given** the incident date (FR-M4-05)
**When** coverage is validated
**Then** the check is whether the policy covered the incident **on that date**, not whether the policy is active now
**And** a claim against an `EXPIRED` policy for an incident inside its coverage window is **accepted**
**And** an incident before `coverage_start` or after `coverage_end` is rejected with `CLAIM_INCIDENT_OUTSIDE_COVERAGE` (409)
**And** boundaries are inclusive at both ends — an incident on `coverage_end` is covered (NFR-8, M3 AD-6)
**And** the comparison uses the injected `Clock` in `Europe/Sofia`; no production code calls `LocalDate.now()` directly

**Given** input validation (FR-M4-06)
**When** the request is bound
**Then** a future incident date is rejected as a **400 with a field-level error** on `incidentDate` (M4-AD-11)
**And** the description has an enforced minimum and maximum length, also field-level

**Given** the claim number (FR-M4-07, M4-AD-8)
**When** a claim is created
**Then** the number is `CL-{year}-{8 digits, zero-padded}`, allocated from a dedicated global PostgreSQL sequence with a `UNIQUE` constraint as the backstop
**And** no code path reads the highest existing number and increments it
**And** the sequence does not reset per year; gaps are expected and acceptable

**Given** any validation failure (M4-AD-3)
**When** the request fails
**Then** neither a claim row nor an attachment row exists afterwards
**And** any bytes already written are best-effort deleted and the failure is logged, but correctness does not depend on that delete succeeding — an orphaned byte is unreachable because serving requires an `attachments` row (M4-AD-4)

**Given** the `claim` module (NFR-9, M4-AD-5)
**When** it is created
**Then** it reaches `policy` only through `policy.application`
**And** `claims.policy_id` is a real foreign key; only `policy_number` is copied, and only for listing without a join

**Given** migrations (M4-AD-13)
**When** they are added
**Then** `V10` creates `claims` plus the claim-number sequence and `V11` creates `attachments`, each header naming its story

---

### Story 10.3: The FNOL Form

As a **client**,
I want a clear form to describe what happened and attach photos,
So that filing a claim after an accident is not another thing that goes wrong that day.

**Acceptance Criteria:**

**Given** a policy detail screen (UX-DR1)
**When** the client opens it
**Then** it offers "File a claim", so a claim always starts from the policy it belongs to

**Given** the FNOL screen (UX-DR5)
**When** it renders
**Then** it is a screen section, not a modal, in reading order: which policy → what happened → when → where → photos → submit
**And** it is built from the Milestone 2 component library and usable from ~375px up (NFR-5)
**And** there is one `primary` submit button labelled with its outcome

**Given** the incident date field (UX-DR5)
**When** it renders
**Then** it is a native date input with no custom picker
**And** a future date is refused with a field-level, translated message

**Given** photo selection (UX-DR6)
**When** the client chooses files
**Then** it is a native `<input type="file" multiple accept="image/jpeg,image/png,image/webp">` — no drag-and-drop library, no upload widget
**And** chosen files are listed with name and size before submit
**And** a rejected file **names itself and its reason**, so a client with four photos knows which one failed

**Given** the four surface states (UX-DR4)
**When** the screen is used
**Then** loading, error and content states are all present, the error `Alert` is keyed off the backend `code`, and submitted values are preserved on failure

**Given** the shared form hook
**When** submission is wired
**Then** it reuses the existing `useFormSubmission` rather than a fifth verbatim copy of the cancelled-ref/phase/double-submit guard (Epic 6 retro item 44's own lesson)

**Given** both languages (NFR-4)
**When** the screen renders
**Then** every label, hint, validation message and error code has a `bg` and an `en` entry with no untranslated fallback

---

### Story 10.4: My Claims — List and Detail

As a **client**,
I want to find my claims again and see everything I submitted,
So that I know the insurer has it and can watch what happens next.

**Acceptance Criteria:**

**Given** `GET /api/v1/claims` (FR-M4-08, NFR-2)
**When** a CLIENT calls it
**Then** it returns their own claims only, newest first, as a bare ordered JSON array of the same DTO the detail endpoint returns (M4-AD-12)
**And** the list is owner-scoped **in the query**; a second client's claims never appear under any query parameter
**And** each row carries the claim number, status, incident date and policy number

**Given** `GET /api/v1/claims/{id}` (FR-M4-08)
**When** a CLIENT opens one
**Then** it returns everything submitted, the current status, the full status history, and the attachment metadata
**And** a claim that is not theirs returns **404, never 403**
**And** `CLAIM_NOT_FOUND` (404) ships with its `bg` + `en` entries (NFR-3)

**Given** `GET /api/v1/claims/{claimId}/attachments/{attachmentId}` (FR-M4-02, M4-AD-4)
**When** it is called
**Then** the claim's own CLIENT owner **or any LIQUIDATOR** receives the file
**And** anyone else receives **404, never 403**
**And** an unauthenticated caller receives 401
**And** knowing or guessing a storage key grants nothing, because the volume is not statically served

**Given** the list screen (UX-DR3, UX-DR4)
**When** it renders
**Then** each row is a `Card` + `Badge` with the whole row as one link target — not a card with a "View" button inside it
**And** an empty list renders as a deliberate empty state naming its cause and the one action that fills it, never an error tone

**Given** claim status presentation (UX-DR2)
**When** a status renders anywhere
**Then** its label and `Badge` variant come from one `claimStatusPresentation.ts`, matching how `quoteStatusPresentation.ts` and `policyStatusPresentation.ts` already work
**And** the five states map: `SUBMITTED` neutral, `UNDER_REVIEW` info, `APPROVED` success, `REJECTED` danger, `PAID` success

**Given** photos on the detail screen (UX-DR7)
**When** they render
**Then** they are a plain responsive thumbnail grid linking to the full image in a new tab — no lightbox, no carousel, no gallery dependency

**Given** dates (UX-DR10)
**When** they render
**Then** they go through the existing UTC-pinned `formatDate` in the active language's convention

---

## Epic 11: The Liquidator Decides

The staff half of the assignment's «работен поток за ликвидатор». Realizes UJ-7.

### Story 11.1: Transition Rules, Status History and Optimistic Locking

As a **backend developer**,
I want the claim lifecycle enforced in one place with an append-only history and real concurrency safety,
So that no decision can be made out of order, lost, or silently overwritten.

**Acceptance Criteria:**

**Given** the status lifecycle (FR-M4-09, M4-AD-1)
**When** it is implemented
**Then** `claims.status` is a `NOT NULL` stored column — the one deliberate departure from M3 AD-3, because a human decision cannot be recomputed from persisted dates
**And** the legality table lives **once**, in `claim.domain`, and every path uses it
**And** only `SUBMITTED` may become `UNDER_REVIEW`; only `UNDER_REVIEW` may be approved or rejected; only `APPROVED` may become `PAID`
**And** `REJECTED` and `PAID` are terminal — no operation moves a claim out of either
**And** an illegal transition, including a direct `SUBMITTED → PAID`, is refused with `CLAIM_ILLEGAL_TRANSITION` (409)
**And** `NEEDS_MORE_INFORMATION` and `WITHDRAWN` do not appear in the enum, since no operation produces them (M4 PRD §5.7, the same discipline M3 applied to `CANCELLED`)

**Given** the four business operations (FR-M4-10, M4-AD-7)
**When** they are exposed
**Then** they are `POST /api/v1/claims/{id}/start-review`, `/approve`, `/reject`, `/mark-paid`, each a distinct `claim.application` use case
**And** no endpoint anywhere accepts a caller-supplied status, and no generic `PATCH /claims/{id}` exists
**And** each carries `@PreAuthorize("hasRole('LIQUIDATOR')")`; a CLIENT calling one gets **403** — a role mismatch, distinct from the 404 an ownership miss produces (M3 AD-10)

**Given** a decision's justification (FR-M4-11, NFR-1, D-10)
**When** reject is called
**Then** a missing, empty or whitespace-only reason is a field-level validation error
**When** approve is called
**Then** `approvedAmount` must be strictly positive — zero and negative are rejected
**And** it carries at most 2 decimal places; a third is a field-level validation error, not a silent rounding
**And** it is `BigDecimal`/`NUMERIC`, HALF_UP to 2 decimals
**And** the only upper bound is the column's own `NUMERIC` precision, surfaced as a validation error with a distinct code and **never** as a database exception reaching the client as a 500
**And** **no business limit of liability is enforced** and the payout is **not** bounded by the premium (D-10)

**Given** optimistic locking (FR-M4-13, M4-AD-6)
**When** two liquidators act on the same claim concurrently
**Then** exactly one decision is applied
**And** the loser receives `CLAIM_MODIFIED` (409) with a translated explanation that the claim changed — never a generic 500, never a silent success
**And** this is enforced by JPA `@Version`, not by an application-level pre-check alone

**Given** the status history (FR-M4-14)
**When** any transition succeeds
**Then** it appends a row carrying from-status, to-status, actor, timestamp, and the reason or amount that accompanied it
**And** the history is **append-only** — no path updates or deletes a history row
**And** the reason and amount are stored on the transition that carried them, not only as the claim's current state

**Given** the test suite (NFR-6, BA §16.3)
**When** it runs
**Then** Testcontainers integration tests cover: no direct `SUBMITTED → PAID`; rejection requires a reason; approval requires a positive amount; a terminal claim cannot move; and **two genuinely concurrent transitions produce one decision and one `CLAIM_MODIFIED`**

**Given** migrations (M4-AD-13)
**When** they are added
**Then** `V12` creates `claim_status_history` and adds the `@Version` column, with its backfill in the same migration

---

### Story 11.2: The Liquidator Queue and Claim Detail

As a **liquidator**,
I want to see the claims waiting for me and everything the client submitted,
So that I can actually assess a claim instead of landing on a placeholder.

**Acceptance Criteria:**

**Given** `GET /api/v1/claims/queue` (FR-M4-12, NFR-2)
**When** a LIQUIDATOR calls it
**Then** it returns claims **across all clients**, newest first, as a bare ordered array (M4-AD-12)
**And** this endpoint is **deliberately not owner-scoped** — the inversion of M3 AD-10 is role-gated instead, and is documented in the code so no reviewer reads it as a leak
**And** it carries `@PreAuthorize("hasRole('LIQUIDATOR')")`; a CLIENT calling it gets 403
**And** no filtering by status, date or client exists this milestone (BA-13 is Milestone 5)

**Given** the liquidator's landing screen (UX-DR1)
**When** a LIQUIDATOR logs in
**Then** `LiquidatorShell.tsx` is a real queue rather than a static "coming soon" card — the placeholder it has been since Milestone 1
**And** `/liquidator/claims` and `/liquidator/claims/:id` sit under the LIQUIDATOR `RoleGuard`

**Given** the queue rows (UX-DR3)
**When** they render
**Then** each is a `Card` + `Badge` with the whole row a link target, showing claim number, status, incident date and policy number
**And** an empty queue renders as a deliberate empty state, not an error

**Given** the claim detail screen
**When** a LIQUIDATOR opens a claim
**Then** it shows the full submission — description, location, incident date, policy — plus the status history
**And** the photos render as a thumbnail grid linking to the full image, fetched through the permission-checked endpoint (FR-M4-02, UX-DR7)

**Given** both languages and mobile (NFR-4, NFR-5)
**When** either screen renders
**Then** every string has a `bg` and an `en` entry with no untranslated fallback, and neither screen scrolls horizontally at 375px

---

### Story 11.3: The Four Decision Actions

As a **liquidator**,
I want to start a review, approve with an amount, reject with a reason, or mark a claim paid,
So that a claim reaches an outcome the client can be told about.

**Acceptance Criteria:**

**Given** a claim in any state (UX-DR8)
**When** the detail screen renders its actions
**Then** **only the legal transitions for that state are rendered** — a `SUBMITTED` claim offers start-review and nothing else
**And** a claim in a terminal state (`REJECTED`, `PAID`) renders its outcome and **no actions at all**

**Given** an action that needs input (UX-DR8)
**When** the liquidator picks it
**Then** the button reveals the single field it needs — a reason for reject, an amount for approve — rather than four always-visible forms
**And** no modal is used (UX-DR11)

**Given** the approve action (FR-M4-11)
**When** an amount is entered
**Then** a zero, negative, or three-decimal amount is refused with a field-level, translated message before or on submit
**And** the amount renders with an explicit `EUR` from the API, identical in both languages (UX-DR10)

**Given** the reject action (FR-M4-11)
**When** it is submitted without a reason
**Then** it is refused with a field-level, translated message

**Given** a concurrent decision (FR-M4-13)
**When** the backend returns `CLAIM_MODIFIED`
**Then** the screen shows a translated explanation that the claim changed while it was open, and re-reads the claim so the liquidator sees its real current state
**And** the UI never asserts an action is available from a stale fetch (the same discipline as M3 UX-DR8's expiry race)

**Given** a successful transition
**When** it completes
**Then** the screen re-renders the claim with its new status and the newly appended history row, with no optimistic UI (UX-DR11)

**Given** the shared form hook
**When** the actions are wired
**Then** they reuse `useFormSubmission` rather than another verbatim copy

---

## Epic 12: Told What Happened

A client finds out without asking. Realizes UJ-8.

### Story 12.1: The `notification` Module, the Event, and the ArchUnit Guard

As a **backend developer**,
I want a status change to persist a notification through an event, without the two modules knowing about each other,
So that the client can be told what happened and the module boundaries survive contact with a fourth module.

**Acceptance Criteria:**

**Given** the event contract (M4-AD-9, NFR-9)
**When** it is defined
**Then** `ClaimStatusChanged` lives in **`shared.event`**, not in `claim`
**And** `claim.application` publishes it and `notification.application` consumes it
**And** **`notification` imports no `claim` type whatsoever** — this is what makes NFR-9 achievable rather than aspirational

**Given** the listener (FR-M4-15, M4-AD-9)
**When** it is wired
**Then** it is `@TransactionalEventListener(phase = AFTER_COMMIT)`
**And** a failure to persist a notification **never rolls back the claim decision that triggered it** — the decision is the transaction of record
**And** that guarantee has a test: a listener that throws leaves the transition committed

**Given** the notification rows (FR-M4-15)
**When** transitions occur
**Then** a notification exists for every transition the client should know about: submitted, review started, approved, rejected, paid
**And** each carries its recipient, the claim it concerns, its read state, and a stable message key — **not** rendered prose, since the backend stays language-agnostic (M1 AD-8)

**Given** migrations (M4-AD-13)
**When** they are added
**Then** `V13` creates `notifications`

**Given** the ArchUnit guard (FR-M4-20, M4-AD-14, D-12)
**When** it is added
**Then** it lives entirely in `src/test/java` — no production code, no build plugin, no new runtime dependency
**And** it asserts: no cycles between modules; `policy` does not depend on `quote` (M3 AD-1); `notification` does not depend on `claim` (M4-AD-9); cross-module access is through the target's `application` package only (M1 AD-2)
**And** **Spring Modulith is not introduced**, and no refactor is undertaken to satisfy the tool rather than the rules
**And** a violation of a rule the architecture never stated is logged to `deferred-work.md` and the assertion is **not** added — the guard encodes decisions already taken, it does not make new ones

---

### Story 12.2: In-App Notifications

As a **client**,
I want to see what happened to my claim without going looking for it,
So that I find out my claim was approved by logging in, not by guessing.

**Acceptance Criteria:**

**Given** `GET /api/v1/notifications` (FR-M4-16, M4-AD-10, NFR-2)
**When** a CLIENT calls it
**Then** it returns their own notifications only, newest first, owner-scoped in the query
**And** a client never sees another client's notifications under any query parameter

**Given** `POST /api/v1/notifications/{id}/read` (FR-M4-16)
**When** a CLIENT marks one read
**Then** the change **persists** — it survives a logout, and the same notification does not reappear on the next login
**And** marking someone else's notification read returns 404, never 403
**And** `NOTIFICATION_NOT_FOUND` (404) ships with its `bg` + `en` entries (NFR-3)

**Given** `NotificationController` (FR-M4-18)
**When** it is written
**Then** it uses the shared `currentUserId(Authentication)` helper from `P-2` — not a fourth copy

**Given** the header (UX-DR9)
**When** a CLIENT is authenticated
**Then** the unread count renders in the nav
**And** there is **no toast, no dropdown panel and no animation** — opening the list screen is how notifications are read

**Given** the notification list screen (UX-DR3, UX-DR4)
**When** it renders
**Then** each entry links to the claim it concerns
**And** an empty list renders as a deliberate empty state, not an error
**And** every message resolves from its stable key into `bg` and `en` with no untranslated fallback (NFR-4)

**Given** polling (M4-AD-10)
**When** the frontend refreshes the count
**Then** the interval is one frontend constant, and no Server-Sent Events or WebSocket are introduced

---

## Epic 13: The Demo Runs Itself *(should-have)*

**Neither story blocks anything in Epics 10–12** (NFR-12, D-5 addendum). If time runs short this epic yields whole, and within it Story 13.2 yields before Story 13.1.

### Story 13.1: Sprint-Status CI Enforcement *(should-have)*

As a **team**,
we want the tracker's structural correctness enforced by CI,
So that the fifth consecutive retrospective is not spent rediscovering that `sprint-status.yaml` disagrees with the repository.

**Acceptance Criteria:**

**Given** the two-tier rule (FR-M4-17, D-11)
**When** the check runs on a pull request
**Then** **Tier 1 fails the build** for: `sprint-status.yaml` not parsing as valid YAML; a duplicated story key; a story present in the epics file with no tracker key or the reverse; and disagreement between the epics file, a `spec-*.md` and the tracker about which stories an epic contains
**And** **Tier 2 warns only** for anything depending on merge lifecycle — most obviously a branch name implying story X whose diff does not move X's key to `done`

**Given** a chore, fix or retrospective PR that closes no story
**When** the check runs
**Then** it passes with no warning — a false red build is what killed the `CONTRIBUTING.md` line, twice

**Given** a story PR that forgets its own key
**When** the check runs
**Then** it warns but does not block

**Given** the tiers prove impractical to separate in implementation (D-11)
**When** the choice collapses to one behaviour
**Then** **the check fails rather than warns** — a blocking check that occasionally annoys beats an advisory one that is ignored

**Given** the existing CI workflow
**When** the job is added
**Then** it joins `.github/workflows/ci.yml` alongside the error-code contract check, following that job's shape

---

### Story 13.2: Playwright End-to-End Scenario *(should-have, yields first)*

As a **team**,
we want BA §16.5's seven-step scenario automated,
So that the mentor demo is reproducible rather than performed from memory.

**Acceptance Criteria:**

**Given** BA §16.5 (FR-M4-19, D-7)
**When** the scenario runs
**Then** it covers, in order: a client logs in → creates a quote → accepts it → receives a policy → files a claim with a photo → a liquidator approves it → the client sees the status and the notification

**Given** the milestone's own primary success metric (SM-1)
**When** the scenario passes
**Then** it demonstrates the assignment's «Резултат» end to end with no manual database intervention

**Given** this story's priority (D-7, NFR-12)
**When** time runs short
**Then** **this story yields before every other item in Milestone 4** — the demonstrable flow is delivered by the application itself; this automates the demo rather than creating it

**Given** the existing CI workflow
**When** the harness is added
**Then** whether it runs in CI or on demand is decided in the story, and a flaky end-to-end job is **not** allowed to become a red build the team learns to ignore — the same failure mode Story 13.1 exists to prevent
