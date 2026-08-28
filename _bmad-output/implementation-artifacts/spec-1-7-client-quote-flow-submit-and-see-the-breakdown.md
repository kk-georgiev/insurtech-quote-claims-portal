---
title: 'Story 1.7: Client Quote Flow — Submit and See the Breakdown'
type: 'feature'
created: '2026-08-28'
status: 'done'
review_loop_iteration: 0
baseline_commit: '9d5dffbfd9f95e762a5dd3205f7eb764ceba49f5'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `POST /api/v1/quotes` and `GET /api/v1/quotes/{id}` have been built and tested since Story 1.5/1.6, but no frontend ever calls them — a logged-in CLIENT lands on a bare `ClientShell.tsx` stub with no way to get a quote. FR-8/FR-9 ("submit driver/vehicle parameters and see the calculated premium") are attributed to Epic 1 but were never delivered end-to-end.

**Approach:** A quote form inside `ClientShell.tsx` (driverAge, regionCode, engineCc, installments) that calls the existing `POST /api/v1/quotes` and renders the full breakdown on success, following the same form/error patterns `LoginForm`/`RegisterForm` already established.

## Boundaries & Constraints

**Always:**
- Attach `Authorization: Bearer <token>` (from `getToken()`) to the quote request — this is the first authenticated frontend call in the codebase; `apiFetch` currently sends no auth header anywhere.
- Reuse `apiFetch`/`ApiRequestError`/`ApiFieldError` from `api/client.ts` unmodified in shape — extend additively (e.g. an `authenticated` option), never fork a parallel call path.
- Field errors (`fieldErrors`) render next to their input, like `LoginForm`/`RegisterForm`; anything unmatched renders as a generic form-level error.
- Money renders exactly as the API returns it — never re-derive or re-round client-side.

**Ask First:**
- Any change to `api/client.ts`'s existing exported signatures beyond an additive option.
- Any change to `CreateQuoteRequest`/`QuoteResponse` (backend) — this story is frontend-only.

