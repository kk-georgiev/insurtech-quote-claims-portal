---
title: 'Quote Input Sanity Bounds'
type: 'bugfix'
created: '2026-08-28'
status: 'done'
review_loop_iteration: 0
baseline_commit: '41d04ec1c8b3c03f683f7d0b204b2f00b09b4261'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Manual testing found `driverAge=100000` and `engineCc=10000000` both submit successfully and produce a priced quote. Not a pricing-logic bug — the PRD addendum's tariff has deliberately open-ended top bands (age `86+`, engine `2501+` cm³), and both are correctly reached by any value at or above those floors. The actual gap: nothing anywhere (backend or frontend) enforces a real-world plausibility ceiling separate from those bands, so an obvious typo/nonsense value still produces a "valid" quote.

**Approach:** Add a sanity `@Max` to `CreateQuoteRequest.driverAge` (100) and `.engineCc` (8000) — ceilings on top of the existing open-ended bands, not new pricing bands; `86+`/`2501+` stay fully valid and reachable up to these numbers. Mirror both as `max` attributes on `QuoteForm`'s number inputs.

## Boundaries & Constraints

**Always:**
- `driverAge` ceiling is exactly 100 — reuses the number the same PRD addendum's own superseded placeholder formula already used ("driver age 18–100"), not an invented value.
- `engineCc` ceiling is exactly 8000 — no production consumer-vehicle engine realistically exceeds ~6500cc; generous margin, not a tight guess.
- The existing `86+` age band and `2501+` engine-cc band remain fully reachable and correctly priced up to the new ceilings — this is not a pricing change, verify `PricingServiceTest`'s existing `calculate_engineCcInOpenEndedTopBand_resolvesToUnboundedRate` (uses `engineCc=5000`, well under 8000) and the age-86 boundary test still pass unmodified.
- Frontend `max` attributes are additive documentation/native-hint only — `QuoteForm`'s `noValidate` already means client-side bounds don't block submission (a separately deferred, known issue); do not remove `noValidate` or add client-side pre-validation logic here.

**Ask First:**
- Any change to `PricingService`'s band logic itself (age surcharge / base premium tables) — this fix only touches the DTO's outer validation layer.

