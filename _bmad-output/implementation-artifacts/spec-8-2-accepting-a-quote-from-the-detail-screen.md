---
title: 'Story 8.2: Accepting a Quote From the Detail Screen'
type: 'feature'
created: '2026-09-01'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '18776780c995b6ea24871cc4a956cde5ecf6fd6d'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 8.1's accept endpoint has no caller. `QuoteDetail` renders a valid quote's breakdown and stops — a client who wants the policy has nowhere to press. This is also the milestone's fourth form, so the `cancelledRef` / `FormPhase` / double-submit-guard block would be copied a fourth time.

**Approach:** Extract the duplicated form mechanics into one shared hook and adopt it in the three existing forms first, then build the acceptance section on top of it — an inline section **below the breakdown** on the quote detail screen, in reading order what → who → when → commit. Add the 90-day coverage-start horizon as a backend rule mirrored on the date input.

## Boundaries & Constraints

**Always:** The acceptance section renders **below** the breakdown, in the page flow — never a modal (UX-DR5) — and is single-column at every width from 375px up (NFR-5). Built from the existing `FormField` + `Input` + `Button` + `Alert` + `Spinner` primitives, with `FormField` owning every field error (M2 AD-5). Coverage start is a **native date input**, no custom picker (UX-DR9). The commit control is the only `primary` button on the screen and its label names the outcome — never "Submit"/"Confirm"; in flight it keeps that label, disables, and shows an inline `Spinner` (UX-DR6). **Nothing is shown as done before the backend confirms it** — no optimistic UI (UX-DR14). Failures render as `Alert` `danger` keyed off the backend `code`, never raw backend prose, and **every value the client entered stays in place** (UX-DR6). The 90-day horizon is enforced in the backend as the authority and mirrored on the input as a courtesy — a frontend-only bound is not sufficient (M1 AD-4). Wherever the horizon or the bonus-malus scale is surfaced, it reads as this project's own rule, not an official or legal requirement (NFR-8). Every new string ships in both `bg` and `en` with no untranslated fallback (NFR-4), and every new backend code with both translations (AD-11). **Every existing form test passes unmodified** through the hook extraction.

**Ask First:** Any change to the accept endpoint's contract beyond adding the horizon rule. Anything that would require the `/policies` routes to exist.

**Never:** No navigation to `/policies/:id` on success — that route only exists after Story 8.3, and the router has no catch-all, so navigating there today lands on a blank screen. Product-owner decision (2026-09-01): an **inline success state** replaces the form in place, and 8.3 swaps it for the navigation. No policy list/detail screens and no `GET /api/v1/policies` call here. No new UI primitive — everything needed exists. No change to `QuoteResult`'s breakdown presentation, to the accept endpoint's response shape, or to `Quote`/`Policy` persistence. No acceptance affordance on an `EXPIRED` or `ACCEPTED` quote — Story 6.3's handling of those states stands unchanged.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid quote | `status === 'CALCULATED'` | Acceptance section below the breakdown; its commit button is the screen's only `primary` | N/A |
| Expired / accepted quote | `status` is `EXPIRED` or `ACCEPTED` | No acceptance section at all — Story 6.3's notices render unchanged | N/A |
| Successful acceptance | 201 + policy | Form replaced in place by a success block: policy number, coverage period, total premium | N/A |
| Replay | 200 + same policy | Same success block — a 200 is a success, never an error (AD-5) | N/A |
| Double press | Two submits attempted | UI guard blocks the second where it can; outcome is one policy and one success block regardless | Guarantee is 8.1's constraint, not this guard |
| Coverage start in the past | yesterday | **400** `QUOTE_COVERAGE_START_IN_PAST` | Field error under the date; all entered values kept |
| Coverage start beyond the horizon | today + 91 days | **400** `QUOTE_COVERAGE_START_TOO_FAR_AHEAD` | Field error; the input's own `max` stops most cases first |
| Horizon boundary | today, and today + 90 | Both accepted | Inclusive at both ends (AD-6) |
| Vehicle identity missing / both | neither or both fields | **400** `QUOTE_VEHICLE_IDENTIFIER_REQUIRED` | Field error on `vehicleRegistration` |
| Quote expires while the screen is open | 409 `QUOTE_EXPIRED` | Client re-reads the quote; screen re-renders as expired with the refusal explained in the same beat | Acceptance section disappears — never asserted from a stale fetch (UX-DR8) |
| Session expired mid-screen | 401 | Story 7.1's behaviour: token cleared, back to login | Nothing half-created — no policy, quote untouched |
| Network / unknown failure | throw, no `code` | Generic `Alert` `danger`; form stays editable with values intact | N/A |

