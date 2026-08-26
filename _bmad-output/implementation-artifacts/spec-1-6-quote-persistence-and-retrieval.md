---
title: 'Story 1.6: Quote Persistence and Retrieval'
type: 'feature'
created: '2026-08-26'
status: 'done'
review_loop_iteration: 0
baseline_commit: '9ab05a0'
context: ['{project-root}/_bmad-output/implementation-artifacts/spec-1-5-quote-calculation-with-transparent-breakdown.md']
backfilled: true
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 1.5's `POST /api/v1/quotes` only calculates transiently — nothing is saved, so a client can never revisit a quote they already got, and there is no id to retrieve one by.

**Approach:** Add `quote.domain.Quote` (JPA entity, `quotes` table) and `quote.persistence.QuoteRepository`. `QuoteService.calculate` now persists immediately as part of calculation - no separate save step, matching the AC's phrasing exactly ("when it completes, then it's persisted"). New `GET /api/v1/quotes/{id}`, ownership-scoped at the query level via `findByIdAndCustomerId` rather than fetch-then-compare - a miss (wrong owner, or truly nonexistent id) is indistinguishable and collapses to the same 404 `QUOTE_NOT_FOUND`, never a 403 that would confirm a foreign id is real (IDOR protection, matches the AC's "never shown someone else's data"). The breakdown is stored flat, not FK'd into `pricing`'s reference tables, so a saved quote keeps showing exactly what was calculated even if the tariff changes later - `zoneName` (added during Story 1.5's own code review, after this story's first draft) was folded in on the same principle when the two branches were merged.

**Backfill note:** Like spec-1-5, this spec was written after implementation, not before - this document itself is part of closing that process gap going forward. Content reflects what was actually built and verified (`mvn test`, 43/43 on the merged branch), not reconstructed from memory.

## Boundaries & Constraints