**Never:**
- No change to `installments` or `regionCode` validation — out of scope, unrelated fields.
- No change to `QuoteForm`'s error-handling/state machine — the existing bean-validation `fieldErrors` path already renders whatever `@Max` produces, verified by Story 1.7's existing "bean-validation failure" test.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| driverAge at ceiling | `driverAge=100`, otherwise valid | 201, quote calculated (86+ band, +10.00€ surcharge) | N/A |
| driverAge over ceiling | `driverAge=101` | 400, field error on `driverAge` | Standard `MethodArgumentNotValidException` fieldErrors |
| engineCc at ceiling | `engineCc=8000`, otherwise valid | 201, quote calculated (2501+ band) | N/A |
| engineCc over ceiling | `engineCc=8001` | 400, field error on `engineCc` | Standard `MethodArgumentNotValidException` fieldErrors |
| Previously-reported case | `driverAge=100000` or `engineCc=10000000` | 400, field error (was: 201, silently priced) | Standard `MethodArgumentNotValidException` fieldErrors |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/motorinsurance/quote/api/CreateQuoteRequest.java:24,26` -- MODIFY: add `@Max(100)` to `driverAge`, `@Max(8000)` to `engineCc`. `@Max` is already imported (used by `installments`).
- `backend/src/test/java/com/motorinsurance/quote/api/QuoteControllerTest.java` -- MODIFY: add boundary tests mirroring the existing `driverAgeUnderEighteen`/`engineCcBelowEightHundred` pattern (lines 156–174).
- `backend/src/test/java/com/motorinsurance/pricing/application/PricingServiceTest.java:106` -- READ-ONLY, verify unmodified: `calculate_engineCcInOpenEndedTopBand_resolvesToUnboundedRate` uses `engineCc=5000` (service-level, bypasses `CreateQuoteRequest` validation entirely) — confirms this fix doesn't touch pricing-band reachability.
- `frontend/src/features/quote/QuoteForm.tsx:137,164` -- MODIFY: add `max={100}` next to existing `min={18}` (driverAge), `max={8000}` next to existing `min={800}` (engineCc).
- `frontend/src/features/quote/QuoteForm.test.tsx` -- READ-ONLY, verify unmodified: existing `VALID_INPUT` (`driverAge: '30'`, `engineCc: '1600'`) stays well under both new ceilings, no existing test breaks.

## Tasks & Acceptance

**Execution:**
- [x] `CreateQuoteRequest.java` -- add `@Max(100)` / `@Max(8000)`
- [x] `QuoteForm.tsx` -- add matching `max` attributes
- [x] `QuoteControllerTest.java` -- boundary tests for both fields (at-ceiling accepted, over-ceiling rejected with field error)
- [x] Run full suite -- confirmed `PricingServiceTest`'s open-ended-band test and `QuoteForm.test.tsx`'s existing tests pass unmodified; full backend suite 80/80; frontend typecheck clean, 5 pre-existing unrelated `LoginForm.test.tsx` failures (Node-version issue, not this change)

**Acceptance Criteria:**
- Given `driverAge` or `engineCc` beyond its ceiling, when submitted via `POST /api/v1/quotes`, then a 400 with a field-level error on that field is returned, matching the existing bean-validation error shape.
- Given a value at or under the ceiling (including the full `86+`/`2501+` open-ended bands up to the ceiling), when submitted, then pricing is unaffected — identical output to before this fix.

## Design Notes

**Why these are sanity ceilings, not new pricing bands:** the tariff's `86+`/`2501+` rows are a deliberate actuarial design choice (confirmed in the PRD addendum) — a 90-year-old driver or a 3000cc engine must still price correctly. The ceiling exists purely to reject values no real vehicle/person could have, which is a data-plausibility concern, not a pricing concern — hence a Bean Validation `@Max`, not a `PricingService` change.

**Why 100 and 8000 specifically, not round "nice" numbers picked freely:** 100 has direct team precedent in this exact document (the superseded placeholder formula's own stated input range). 8000 has no such precedent (the old formula had no engine-cc input) — it's a reasoned judgment call, flagged as such in case the human wants a different number.

## Verification

**Commands:**
- `cd backend && mvn clean test` -- expected: all tests green, including new boundary tests, with `PricingServiceTest`'s open-ended-band test unaffected -- CONFIRMED: 81/81
- `cd frontend && npm run typecheck && npm test && npm run build` -- expected: clean, no regressions -- CONFIRMED: typecheck clean, QuoteForm.test.tsx 10/10, build succeeds; 5 pre-existing unrelated `LoginForm.test.tsx` failures (Node-version issue)

## Suggested Review Order

**The fix itself**

- Entry point: both ceilings, `@Max` already imported (used by `installments`).
  [`CreateQuoteRequest.java:29`](../../backend/src/main/java/com/motorinsurance/quote/api/CreateQuoteRequest.java#L29)

- Mirrored on the frontend, with a comment pointing back to the backend's rationale (post-review fix).
  [`QuoteForm.tsx:151`](../../frontend/src/features/quote/QuoteForm.tsx#L151)

**Proof the open-ended bands still work exactly as before**

- At-ceiling tests assert the full computed breakdown, not just 201 (post-review fix) — driverAge=100 hits the `86+` surcharge band, engineCc=8000 hits the open-ended `2501+` band.
  [`QuoteControllerTest.java:178`](../../backend/src/test/java/com/motorinsurance/quote/api/QuoteControllerTest.java#L178)

- The literal originally-reported bug values (100000, 10000000) are now pinned directly, not just the nearest boundary (post-review fix).
  [`QuoteControllerTest.java:263`](../../backend/src/test/java/com/motorinsurance/quote/api/QuoteControllerTest.java#L263)

**Frontend coverage (post-review additions)**

- `max` attribute presence and an `@Max`-shaped field-error render path, mirroring the existing `@Min` test.
  [`QuoteForm.test.tsx:140`](../../frontend/src/features/quote/QuoteForm.test.tsx#L140)

**Deferred**

- One finding: no client-side hint of the ceiling before submit (same root cause as an already-deferred `noValidate` finding from Story 1.7).
  [`deferred-work.md:221`](../../_bmad-output/implementation-artifacts/deferred-work.md#L221)