</frozen-after-approval>

## Code Map

**Backend — the horizon rule only (small, deliberately)**

- `backend/src/main/java/com/motorinsurance/quote/application/CoverageStartTooFarAheadException.java` -- NEW `ApiException`, modelled exactly on its sibling `CoverageStartInPastException.java`: 400, code `QUOTE_COVERAGE_START_TOO_FAR_AHEAD`, one `ApiError.FieldError` on `coverageStart`.
- `backend/src/main/java/com/motorinsurance/quote/application/QuoteAcceptanceTransaction.java:67` -- ADD the upper bound beside the existing past-date check, against the same `today` already resolved from the injected clock (AD-6). Inclusive: `today.plusDays(max)` itself is accepted.
- `backend/src/main/resources/application.yml:32` -- ADD `quote.max-coverage-start-days-ahead: 90` beside `offer-validity-days`. Under `quote`, not `policy`: this is a rule about the acceptance *input*, and `quote.application` is what enforces it.
- `backend/src/test/java/com/motorinsurance/quote/api/QuoteAcceptanceControllerTest.java` -- ADD the boundary pair (exactly `+max` accepted, `+max+1` refused with the code and field), reading the bound from the same `@Value` the existing `coverageMonths` field uses rather than a literal 90. READ-ONLY: `clientRole_coverageStartInTheFuture_isHonouredRatherThanReplacedByToday` uses `today()+45`, which stays inside the horizon and needs no change.

**Frontend — the shared form mechanics (do this first)**

- `frontend/src/hooks/useFormSubmission.ts` -- NEW. Owns exactly what the three forms duplicate today: the `cancelledRef` unmount guard and its mount-effect reset, the `'editing' | 'submitting'` phase, the double-submit early return, clearing both failures on submit, restoring `'editing'` on any error, routing `ApiRequestError` into field-vs-form failure, and resolving both during render so a visible error re-translates on a language change. See Design Notes for the contract.
- `frontend/src/hooks/useCancelledRef.ts` -- NEW. The unmount guard alone, used internally by the hook above and directly by the two load screens (Epic 6 retro item 44 — its scope question answered here rather than deferred).
- `frontend/src/features/auth/LoginForm.tsx:62` / `RegisterForm.tsx:62` / `quote/QuoteForm.tsx:118` -- ADOPT the hook. **Behaviour-identical**: same `data-testid`s, same accessible names, same generic-fallback semantics. `LoginForm`'s "token came back unusable" branch and `RegisterForm`'s `'success'` phase stay in their own components — the hook must not absorb per-form success states.
- `frontend/src/features/quote/MyQuotes.tsx:38` / `QuoteDetail.tsx:39` -- ADOPT `useCancelledRef`, replacing the hand-rolled ref + effect. Loads only; no phase machine is imposed on them.

**Frontend — the acceptance surface**

