---
title: 'Story 1.5: Quote Calculation With Transparent Breakdown'
type: 'feature'
created: '2026-08-26'
status: 'done'
review_loop_iteration: 0
baseline_commit: 'c92f1cb'
context: ['{project-root}/_bmad-output/planning-artifacts/prds/prd-motor-insurance-quote-claims-portal-2026-08-23/addendum.md']
backfilled: true
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 1.4 gates every endpoint behind authentication, but no real business endpoint exists yet to protect — a client still cannot get a premium quote. Separately, the Milestone 1 tariff recorded in the PRD addendum at that point (multiplicative age/experience/region/power/bonus-malus factors, 180 EUR flat base) was an explicit placeholder pending real data, never validated against an actual GO (Гражданска отговорност) tariff.

**Approach:** Add `pricing` module (AD-2 sole entry point: `PricingService`) implementing a real zone/engine-cc GO tariff — reference data seeded via Flyway from two teammate-provided spreadsheets, cross-checked during this story against Bulgaria's 28 registration oblasti (two data-quality issues found and resolved by exclusion: `BA` is a military-vehicle code wrongly grouped as a Sofia sub-code in the source sheet; `CP`/`XX` are unverified Sofia overflow codes). This superseded the placeholder formula outright — `addendum.md`/`epics.md` were updated in this story to record the new tariff as canonical, keeping the old formula only as `<details>`-collapsed history. Add `quote` module's first endpoint (`POST /api/v1/quotes`), `@PreAuthorize("hasRole('CLIENT')")` per the hook Story 1.4 already left for it, calculating and returning the full breakdown — no persistence yet (Story 1.6).

