---
title: 'Story 8.1: Accept a Quote and Issue a Policy (Backend)'
type: 'feature'
created: '2026-08-31'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '59df1bc249179650a42654270fe79bf95d11b540'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A quote is still a dead end. `quotes.accepted_at` exists but nothing sets it, `QuoteStatus.ACCEPTED` has no producer, and there is no `policy` module at all — so the milestone's core transaction (a valid offer becoming a contract) cannot happen, and neither can Stories 8.2/8.3 which consume it.

**Approach:** Create the `policy` module (domain, persistence, application — no api yet) and add `POST /api/v1/quotes/{id}/accept` served by `quote.api`. `quote.application` owns one transaction that validates ownership and expiry, sets `accepted_at`, and calls one `policy.application` issuance entry point with a fully-formed command; `policy` allocates its number from a Postgres sequence, derives its coverage period, and stores a complete snapshot. The endpoint is genuinely idempotent: a replay returns the same policy with 200, never an error.

## Boundaries & Constraints

**Always:** `policy` imports no `quote` type, holds no `QuoteRepository`, and issues no query against `quotes` (AD-1) — the dependency runs one way, `quote → policy`. `policies.quote_id` is `UNIQUE` and is the sole authority that one policy exists per quote (AD-5); the application pre-check is an optimization, never the guarantee. The whole accept sequence runs in one `@Transactional` method; any failure leaves neither an accepted quote nor a policy. Ownership is enforced inside the query (`findByIdAndCustomerId`), a miss is **404, never 403** (AD-10), and the customer id comes from the `SecurityContext`. `@PreAuthorize("hasRole('CLIENT')")` on the new endpoint. Every business date resolves through the injected `Clock` in `Europe/Sofia`; `LocalDate`/`DATE` for business dates, `Instant`/`TIMESTAMPTZ` for events; boundaries inclusive at both ends (AD-6). Money stays `BigDecimal`/`NUMERIC` copied verbatim from the quote — never recalculated (NFR-1). The migration continues the Flyway sequence with a story-stating header (AD-9). New error codes ship with their `bg` and `en` entries in the same change (AD-11).

**Ask First:** Any change to an existing `QuoteResponse` field, or any need to make `policy` read `quotes`.