**Always:**
- Every successful calculation is persisted immediately - `POST /api/v1/quotes` calculates and saves in one transaction (`@Transactional`), never a separate "save this quote" step.
- Ownership is enforced at the query level (`QuoteRepository.findByIdAndCustomerId`), not by fetching a quote and comparing ids in application code afterward.
- `quotes.customer_id` has a real foreign key to `users(id)` (V4__create_quotes_table.sql) - a forged JWT for a nonexistent user (fine for Story 1.4's auth-gate-only tests) fails with a constraint violation the moment a quote actually persists; tests that expect a successful, persisting call must register a real user first.
- A quote id that exists but belongs to a different customer renders identically to one that doesn't exist at all: 404 `QUOTE_NOT_FOUND`, never 403.

**Ask First:** none surfaced live specific to this story - a fairly mechanical CRUD addition on top of Story 1.5's calculation. (The `zoneName` persistence question was Story 1.5's own review decision, not this story's.)

**Never:**
- No update or delete of a persisted quote (immutable once created) - not requested by the AC.
- No list-all-quotes endpoint - only single lookup by id (`GET /api/v1/quotes/{id}`), matching the AC's "retrievable by ID" exactly.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Successful calculation | Valid input, CLIENT token | 200, persisted with `id`/`createdAt`, full breakdown | N/A |
| Retrieve own quote | `GET /{id}` for an id the caller owns | 200, identical breakdown to the original calculation | N/A |
| Retrieve another customer's quote | `GET /{id}` for an id owned by someone else | 404 | `QUOTE_NOT_FOUND` |
| Retrieve nonexistent id | `GET /{random-uuid}` | 404 | `QUOTE_NOT_FOUND` |
| No token on retrieval | `GET /{id}`, no `Authorization` header | 401 | `AUTH_UNAUTHENTICATED` |
| Non-CLIENT token | Valid token, wrong role, on either endpoint | 403 | `AUTH_FORBIDDEN` |

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/db/migration/V4__create_quotes_table.sql` -- NEW: `quotes` table, FK to `users(id)`, index on `customer_id`
- `backend/src/main/java/com/motorinsurance/quote/domain/Quote.java` -- NEW: JPA entity, flat breakdown snapshot including `zoneName`
- `backend/src/main/java/com/motorinsurance/quote/persistence/QuoteRepository.java` -- NEW: `findByIdAndCustomerId`
- `backend/src/main/java/com/motorinsurance/quote/application/QuoteNotFoundException.java` -- NEW: 404, `QUOTE_NOT_FOUND`
- `backend/src/main/java/com/motorinsurance/quote/application/QuoteService.java` -- MODIFY: `calculate` now persists and returns the saved quote; new `getById(id, customerId)`
- `backend/src/main/java/com/motorinsurance/quote/api/QuoteController.java` -- MODIFY: `calculate` takes `Authentication` for the owning customer id; new `GET /{id}`
- `backend/src/main/java/com/motorinsurance/quote/api/QuoteResponse.java` -- MODIFY: adds `id`, `createdAt`
- `backend/src/test/java/com/motorinsurance/quote/api/QuoteControllerTest.java` -- MODIFY: `registerClient()` helper (real user via `/api/v1/auth/register`, required by the FK), persistence/retrieval/ownership tests

## Tasks & Acceptance

**Execution:**
- [x] `V4__create_quotes_table.sql` -- schema, FK, index
- [x] `Quote` entity + `QuoteRepository`
- [x] `QuoteService.calculate` persists; `QuoteService.getById` added
- [x] `QuoteController` -- `Authentication` wiring, `GET /{id}`
- [x] `QuoteControllerTest` -- `registerClient()` helper, full persistence/ownership/not-found coverage
- [x] Fold in Story 1.5 review's `zoneName` field through `Quote`/`QuoteResponse` during the branch merge
- [x] `mvn test` -- 43/43 passing on the merged branch (7 JwtServiceTest + 9 JwtAuthenticationFilterTest + 13 PricingServiceTest + 14 QuoteControllerTest)

**Acceptance Criteria:** see `epics.md` Story 1.6 (persisted with inputs/factors/premium/creation time; owned id returns the full original quote; another customer's id is rejected, never shown).

## Design Notes

**Why ownership is a query condition, not a fetch-then-compare:** `findByIdAndCustomerId` makes "not yours" and "doesn't exist" the same code path by construction - there's no separate branch that could leak a distinguishing signal (timing, a different error) between the two cases.

**Why 404, never 403, for someone else's quote:** a 403 would confirm the id is real and just not the caller's - handing an attacker probing ids a free existence oracle. A 404 tells them nothing.

**Why the breakdown (including `zoneName`) is stored flat rather than referencing `pricing`'s tables:** a quote is a record of what the customer was shown at calculation time. If the tariff or a zone's display name changes later, an already-issued quote must not silently change with it.

**Why tests need `registerClient()`:** the FK from `quotes.customer_id` to `users.id` means any test where a quote is actually expected to persist needs a real, committed user row - a forged token's `UUID.randomUUID()` subject has no such row and trips the constraint the moment `QuoteService.calculate` tries to save.

## Verification

**Commands:**
- `cd backend && mvn clean test` -- expected: 43/43 passing (needs Docker for Testcontainers)
- `cd backend && mvn spring-boot:run` (after `docker compose up postgres`), then: register → login → `POST /api/v1/quotes` (note the returned `id`) → `GET /api/v1/quotes/{id}` with the same token -- expected: identical breakdown back
- Same `GET` with a different client's token -- expected: 404 `QUOTE_NOT_FOUND`

## Suggested Review Order

**Ownership and IDOR protection (highest risk - customer data)**
- The query-level ownership check itself.
  [`QuoteRepository.java`](../../backend/src/main/java/com/motorinsurance/quote/persistence/QuoteRepository.java)
- The 404-not-403 exception and its rationale.
  [`QuoteNotFoundException.java`](../../backend/src/main/java/com/motorinsurance/quote/application/QuoteNotFoundException.java)

**Persistence correctness**
- Every breakdown field actually gets saved and read back identically, including the merged-in `zoneName`.
  [`Quote.java`](../../backend/src/main/java/com/motorinsurance/quote/domain/Quote.java), [`QuoteService.java`](../../backend/src/main/java/com/motorinsurance/quote/application/QuoteService.java)

**Auth wiring**
- `Authentication` -> customer id extraction, reused across both endpoints.
  [`QuoteController.java`](../../backend/src/main/java/com/motorinsurance/quote/api/QuoteController.java)

### Review Findings

Retroactive `bmad-code-review` run, 2026-08-26 — layers: blind-hunter, edge-case-hunter, verification-gap, acceptance-auditor (all four active, `review_mode: full`). 22 raw findings, merged to 16: 0 decision-needed, 5 patch (all applied), 7 defer, 4 dismissed. `mvn clean test` afterward: 45/45 passing (7 JwtServiceTest + 9 JwtAuthenticationFilterTest + 13 PricingServiceTest + 16 QuoteControllerTest).

- [x] [Review][Patch] `QuoteResponse` never returns the original inputs (`driverAge`, `regionCode`, `engineCc`) — the AC says "I get the full original quote," but a client retrieving a quote later sees only the resulting breakdown, not what it was quoted for. Fixed: all three added to `QuoteResponse`, populated from the persisted `Quote` entity. [`QuoteResponse.java`](../../backend/src/main/java/com/motorinsurance/quote/api/QuoteResponse.java), [`QuoteService.java`](../../backend/src/main/java/com/motorinsurance/quote/application/QuoteService.java)
- [x] [Review][Patch] Persisted `regionCode` is the raw request value, not the normalized one `PricingService` actually priced against — a quote calculated from `"kh"` stores `regionCode="kh"` while `zoneId`/`zoneName` reflect `"KH"`, an inconsistent record. Fixed: `PricingResult` now carries the normalized `regionCode`; `QuoteService` persists that instead of the raw request value. [`PricingResult.java`](../../backend/src/main/java/com/motorinsurance/pricing/application/PricingResult.java), [`QuoteService.java`](../../backend/src/main/java/com/motorinsurance/quote/application/QuoteService.java)
- [x] [Review][Patch] An invalid `{id}` path segment on `GET /api/v1/quotes/{id}` (e.g. `/api/v1/quotes/not-a-uuid`) throws `MethodArgumentTypeMismatchException`, unhandled by `GlobalExceptionHandler` — falls through to the generic 500 instead of a clean 400, same class of gap already fixed once for malformed request bodies. Fixed: dedicated handler added, 400 with a field-level error. [`GlobalExceptionHandler.java`](../../backend/src/main/java/com/motorinsurance/shared/api/GlobalExceptionHandler.java)
- [x] [Review][Patch] No test verifies a non-CLIENT role is rejected on `GET /api/v1/quotes/{id}` — the `@PreAuthorize("hasRole('CLIENT')")` annotation is present and presumably correct, but a regression removing/weakening it would ship with no test failing (the sibling POST endpoint has exactly this test, GET doesn't). Fixed: `nonClientRole_onGetById_isRejectedForbidden` added. [`QuoteControllerTest.java`](../../backend/src/test/java/com/motorinsurance/quote/api/QuoteControllerTest.java)
- [x] [Review][Patch] `clientRole_calculateThenRetrieveById_returnsTheSamePersistedQuote` only checks `id` and `totalPremium` substrings between the create and retrieve responses, not the full breakdown — a mapping bug in `QuoteService.toResponse` (e.g. dropping a field) wouldn't be caught. Fixed: full-body comparison (`createdAt` normalized out first — Postgres `TIMESTAMPTZ`'s microsecond rounding vs. Java `Instant`'s nanosecond precision made the two responses render that one field differently for the same instant, an artifact of the DB round-trip, not a real divergence). [`QuoteControllerTest.java`](../../backend/src/test/java/com/motorinsurance/quote/api/QuoteControllerTest.java)

- [x] [Review][Defer] `Quote`'s constructor narrows `int installments` to `short` with no bounds check, unlike `PricingService`'s explicit guard against the identical overflow - not reachable today (the only call path already validates via `PricingService` before constructing `Quote`), flagged by 3 of 4 review layers as a symmetry gap worth a defensive check if a second call path to `Quote`'s constructor is ever added. — deferred, unreachable given pricing's sole-entry-point architecture
- [x] [Review][Defer] `quoteRepository.save()` doesn't catch `DataIntegrityViolationException` - an unexpected DB constraint violation surfaces as the generic 500 rather than a controlled error. — deferred, same "shouldn't happen given upstream validation" category as `PricingService`'s own already-accepted gaps
- [x] [Review][Defer] No explicit length validation before persisting `regionCode`/`zoneName`/`currency` against their `VARCHAR` column widths - all three are either seed-controlled (`zoneName`, `currency`) or already matched a seeded primary key (`regionCode`) by the time they reach persistence. — deferred, low risk given the values' provenance
- [x] [Review][Defer] `QuoteController.currentUserId()` casts `Authentication.getPrincipal()` straight to `UUID` with no failure handling - safe today since `JwtAuthenticationFilter` is the only thing that ever populates the security context, and always with a `UUID`. — deferred, revisit only if the auth mechanism changes
- [x] [Review][Defer] `QuoteControllerTest.extractId()` parses response JSON with a hand-written regex instead of proper deserialization - works today because DTOs are flat with one unique `id` field, would silently break or mismatch if a response ever nests another `id`-bearing object. — deferred, test-quality cleanup
- [x] [Review][Defer] `quotes.customer_id REFERENCES users(id)` has no explicit `ON DELETE` behavior (defaults to `RESTRICT`) - only matters once user deletion exists, which it doesn't yet. — deferred
- [x] [Review][Defer] No documented retention/PII stance for `driverAge`/`regionCode`/`engineCc` now that Story 1.6 makes them durable (Story 1.5 was ephemeral) - a data-governance question, not a code defect. — deferred, business/compliance decision outside this story's scope

**Dismissed (4):** no unit-level test for `QuoteService`/`QuoteRepository` beyond the Testcontainers integration tests — the ownership-scoped query is exactly the kind of thing that needs a real DB to prove, a mocked unit test would add little `PricingServiceTest`-style value here since this service has no standalone calculation logic to isolate. `registerClient()` coupling quote tests to auth's HTTP contract — accepted tradeoff given the real FK, no materially better alternative without much larger test-infrastructure investment. No list-all-quotes endpoint — explicitly out of scope per this story's own "Never" section and `epics.md`'s literal AC ("retrievable by ID", not "listable"). `Quote` entity missing `equals`/`hashCode`/`toString`/`@Version` — matches this codebase's existing convention (`auth.domain.User` has none of these either), not a deviation introduced here.