**Never:**
- No new route — form and breakdown render in place inside `ClientShell.tsx`.
- No "view a past quote by ID" UI (FR-11) — out of scope, a later story's job.
- No route guard (Story 2.4's job) — a logged-out/non-CLIENT visitor reaching this form is out of scope; the backend still enforces the real boundary.
- No changes to `AgentShell.tsx`/`LiquidatorShell.tsx`/`AdministratorShell.tsx` — Story 2.3 (Viktor, concurrent) owns those.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid quote | Logged-in CLIENT submits valid driverAge/regionCode/engineCc/installments | Full breakdown rendered (zone, base premium, age surcharge, one-time premium, installment fee, total premium, installment amount) | N/A |
| Unknown region | `regionCode` not in `region_zone_map` | Field-level error on region input | `PRICING_UNKNOWN_REGION` fieldError |
| Unsupported installments | `installments` not in {1,2,4} | Field-level error on installments input | `PRICING_UNSUPPORTED_INSTALLMENTS` fieldError |
| Bean-validation failure | `driverAge<18`, `engineCc<800`, blank `regionCode` | Field-level error on that input | Standard `MethodArgumentNotValidException` fieldErrors |
| No/expired token | `getToken()` null, or backend returns 401 | Generic form-level error, form stays editable | Same fallback pattern as `LoginForm` |

</frozen-after-approval>

## Code Map

- `frontend/src/features/shells/client/ClientShell.tsx` -- MODIFY: replace the bare stub with the quote form + result
- `frontend/src/features/quote/QuoteForm.tsx` -- NEW: mirrors `LoginForm.tsx`'s shape (`'editing'|'submitting'` phase, `cancelledRef` unmount guard, `toFieldErrorMap`)
- `frontend/src/features/quote/QuoteResult.tsx` -- NEW (or inline in `QuoteForm`): renders the breakdown
- `frontend/src/api/client.ts` -- MODIFY: `ApiFetchOptions` needs a way to attach `Authorization` — none exists today (confirmed: zero `Authorization` usages anywhere in `frontend/src`)
- `frontend/src/api/authToken.ts:19` -- `getToken()`, read for the header
- `backend/.../quote/api/CreateQuoteRequest.java` -- READ-ONLY: `driverAge: Integer, regionCode: String, engineCc: Integer, installments: Integer`
- `backend/.../quote/api/QuoteResponse.java` -- READ-ONLY: `id, createdAt, driverAge, regionCode, engineCc, zoneId, zoneName, basePremium, ageSurcharge, oneTimePremium, installments, installmentFee, totalPremium, installmentAmount, currency`
- `backend/.../pricing/application/Unknown­RegionCodeException.java`, `UnsupportedInstallmentCountException.java` -- READ-ONLY: both already attach `ApiError.FieldError`, no special-casing needed beyond the existing generic `fieldErrors` path
- `frontend/src/features/auth/LoginForm.tsx` -- READ-ONLY: the pattern to mirror

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/api/client.ts` -- add an `authenticated` option to `ApiFetchOptions`, attaching `Authorization: Bearer ${getToken()}` -- first authenticated call in the codebase, must not break login/register/health
- [x] `frontend/src/features/quote/QuoteForm.tsx` -- form, submits `POST /api/v1/quotes` with `{ authenticated: true }`
- [x] `frontend/src/features/quote/QuoteResult.tsx` (or inline) -- renders the full `QuoteResponse` breakdown
- [x] `frontend/src/features/shells/client/ClientShell.tsx` -- mount `QuoteForm`, replacing the stub
- [x] Component tests for the happy path and each I/O Matrix edge case (Vitest, mirroring `LoginForm.test.tsx`'s `apiFetch` mocking)
- [x] `npm run typecheck && npm test && npm run build` -- clean; 24/24 new+pre-existing suite passes, 5 pre-existing `LoginForm.test.tsx` failures confirmed unrelated (Node 24 vs CI's pinned Node 20, react-router internal `navigate()`/`AbortSignal`, not exercised by `QuoteForm`)

**Acceptance Criteria:** see `epics.md` Story 1.7.

## Design Notes

**Why extend `apiFetch` instead of a parallel authenticated client:** AD-10 mandates one typed fetch client; a second path would fork the `ApiRequestError`/`fieldErrors` shape every future Epic 2 screen also depends on.

**Why no route guard here:** Story 2.4 owns the single guard wrapper (AD-10). An ad-hoc check here would duplicate and risk diverging from that pattern.

**Why inline, not a new route:** nothing in FR-8/FR-9 or the PRD's UJ-1 flow asks for a shareable result URL — `GET /api/v1/quotes/{id}` (already built) is there if a later story wants that.

## Verification

**Commands:**
- `cd frontend && npm run typecheck && npm test && npm run build` -- expected: clean, all tests green
- Manual: `docker compose up postgres` + `mvn spring-boot:run` + `npm run dev`, register/login as CLIENT, submit a quote, confirm the breakdown matches a hand-computed value against the PRD addendum's tariff table -- CONFIRMED 2026-08-28: full register -> login -> submit -> breakdown round trip verified working end to end

## Suggested Review Order

**First authenticated frontend call**

- Entry point: `apiFetch` grows an additive `authenticated` option, attaching `Authorization: Bearer <token>` only when opted in — no existing caller's behavior changes.
  [`client.ts:55`](../../frontend/src/api/client.ts#L55)

- Header attachment itself — omits the header entirely rather than sending a literal `"Bearer null"` when no token is stored.
  [`client.ts:68`](../../frontend/src/api/client.ts#L68)

**The quote form**

- `handleSubmit` — resets all three result/error states (including the post-review `setQuote(null)` fix) before each new submit, then calls the endpoint with `{ authenticated: true }`.
  [`QuoteForm.tsx:94`](../../frontend/src/features/quote/QuoteForm.tsx#L94)

- Unmatched-`fieldErrors` fallback (post-review fix) — if a field error names something this form doesn't render, fall back to the generic message instead of silently showing nothing.
  [`QuoteForm.tsx:127`](../../frontend/src/features/quote/QuoteForm.tsx#L127)

- Mounted in place of the old bare stub — no new route, per the frozen spec's Boundaries.
  [`ClientShell.tsx:15`](../../frontend/src/features/shells/client/ClientShell.tsx#L15)

**Proof it's actually wired up (post-review fix)**

- The index-route test now asserts the quote form's own heading renders, not just the shell wrapper — would have caught a broken/removed mount.
  [`router.test.tsx:42`](../../frontend/src/app/router.test.tsx#L42)

**Result rendering**

- `QuoteResult` — every amount interpolated exactly as the API returns it, never re-derived or re-rounded.
  [`QuoteResult.tsx:13`](../../frontend/src/features/quote/QuoteResult.tsx#L13)

**Tests**

- `QuoteForm.test.tsx` — covers all 5 frozen I/O Matrix rows plus the two review-driven additions (unmatched field name, plain network error).
  [`QuoteForm.test.tsx:68`](../../frontend/src/features/quote/QuoteForm.test.tsx#L68)

- `client.test.ts` — unit-tests the new `authenticated` option in isolation from any form.
  [`client.test.ts:1`](../../frontend/src/api/client.test.ts#L1)