**Never:** No `QUOTE_ALREADY_ACCEPTED` code — a replay is a 200 success (AD-5). No `status` column on `policies`, and **no policy status derivation this story** — FR-M3-09 is Story 8.3's. No `policy.api` controller, no `GET /policies` (8.3), no frontend feature work beyond the i18n error-code contract (8.2/8.3). No JPA association from `Policy` to `Quote` — a plain `UUID` column only (AD-4). No "max + 1" numbering (AD-7). No external registry lookup for plate/VIN — format-level validation only. No new tests added to the 644-line `QuoteControllerTest.java`, and no splitting of it either (retro item 45 stays open). No existing test weakened.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy path | CLIENT accepts own valid quote; `coverageStart` today or later, holder name, registration **or** VIN | **201** + policy representation: `MI-{year}-{8 digits}`, `coverageEnd = start + 12 months − 1 day`, full premium/rating snapshot copied from the quote; `quotes.accepted_at` set | N/A |
| Replay (already accepted) | Same quote accepted again, any body | **200** + the *same* policy; no insert attempted, `accepted_at` unchanged | N/A |
| Concurrent double-accept | Two accepts race past the pre-check | Exactly one policy row; one 201 + one 200 (or two 200s) | Loser's unique-constraint violation is caught, its tx rolls back, policy re-read and returned — never an error |
| Not the caller's quote | Valid quote id owned by another client | **404** `QUOTE_NOT_FOUND` | Never 403; indistinguishable from unknown id |
| Unknown quote id | Random UUID | **404** `QUOTE_NOT_FOUND` | N/A |
| Expired quote | `today > valid_until`, not accepted | **409** `QUOTE_EXPIRED` | The one genuine conflict this endpoint reports |
| Coverage start in the past | `coverageStart` = yesterday (business zone) | **400** `QUOTE_COVERAGE_START_IN_PAST`, `fieldErrors[coverageStart]` | Today itself is accepted — inclusive boundary |
| Vehicle identity missing / both given | Neither or both of registration and VIN | **400** `QUOTE_VEHICLE_IDENTIFIER_REQUIRED`, `fieldErrors[vehicleRegistration]` | Exactly one is required |
| Malformed identity | Blank holder name, VIN not 17 chars of `[A-HJ-NPR-Z0-9]`, over-long plate | **400** `SHARED_VALIDATION_ERROR` + offending field | Bean Validation on the request DTO |
| No / wrong-role token | Anonymous, or AGENT token | **401** `AUTH_UNAUTHENTICATED` / **403** `AUTH_FORBIDDEN` | Existing gates, unchanged |
| Tariff mutated after issuance | `tariff_rate` (or the quote row) changed later | Stored policy figures byte-identical | Snapshot, not join (AD-4) |

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/db/migration/V9__create_policies_table.sql` -- NEW. `CREATE SEQUENCE policy_number_seq` (global, never resets — AD-7) + `policies`. `quote_id UUID NOT NULL UNIQUE` with **no FK** (AD-4: the row must read complete with `quotes` empty); `policy_number VARCHAR(20) NOT NULL UNIQUE`; `customer_id` FK to `users` like `quotes` has; `CHECK (num_nonnulls(vehicle_registration, vehicle_vin) = 1)`; `idx_policies_customer_id`. Snapshot columns mirror `V4`/`V7` types exactly (`NUMERIC(10,2)` / `(6,2)` / factor `NUMERIC(4,3)`). Header follows `V7`/`V8`'s convention.
- `backend/src/main/java/com/motorinsurance/policy/domain/Policy.java` -- NEW entity mirroring V9 (`ddl-auto: validate`). Modelled on `quote/domain/Quote.java`: `UUID.randomUUID()` id in the constructor, getters only, no setters.
- `backend/src/main/java/com/motorinsurance/policy/domain/PolicyNumber.java` -- NEW. `format(int year, long sequenceValue)` → `MI-%d-%08d`. Pure function, no clock.
- `backend/src/main/java/com/motorinsurance/policy/persistence/PolicyRepository.java` -- NEW. `findByQuoteIdAndCustomerId` (AD-10 — ownership in the query) and `@Query(value = "SELECT nextval('policy_number_seq')", nativeQuery = true) long nextPolicyNumberValue()`.
- `backend/src/main/java/com/motorinsurance/policy/application/{IssuePolicyCommand,PolicyView,PolicyService,PolicyAlreadyIssuedException}.java` -- NEW. `PolicyView` is the cross-module representation, mirroring how `pricing.application.PricingResult` crosses the `pricing → quote` boundary — `quote.api` returns it directly, so it never imports a `policy.api` type (M1 AD-2). `PolicyService.issue` derives `coverageEnd` from `${policy.coverage-months}` and stamps `issuedAt`/the number's year from the injected `Clock`; the insert is `saveAndFlush` inside try/catch (the `QuoteService.calculate:83` pattern) and rethrows `PolicyAlreadyIssuedException` — a plain `RuntimeException`, deliberately **not** an `ApiException`, since it must never reach a client.
- `backend/src/main/java/com/motorinsurance/quote/application/QuoteAcceptanceService.java` -- NEW, the use-case entry point (AD-1). **Not** `@Transactional`: it calls the transactional bean below and, on `PolicyAlreadyIssuedException` (tx already rolled back), re-reads via `PolicyService.findByQuoteId` in a fresh transaction and returns 200. See Design Notes.
- `backend/src/main/java/com/motorinsurance/quote/application/QuoteAcceptanceTransaction.java` -- NEW. The single `@Transactional` sequence: read owner-scoped quote → 404; already accepted → read existing policy, `created=false`; `quote.status(LocalDate.now(clock)) == EXPIRED` → 409; coverage-start and vehicle-identity checks → 400; `quote.accept(now)`; build the command from the quote's own persisted snapshot; issue.
- `backend/src/main/java/com/motorinsurance/quote/application/{QuoteExpiredException,CoverageStartInPastException,VehicleIdentifierRequiredException}.java` -- NEW `ApiException` subclasses, modelled on `QuoteNotFoundException.java`; the latter two carry `ApiError.FieldError` (the base class already supports it) — no new `GlobalExceptionHandler` method.
- `backend/src/main/java/com/motorinsurance/quote/api/AcceptQuoteRequest.java` -- NEW record: `@NotNull LocalDate coverageStart`, `@NotBlank @Size(2..120) holderName`, optional `vehicleRegistration`, optional `vehicleVin`. Three details settled during implementation and recorded here rather than left to javadoc: both identifier patterns admit the **empty string** (a form submitting the field it did not use as `""` must reach the specific `QUOTE_VEHICLE_IDENTIFIER_REQUIRED` message, not a pattern failure); the VIN pattern is `CASE_INSENSITIVE` since the application layer uppercases; and every text pattern is **anchored on a non-space character at both ends**, because validation runs before the application layer trims and `"  AB  "` would otherwise satisfy a bare length rule and persist two characters long.
- `backend/src/main/java/com/motorinsurance/quote/api/QuoteController.java:56` -- ADD `accept`, returning `ResponseEntity<PolicyView>` (201 vs 200 is per-call, so no `@ResponseStatus`). Reuses `currentUserId(authentication)`.
- `backend/src/main/java/com/motorinsurance/quote/domain/Quote.java:203` -- ADD one intention-revealing mutator `accept(Instant acceptedAt)`; `status()` already returns `ACCEPTED` off it. No other change.
- `backend/src/main/resources/application.yml:32` -- ADD `policy.coverage-months: 12` beside `quote.offer-validity-days` (PRD Q-1: a configured value, not a literal at a call site).
- `backend/src/test/java/com/motorinsurance/quote/api/QuoteAcceptanceControllerTest.java` -- NEW Testcontainers + `RestClient` suite in its own file (retro item 45: nothing new lands in `QuoteControllerTest.java`). Copies that file's `registerClient()`/`postJson`/`extractId` helper shape; adds a `JdbcTemplate` for the three states HTTP cannot create: expiring a quote (`UPDATE quotes SET valid_until = ...`), counting `policies` rows, and mutating `tariff_rate` to prove the snapshot.
- `backend/src/test/java/com/motorinsurance/policy/domain/PolicyNumberTest.java` -- NEW unit test: padding, an 8-digit boundary, the year segment.
- `backend/src/test/java/com/motorinsurance/quote/domain/QuoteTest.java` -- EXTEND: `accept()` flips derived status to `ACCEPTED` even before `valid_until`.
- `frontend/src/i18n/{bg,en}.json` -- ADD the three new codes under `errors.codes` (CI's `scripts/check-error-code-contract.mjs` derives backend codes from quoted `MODULE_REASON` literals and fails in both directions).
- `frontend/src/i18n/errorMessages.test.ts:13` -- EXTEND the `CODES` list; its "no more, no fewer" case fails otherwise.
- `frontend/src/i18n/errorMessages.ts:59` -- EXTEND `FIELD_SPECIFIC_CODES` with the two field-scoped codes, plus a precedence case per code in the test. Originally scoped to Story 8.2 and pulled forward during review: an unregistered code falls through to a per-field key no namespace defines, so `resolveFieldErrors` would render the generic fallback and discard the very copy this change adds to both catalogs — with nothing pointing back here once 8.2 builds the form.
- READ-ONLY evidence: `SecurityConfig.java:88` gates by `anyRequest().authenticated()` with a POST allow-list — the new path needs no entry. `GlobalExceptionHandler.java:139` already maps any `ApiException` to the envelope. `QuoteResponse` needs no new field: `status` derives to `ACCEPTED` automatically once `accepted_at` is set.

## Tasks & Acceptance

**Execution:**
- [x] `V9__create_policies_table.sql` -- sequence, table, unique/check constraints, index -- AD-4/AD-5/AD-7/AD-9.
- [x] `policy/domain/{Policy,PolicyNumber}.java` -- entity mirroring V9 + the number format -- FR-M3-06, FR-M3-07.
- [x] `policy/persistence/PolicyRepository.java` -- owner-scoped read + `nextval` -- AD-7, AD-10.
- [x] `policy/application/*` -- `IssuePolicyCommand`, `PolicyView`, `PolicyService`, `PolicyAlreadyIssuedException`; coverage period from config; unique-violation handling -- AD-1, AD-5.
- [x] `quote/application/QuoteAcceptance{Service,Transaction}.java` + the three exceptions -- the one transaction and the idempotent outcome -- FR-M3-05, AD-5.
- [x] `quote/domain/Quote.java` -- `accept(Instant)` -- FR-M3-05.
- [x] `quote/api/{AcceptQuoteRequest,QuoteController}.java` -- the endpoint, 201/200 -- FR-M3-04, FR-M3-08, AD-2.
- [x] `application.yml` -- `policy.coverage-months: 12` -- PRD Q-1.
- [x] `QuoteAcceptanceControllerTest.java` -- every I/O Matrix row, including the concurrent double-accept and the snapshot-immutability case -- NFR-6.
- [x] `PolicyNumberTest.java` + `QuoteTest.java` -- unit coverage for the pure rules -- FR-M3-06.
- [x] `frontend/src/i18n/{bg,en}.json` + `errorMessages.test.ts` -- the three codes in both languages -- AD-11, NFR-3.

**Acceptance Criteria:**
- Given the merged change, when the module graph is inspected, then no file under `policy/` imports anything from `com.motorinsurance.quote`, and `quote` reaches `policy` only through `policy.application`.
- Given two accept requests issued concurrently for one quote, when both complete, then `SELECT count(*) FROM policies WHERE quote_id = ?` is exactly 1 and neither response is an error.
- Given a policy issued from a quote, when `tariff_rate` is mutated and the policy row re-read, then every stored figure is unchanged and equals the quote's own persisted values.
- Given any rejected acceptance (404/409/400), when the response returns, then `quotes.accepted_at` is still null and no `policies` row exists for that quote.
- Given `mvn clean test`, when it runs, then every pre-existing test passes unmodified alongside the new ones.

## Design Notes

**Why the acceptance use case is split across two beans.** Postgres marks a transaction unusable after a constraint violation, and Hibernate flags it rollback-only — so the race loser cannot catch the violation and keep working in the same transaction, which is what a literal reading of AD-5 ("the loser catches that exception, re-reads the policy") would imply. `QuoteAcceptanceTransaction` therefore owns the single `@Transactional` sequence and lets `PolicyAlreadyIssuedException` escape (rolling its own work back — including its `accepted_at` write, leaving the winner's intact), while the non-transactional `QuoteAcceptanceService` catches it and re-reads through `PolicyService` in a fresh transaction. Two beans, not a self-call, because Spring's proxy would not apply `@Transactional` to a self-invocation. Observable behaviour is exactly AD-5's contract: 201 for the winner, 200 with the same policy for the loser.

**Coverage period lives in `policy`.** The command carries `coverageStart` only; `policy` derives `coverageEnd = start.plusMonths(months).minusDays(1)` from its own configured period. Deriving it in `quote` would put a policy rule in the wrong module; this keeps the command "fully formed" in the sense AD-1 means — every *quote-side* value already resolved.

**Sequence gaps are expected.** `nextval` is non-transactional, so a rolled-back acceptance consumes a number. AD-7 accepts this; do not add compensating logic.

**Invariant breach.** An accepted quote with no policy row is unreachable by construction (both happen in one transaction). If the pre-check ever finds one, throw `IllegalStateException` — a logged 500 — rather than silently issuing a second policy.

## Verification

**Commands:**
- `cd backend && mvn clean test` -- expected: all green, including the new acceptance suite. Requires Docker Desktop running (Testcontainers) and `JAVA_HOME` pointed at JDK 21.
- `node scripts/check-error-code-contract.mjs` -- expected: `Error-code contract OK` with the three new codes counted.
- `cd frontend && npm test` -- expected: all green; the `CODES` "no more, no fewer" case proves the catalogs match.

**Manual checks (if no CLI):**
- `docker compose up --build`, register a client, calculate a quote, then `POST /api/v1/quotes/{id}/accept` twice with the same body: first 201, second 200 with an identical policy id and number.

## Suggested Review Order

**The acceptance transaction — start here**

- The whole BA §7.3 sequence in one place; read this to grasp the design.
  [`QuoteAcceptanceTransaction.java:49`](../../backend/src/main/java/com/motorinsurance/quote/application/QuoteAcceptanceTransaction.java#L49)

- Why acceptance needs two beans: the race loser must leave its transaction to recover.
  [`QuoteAcceptanceService.java:48`](../../backend/src/main/java/com/motorinsurance/quote/application/QuoteAcceptanceService.java#L48)

- 201 vs 200 is per-call, so the outcome carries `created` rather than the controller assuming.
  [`QuoteController.java:95`](../../backend/src/main/java/com/motorinsurance/quote/api/QuoteController.java#L95)

**Idempotency and the unique constraint**

- The constraint is the authority; this insert is flushed so its violation is catchable.
  [`PolicyService.java:80`](../../backend/src/main/java/com/motorinsurance/policy/application/PolicyService.java#L80)

- Only the `quote_id` collision becomes a replay; every other integrity failure still propagates.
  [`PolicyService.java:150`](../../backend/src/main/java/com/motorinsurance/policy/application/PolicyService.java#L150)

- `quote_id UNIQUE` with no FK — idempotency key only, never dereferenced.
  [`V9__create_policies_table.sql:76`](../../backend/src/main/resources/db/migration/V9__create_policies_table.sql#L76)

**Schema and the snapshot**

- A dedicated sequence allocates numbers; no "max + 1" path exists.
  [`V9__create_policies_table.sql:41`](../../backend/src/main/resources/db/migration/V9__create_policies_table.sql#L41)

- Entity mirrors the migration, no setters, no association back to `Quote`.
  [`Policy.java:33`](../../backend/src/main/java/com/motorinsurance/policy/domain/Policy.java#L33)

- Every value already resolved by the caller — `policy` looks nothing up.
  [`IssuePolicyCommand.java:33`](../../backend/src/main/java/com/motorinsurance/policy/application/IssuePolicyCommand.java#L33)

**Module boundary**

- The cross-module representation lives in `application`, so `quote.api` imports no `policy.api`.
  [`PolicyView.java:36`](../../backend/src/main/java/com/motorinsurance/policy/application/PolicyView.java#L36)

- The quote's one permitted state change, guarded against a silent re-accept.
  [`Quote.java:237`](../../backend/src/main/java/com/motorinsurance/quote/domain/Quote.java#L237)

**Tests worth reading (the two review found gaps)**

- Proves the client's chosen start date is honoured, not silently replaced by today.
  [`QuoteAcceptanceControllerTest.java:138`](../../backend/src/test/java/com/motorinsurance/quote/api/QuoteAcceptanceControllerTest.java#L138)

- Forces the losing insert deterministically; the concurrent test alone cannot fail for its own reason.
  [`QuoteAcceptanceControllerTest.java:271`](../../backend/src/test/java/com/motorinsurance/quote/api/QuoteAcceptanceControllerTest.java#L271)

- The BA §19 double-click, end to end: one creation, nobody an error.
  [`QuoteAcceptanceControllerTest.java:230`](../../backend/src/test/java/com/motorinsurance/quote/api/QuoteAcceptanceControllerTest.java#L230)

**Peripherals**

- New codes land with both translations; CI's contract check is the gate.
  [`en.json:155`](../../frontend/src/i18n/en.json#L155)

- Field-scoped codes registered so 8.2's form shows the specific copy, not the fallback.
  [`errorMessages.ts:64`](../../frontend/src/i18n/errorMessages.ts#L64)

- The coverage period is configuration, not a literal at a call site.
  [`application.yml:38`](../../backend/src/main/resources/application.yml#L38)