- `frontend/src/features/quote/AcceptQuoteForm.tsx` -- NEW. The form and its inline success block. Fields in reading order: holder name → vehicle registration **or** VIN → coverage start → commit. `max` on the date input mirrors the backend horizon; `min` is today. Carries a note stating the horizon is this project's own rule, in the style of `quote.form.bonusMalusNote`.
- `frontend/src/features/quote/QuoteDetail.tsx:110` -- RENDER the section after `<QuoteResult>` when `status === 'CALCULATED'`; on a `QUOTE_EXPIRED` refusal, trigger the existing `reloadToken` re-read so the screen re-renders as expired in the same beat (UX-DR8). The existing `EXPIRED`/`ACCEPTED` branches are untouched.
- `frontend/src/features/policy/policyTypes.ts` -- NEW. `PolicyResponse`, mirroring the backend's `policy.application.PolicyView` field for field. In `features/policy/` because Story 8.3's screens are its next consumer (the M3 spine's Structural Seed already places that folder).
- `frontend/src/i18n/{bg,en}.json` -- ADD the `quotes.accept.*` namespace (labels, the horizon note, the commit and submitting labels, the success block, `fieldErrors` per field) and the `QUOTE_COVERAGE_START_TOO_FAR_AHEAD` code entry.
- `frontend/src/i18n/errorMessages.ts:31` -- ADD `'quotes.accept'` to `FieldErrorNamespace`, and the new code to `FIELD_SPECIFIC_CODES` mapped to `coverageStart`.
- `frontend/src/i18n/errorMessages.test.ts:13` -- EXTEND `CODES`; the "no more, no fewer" case fails otherwise.
- READ-ONLY evidence: `QuoteResponse` (in `QuoteForm.tsx:34`) already carries `status`, so no new fetch decides whether to offer acceptance. `FormField` renders the error as a sibling of `<label>` and wires `aria-describedby` — callers pass `errorId`, nothing more. `Button` has no `loading` prop (deferred item 39); the spinner-beside-label pattern from `QuoteForm.tsx:262` is the one to copy.

## Tasks & Acceptance

**Execution:**
- [x] `hooks/useCancelledRef.ts` + `hooks/useFormSubmission.ts` -- the shared mechanics, with their own unit tests -- Epic 5 retro item 41.
- [x] `LoginForm.tsx`, `RegisterForm.tsx`, `QuoteForm.tsx` -- adopt the hook, no behaviour change -- Epic 5 retro item 41.
- [x] `MyQuotes.tsx`, `QuoteDetail.tsx` -- adopt `useCancelledRef` -- Epic 6 retro item 44.
- [x] `features/policy/policyTypes.ts` -- `PolicyResponse` mirroring `PolicyView` -- FR-M3-05.
- [x] `AcceptQuoteForm.tsx` -- the form, the in-flight state, the inline success block -- FR-M3-05, FR-M3-08, UX-DR5/6/9/14.
- [x] `QuoteDetail.tsx` -- mount the section for `CALCULATED`; re-read on `QUOTE_EXPIRED` -- UX-DR7, UX-DR8.
- [x] `CoverageStartTooFarAheadException.java` + `QuoteAcceptanceTransaction.java` + `application.yml` -- the 90-day rule, backend-authoritative -- product-owner decision 2026-09-01.
- [x] `QuoteAcceptanceControllerTest.java` -- the horizon boundary pair -- same decision.
- [x] `i18n/{bg,en}.json`, `errorMessages.ts`, `errorMessages.test.ts` -- the namespace, the new code, the field mapping -- NFR-3, NFR-4, AD-11.
- [x] `AcceptQuoteForm.test.tsx` + `QuoteDetail.test.tsx` -- every I/O Matrix row -- NFR-6.

**Acceptance Criteria:**
- Given the three existing forms, when the hook lands, then their test files are **unmodified** and green — the extraction is provable by `git diff` touching no existing test.
- Given the acceptance section, when rendered at 375px, then it is single-column with no horizontal scroll, and the screen has exactly one `primary` button.
- Given a submitted acceptance, when the backend has not yet answered, then no success copy and no policy number appear anywhere on screen.
- Given any 4xx from the endpoint, when it renders, then every field the client filled still holds its value.
- Given the whole suite after this change, when it runs, then backend and frontend are green with no test weakened or skipped.

## Design Notes

**The hook's contract.** Callers keep their own inputs and their own success state; the hook owns only the mechanics:

```ts
const { submitting, formError, fieldErrors, submit, reportFailure } =
  useFormSubmission('quotes.accept', { knownFields: KNOWN_FIELDS });

// submit() guards double-press, flips phase, clears failures, and routes
// any thrown ApiRequestError into field/form failures. The action owns
// what success means — navigate, set a phase, render a block.
await submit(async () => { const policy = await apiFetch(...); setIssued(policy); });
```

`reportFailure` exists for the one case a thrown error cannot express: `LoginForm`'s request succeeds but returns an unusable token, which must read as a generic form-level failure with no backend `code`.

**Why the horizon lives in `quote.application`, not Bean Validation.** "90 days ahead" is only meaningful against the business zone's today, which comes from the injected `Clock` (AD-6) — the same reason the past-date rule sits there. The date input's `max` is a courtesy that stops the common case client-side; the API is reachable without the form, so the backend rule is the authority (M1 AD-4).

**Inclusive at both ends.** `today + 90` is acceptable, `today + 91` is not — matching every other date boundary this milestone defines.

## Verification

**Commands:**
- `cd backend && mvn clean test` -- expected: green, with the two new horizon cases. Needs Docker running and `JAVA_HOME` on JDK 21.
- `cd frontend && npx vitest run --maxWorkers=2` -- expected: green. Full parallelism starves this machine and produces phantom timeouts; cap the workers.
- `cd frontend && npm run typecheck` -- expected: clean, including the widened `FieldErrorNamespace`.
- `node scripts/check-error-code-contract.mjs` -- expected: 15 backend codes, all present in both catalogs.

**Manual checks (if no CLI):**
- At 375px, the acceptance section is one column, the date input opens the platform picker, and the commit button is the only filled button on the screen.

## Suggested Review Order

**The shared form mechanics — read this first, it is what the rest is built on**

- The contract: what is shared, and what each form keeps for itself.
  [`useFormSubmission.ts:79`](../../frontend/src/hooks/useFormSubmission.ts#L79)

- The guard alone, for screens that load rather than submit.
  [`useCancelledRef.ts:25`](../../frontend/src/hooks/useCancelledRef.ts#L25)

- The smallest adoption, to see the shape of the change in an existing form.
  [`QuoteForm.tsx:105`](../../frontend/src/features/quote/QuoteForm.tsx#L105)

**The acceptance surface**

- The form: reading order, native date input, the one primary button.
  [`AcceptQuoteForm.tsx:69`](../../frontend/src/features/quote/AcceptQuoteForm.tsx#L69)

- Success replaces the form in place; navigation to the policy screen is 8.3's.
  [`AcceptQuoteForm.tsx:121`](../../frontend/src/features/quote/AcceptQuoteForm.tsx#L121)

- Mounted only for a still-valid quote; a mid-screen expiry triggers the re-read.
  [`QuoteDetail.tsx:123`](../../frontend/src/features/quote/QuoteDetail.tsx#L123)

**The 90-day horizon**

- Enforced against the injected clock — the authority, not the input's `max`.
  [`QuoteAcceptanceTransaction.java:83`](../../backend/src/main/java/com/motorinsurance/quote/application/QuoteAcceptanceTransaction.java#L83)

- The coverage-period rule, moved to the domain so month-end cases stay testable.
  [`CoveragePeriod.java:36`](../../backend/src/main/java/com/motorinsurance/policy/domain/CoveragePeriod.java#L36)

**Peripherals**

- The policy shape the success block reads; Story 8.3's screens are its next consumer.
  [`policyTypes.ts:19`](../../frontend/src/features/policy/policyTypes.ts#L19)