**Backfill note:** This spec was written after implementation and review, not before (the team's process gap this document itself corrects going forward — see project retrospective). Content reflects what was actually decided and built, confirmed against the real diff and test run, not reconstructed from memory.

## Boundaries & Constraints

**Always:**
- Money exact-decimal throughout (`BigDecimal`/`NUMERIC`), rounded `HALF_UP` to 2 decimals (AD-5, NFR-1) — no floating point anywhere in the calculation.
- `driverAge` (≥18) and `engineCc` (≥800) are structural floors matching the tariff's own lower bounds — enforced as Bean Validation on `CreateQuoteRequest`, producing standard field-level errors via the existing `MethodArgumentNotValidException` path.
- `regionCode` and `installments` can only be validated against reference data (`region_zone_map`, `installment_plan`) — `PricingService` performs those lookups itself and throws its own field-level `ApiException` subtypes (`PRICING_UNKNOWN_REGION`, `PRICING_UNSUPPORTED_INSTALLMENTS`) on a miss, rather than duplicating that knowledge as a Bean Validation constraint.
- Driving experience and vehicle power play **no** part in this model — explicit, deliberate simplification superseding the placeholder formula's experience/power factors. No validation, input field, or output field for either.
- `region_zone_map` seed data excludes `BA` (Bulgaria's military-vehicle plate code) and `CP`/`XX` (unverified Sofia overflow codes) — an unmapped region code fails closed as "unknown region," never silently mispriced.
- New codes: `PRICING_UNKNOWN_REGION`, `PRICING_UNSUPPORTED_INSTALLMENTS` (400) — namespaced per AD-7; no i18n entry yet, same accepted gap as Stories 1.2/1.3 (deferred to Epic 3).

**Ask First (resolved live during this story, recorded here for the trail):**
- Whether to supersede the placeholder tariff with the teammate's real data before writing any code — confirmed yes, planning docs updated first.
- How to test the new JPA repositories against real range queries/constraints — confirmed Testcontainers + real Postgres over H2, per business analysis §16.4.

**Never:**
- No persistence of the calculated quote (Story 1.6's job — this endpoint is calculate-only).
- No tariff versioning/effective-dating (business analysis stretch goal, explicitly out of Milestone 1).
- No `docs/quote_pricing_v1.md` / `feat/quote-engine-v1` dependency — that prototype branch remains unused; only its author's real-world tariff spreadsheets (a separate, later contribution) were adopted.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid input | `driverAge=20, regionCode=KH, engineCc=1500, installments=2` | 200, full breakdown (zone 1, base 141.12, surcharge 36.00, one-time 177.12, fee 2.00, total 179.12, per-installment 89.56, EUR) | N/A |
| Age 18–24 | Any valid region/cc/installments | `ageSurcharge = 36.00` | N/A |
| Age 25–85 (inclusive) | Any valid region/cc/installments | `ageSurcharge = 0.00` | N/A |
| Age 86+ | Any valid region/cc/installments | `ageSurcharge = 10.00` | N/A |
| `driverAge` < 18 | e.g. 17 | 400, field error on `driverAge` | `SHARED_VALIDATION_ERROR` |
| `engineCc` < 800 | e.g. 700 | 400, field error on `engineCc` | `SHARED_VALIDATION_ERROR` |
| Unknown `regionCode` | e.g. `ZZ`, or `BA` | 400, field error on `regionCode` | `PRICING_UNKNOWN_REGION` |
| `installments` not in {1,2,4} | e.g. 3 | 400, field error on `installments` | `PRICING_UNSUPPORTED_INSTALLMENTS` |
| Malformed request body | e.g. `driverAge` sent as a string | 400, not 500 | `SHARED_VALIDATION_ERROR` (new `HttpMessageNotReadableException` handler) |
| No token | Any input | 401 | `AUTH_UNAUTHENTICATED` |
| Non-CLIENT token | Any input | 403 | `AUTH_FORBIDDEN` |

</frozen-after-approval>

> **Superseded 2026-08-27** (Epic 1 retro action item 7): the "valid input" row's `200` was correct when this story shipped (calculate-only, no persistence). Story 1.6 made `POST /api/v1/quotes` persist the quote, and its status was renegotiated to `201 Created` to match `POST /api/v1/auth/register` — see `spec-1-6-quote-persistence-and-retrieval.md`'s I/O matrix. The breakdown values in that row are unchanged.

## Code Map

- `backend/src/main/resources/db/migration/V3__create_pricing_tables.sql` -- NEW: `tariff_zone`, `region_zone_map`, `tariff_rate`, `age_surcharge`, `installment_plan` + seed data
- `backend/src/main/java/com/motorinsurance/pricing/domain/{RegionZoneMap,TariffRate,AgeSurcharge,InstallmentPlan}.java` -- NEW: JPA entities
- `backend/src/main/java/com/motorinsurance/pricing/persistence/{RegionZoneMapRepository,TariffRateRepository,AgeSurchargeRepository,InstallmentPlanRepository}.java` -- NEW
- `backend/src/main/java/com/motorinsurance/pricing/application/{PricingService,PricingResult,UnknownRegionCodeException,UnsupportedInstallmentCountException}.java` -- NEW: the sole entry point (AD-2) + its result DTO + its two field-level exceptions
- `backend/src/main/java/com/motorinsurance/quote/api/{QuoteController,CreateQuoteRequest,QuoteResponse}.java` -- NEW
- `backend/src/main/java/com/motorinsurance/quote/application/QuoteService.java` -- NEW: thin orchestration, delegates to `PricingService`
- `backend/src/main/java/com/motorinsurance/shared/api/GlobalExceptionHandler.java` -- MODIFY: add `HttpMessageNotReadableException` handler (deferred since Story 1.1, resolved here — first controller whose fields a client can plausibly mistype)
- `backend/pom.xml` -- MODIFY: add Testcontainers (`spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql` — note the `testcontainers-` prefix, renamed in Testcontainers 2.x)
- `backend/src/test/java/com/motorinsurance/pricing/application/PricingServiceTest.java` -- NEW: Testcontainers + real Postgres
- `backend/src/test/java/com/motorinsurance/quote/api/QuoteControllerTest.java` -- NEW: full HTTP stack, same pattern as `JwtAuthenticationFilterTest`
- `_bmad-output/planning-artifacts/prds/.../addendum.md`, `_bmad-output/planning-artifacts/epics.md` -- MODIFY: supersede the placeholder tariff formula and Story 1.5's AC with the real zone/engine-cc model

## Tasks & Acceptance

**Execution:**
- [x] Verify the teammate's tariff spreadsheets against public sources — found and resolved two data-quality issues (`BA`, `CP`/`XX`)
- [x] Update `addendum.md`/`epics.md` to record the new tariff as canonical before writing code
- [x] `V3__create_pricing_tables.sql` -- schema + seed data
- [x] `pricing` module -- entities, repositories, `PricingService`
- [x] `quote` module -- `QuoteController`, DTOs, `QuoteService`
- [x] `GlobalExceptionHandler` -- malformed-body handler (discovered as newly-reachable during this story, not in the original deferred note's context)
- [x] Testcontainers wiring + `PricingServiceTest` + `QuoteControllerTest`
- [x] `mvn test` -- 32/32 passing
- [x] Manual smoke test via `mvn spring-boot:run` + curl against the golden case

**Acceptance Criteria:** see `epics.md` Story 1.5 (Given valid inputs.../And given an unknown regionCode, an engineCc below 800, a driverAge under 18, or an unsupported installments value.../And given any calculation... exact decimal precision...).

## Design Notes

**Why `pricing` throws its own field-level exceptions instead of Bean Validation for `regionCode`/`installments`:** both can only be checked against reference data pricing itself owns (`region_zone_map`, `installment_plan`) — a Bean Validation constraint would either hardcode a duplicate list of valid values (drifts from the DB) or require a custom DB-aware validator, more machinery than a direct repository-miss check inside the one service that already does the lookup.

**Why no separate pure `PremiumCalculator` class:** the arithmetic itself is two additions and a division — the actual business rules are the table lookups, not the math combining them. Splitting it out would be a premature abstraction for three lines; `PricingServiceTest`'s Testcontainers-backed integration tests already exercise the arithmetic together with the lookups, which is what needs proving.

**Why `HttpMessageNotReadableException` is handled now:** `deferred-work.md` flagged this since Story 1.1 as "worth adding once a second controller with a body lands" — Story 1.5 is exactly that moment, and leaving it unhandled would have this story's own new endpoint 500 on a client's simple type mistake.

**Why `installmentAmount` doesn't do remainder-absorption across installments:** a quote isn't yet a binding payment schedule (no Policy/invoice entity exists in Milestone 1) — a nominal `total ÷ installments` rounded `HALF_UP` is an acceptable display figure at this stage; exact remainder allocation is deferred to whichever future story introduces real invoicing.

## Verification

**Commands:**
- `cd backend && mvn compile` -- expected: clean
- `cd backend && mvn test` -- expected: 32/32 passing (Testcontainers spins up Postgres automatically; requires Docker running)
- `cd backend && mvn spring-boot:run` (after `docker compose up postgres`), then the golden-case curl sequence (register → login → `POST /api/v1/quotes` with `driverAge=20, regionCode=KH, engineCc=1500, installments=2`) -- expected: `totalPremium: 179.12`, `installmentAmount: 89.56`

## Suggested Review Order

**Tariff correctness (highest risk — real money math)**
- The golden-case test, hand-verified against the source spreadsheet row.
  [`PricingServiceTest.java`](../../backend/src/test/java/com/motorinsurance/pricing/application/PricingServiceTest.java)
- The seed data itself, especially the deliberate `BA`/`CP`/`XX` exclusions.
  [`V3__create_pricing_tables.sql`](../../backend/src/main/resources/db/migration/V3__create_pricing_tables.sql)
- The one entry point and its lookup-miss exceptions.
  [`PricingService.java`](../../backend/src/main/java/com/motorinsurance/pricing/application/PricingService.java)

**Auth wiring reused from Story 1.4**
- `@PreAuthorize` on the first real consumer of the shared gate.
  [`QuoteController.java`](../../backend/src/main/java/com/motorinsurance/quote/api/QuoteController.java)

**The malformed-body fix**
- New handler, and whether it could mask a legitimate validation case.
  [`GlobalExceptionHandler.java`](../../backend/src/main/java/com/motorinsurance/shared/api/GlobalExceptionHandler.java)

### Review Findings

Retroactive `bmad-code-review` run, 2026-08-26 — layers: blind-hunter, edge-case-hunter, verification-gap, acceptance-auditor (all four active, `review_mode: full`). 20 raw findings, merged to 12: 2 decision-needed (both resolved by Konstantin, became patches below), 7 patch (all applied), 2 defer, 2 dismissed. All 7 patches applied; `mvn clean test` afterward: 39/39 passing (7 `JwtServiceTest` + 9 `JwtAuthenticationFilterTest` + 13 `PricingServiceTest` + 10 `QuoteControllerTest`).

- [x] [Review][Patch] (resolved decision) `tariff_zone.zone_name` is seeded but never read. **Resolved by Konstantin: add a `TariffZone` lookup and a `zoneName` field to the response.** Fixed: `TariffZone`/`TariffZoneRepository` added; `PricingResult`/`QuoteResponse` now carry `zoneName`. [`V3__create_pricing_tables.sql`](../../backend/src/main/resources/db/migration/V3__create_pricing_tables.sql), [`PricingResult.java`](../../backend/src/main/java/com/motorinsurance/pricing/application/PricingResult.java)
- [x] [Review][Patch] (resolved decision) Test setup split between Testcontainers (`pricing`/`quote`) and manual `docker compose up postgres` (`auth`). **Resolved by Konstantin: migrate `JwtAuthenticationFilterTest` to Testcontainers too** (`JwtServiceTest` needs no change — it's a pure unit test with no Spring context or DB). Fixed. [`JwtAuthenticationFilterTest.java`](../../backend/src/test/java/com/motorinsurance/auth/config/JwtAuthenticationFilterTest.java)

- [x] [Review][Patch] `installments` int→short narrowing cast lets out-of-range values alias into a valid plan (e.g. `65540` casts to `(short) 4`, matching the seeded 4-installment row) — bypasses validation entirely and returns `200 OK` with a nonsensical `installmentAmount` instead of the required `400 PRICING_UNSUPPORTED_INSTALLMENTS`. Fixed: `@Min(1) @Max(4)` on the DTO, plus a range guard inside `PricingService` itself as pricing's sole entry point (AD-2), independent of caller. [`PricingService.java:71-73,79-80`](../../backend/src/main/java/com/motorinsurance/pricing/application/PricingService.java#L71), [`CreateQuoteRequest.java:21`](../../backend/src/main/java/com/motorinsurance/quote/api/CreateQuoteRequest.java#L21)
- [x] [Review][Patch] `CreateQuoteRequest.regionCode`'s `@Size(max = 5)` contradicts this spec's own stated design (regionCode validation belongs entirely to `PricingService`'s reference-data lookup) — an over-long code gets generic `SHARED_VALIDATION_ERROR` instead of `PRICING_UNKNOWN_REGION`. Fixed: `@Size` removed, `@NotBlank` kept. [`CreateQuoteRequest.java:19`](../../backend/src/main/java/com/motorinsurance/quote/api/CreateQuoteRequest.java#L19)
- [x] [Review][Patch] `regionCode` lookup is case-sensitive against uppercase-only seed data — a lowercase but otherwise-valid plate prefix (e.g. `"kh"`) is wrongly rejected as `PRICING_UNKNOWN_REGION`. Fixed: normalized via `.trim().toUpperCase()` before lookup. [`PricingService.java:57-60`](../../backend/src/main/java/com/motorinsurance/pricing/application/PricingService.java#L57)
- [x] [Review][Patch] Malformed-body handler (`HttpMessageNotReadableException`) returns a flat message with no field information, unlike every other 400 handler in the same class. Fixed: unwraps to Jackson 3's `tools.jackson.databind.exc.MismatchedInputException`, extracts `.getPath()` for a `FieldError` when available. [`GlobalExceptionHandler.java`](../../backend/src/main/java/com/motorinsurance/shared/api/GlobalExceptionHandler.java)
- [x] [Review][Patch] `deferred-work.md` wasn't updated with the new risks this story itself surfaces, despite that being the file's stated convention. Fixed: this review's two `defer` findings appended under `## Deferred from: code review of story-1-5 (2026-08-26)`. [`deferred-work.md`](../deferred-work.md)
- [x] [Review][Patch] `addendum.md`'s region→zone table groups Plovdiv (`PB`) with Sofia-city (`C`/`CA`/`CB`) without flagging that specific grouping as unverified, unlike the explicit `BA`/`CP`/`XX` caveats already there — the oblast→code mapping was independently verified, the zone-tier bucketing itself was taken as given from the source spreadsheet. Fixed: caveat note added. [`addendum.md`](../planning-artifacts/prds/prd-motor-insurance-quote-claims-portal-2026-08-23/addendum.md)

- [x] [Review][Defer] No overlap-prevention constraint on `tariff_rate`/`age_surcharge` ranges — a future bad migration could cause an opaque 500 via `NonUniqueResultException`. Current seed data has no such overlap. — deferred, structural hardening beyond this story's scope
- [x] [Review][Defer] No end-to-end frontend for quote calculation — the AC is phrased "as a client, I want to submit... and see," but only the backend exists; no frontend story for this exists anywhere in `epics.md` yet either. — deferred, pre-existing scope gap not introduced by this diff

**Dismissed (2):** hardcoded `"CLIENT"` string in `@PreAuthorize` instead of a `Role` enum reference — not actionable, Spring Security `@PreAuthorize` requires a SpEL string literal, matches Story 1.4's own established precedent exactly. No `@Transactional(readOnly = true)` on `PricingService.calculate`'s reads — negligible in practice, all four repositories read static seed reference data, no realistic inconsistency window.
