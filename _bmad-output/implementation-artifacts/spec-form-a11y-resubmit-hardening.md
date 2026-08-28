---
title: 'Form A11y and Resubmit-Guard Hardening'
type: 'chore'
created: '2026-08-28'
status: 'done'
review_loop_iteration: 1
baseline_commit: '4b8a434bd7d1457c4093de0527186d544ac8d1b4'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Three findings, each deferred independently across multiple review passes with the exact same note — "worth a shared fix applied to all three forms together" — remain unaddressed in `LoginForm.tsx`, `RegisterForm.tsx`, `QuoteForm.tsx`: (1) field errors have no `aria-describedby`/`aria-invalid`, so a screen-reader user re-tabbing into an errored field gets no re-announcement; (2) no guard against a rapid double-submit sending two concurrent requests; (3) the `cancelledRef` unmount guard exists in all three but is untested in any of them. Investigation also found `RegisterForm.tsx` has **zero** test coverage — no `RegisterForm.test.tsx` exists at all (confirmed with the human; scope includes creating it).

**Approach:** Apply the same three hardening changes identically across all three forms, plus create `RegisterForm.test.tsx` from scratch (mirroring `LoginForm.test.tsx`'s coverage: happy path, field errors, generic error) so all three forms end up with equal verification depth.

## Boundaries & Constraints

**Always:**
- `aria-describedby`/`aria-invalid` pattern is identical across all three forms: each error `<p role="alert">` gets `id="{input-id}-error"`; the corresponding `<input>` gets `aria-invalid={<fieldHasError> ? true : undefined}` and `aria-describedby={<fieldHasError> ? '{input-id}-error' : undefined}`.
- Double-submit guard is one line at the top of each `handleSubmit`, before any state changes: `if (phase === 'submitting') return;`.
- `RegisterForm.test.tsx` follows `LoginForm.test.tsx`'s established conventions exactly: mock only `apiFetch` (`vi.mock('../../api/client', ...)`), a `renderForm`/`fillAndSubmit` helper pair, `mockReset: true` from `vitest.config.ts` (no manual reset needed).
- Unmount-guard test shape (all three forms): mock `apiFetch` to return a controllable pending `Promise`, submit, unmount the rendered tree before resolving, then resolve — assert no console error/warning about a state update on an unmounted component.

**Ask First:**
- Any change to `LoginForm.test.tsx`'s existing router-based render setup (`createMemoryRouter`/`RouterProvider`) — the 5 pre-existing Node-version-related failures there are known and out of scope; do not attempt to fix them as part of this chore.

**Never:**
- No i18n catalog, no new error-message copy — reuse exactly what each form already renders.
- No change to `QuoteResult.tsx`, `RootLayout.tsx`, or any shell component — out of scope, and `AgentShell`/`LiquidatorShell`/`AdministratorShell` are Story 2.3's concurrent territory.
- No visual/CSS change — `aria-*` attributes are non-visual; `index.css`'s existing `[role="alert"]` styling already covers the error text.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Field error rendered | Any form, a field-level error is set | Input has `aria-invalid="true"` and `aria-describedby` pointing at the error `<p>`'s `id`; error `<p>` has that exact `id` | N/A |
| No error | Field has no error | Input has neither `aria-invalid` nor `aria-describedby` present | N/A |
| Rapid double-submit | User submits twice before re-render (e.g. double Enter) | Only one request is sent — `apiFetch` called exactly once | N/A |
| Unmount mid-request (LoginForm) | Component unmounts while a submit is pending, then resolves | The guarded side effect (`saveToken`) never fires — `getToken()` still returns `null` after the resolve | N/A |
| Unmount mid-request (RegisterForm/QuoteForm) | Component unmounts while a submit is pending, then resolves | Resolving after unmount does not throw or log a console error — the only side effects these two forms have post-response are internal React state updates, which React 18 already no-ops silently after unmount (no console warning exists to assert on, unlike the assumption this row originally made) | N/A |
| RegisterForm baseline coverage | New `RegisterForm.test.tsx` | Happy path (success state), `AUTH_EMAIL_TAKEN` error, field-level bean-validation errors, generic fallback — mirrors `LoginForm.test.tsx`'s depth | N/A |

</frozen-after-approval>

## Code Map

- `frontend/src/features/auth/LoginForm.tsx` -- MODIFY: aria attrs on `email`/`password` inputs + their error `<p>`s (`login-email-error`, `login-password-error`); double-submit guard in `handleSubmit`.
- `frontend/src/features/auth/RegisterForm.tsx` -- MODIFY: same pattern (`register-email-error`, `register-password-error`); double-submit guard.
- `frontend/src/features/quote/QuoteForm.tsx` -- MODIFY: same pattern on all four fields (`quote-driverAge-error`, `quote-regionCode-error`, `quote-engineCc-error`, `quote-installments-error`); double-submit guard. Note: `handleSubmit` already does `setQuote(null)` etc. at the top (post-Story-1.7-review fix) — the new guard line goes before those resets.
- `frontend/src/features/auth/LoginForm.test.tsx:33-51` -- MODIFY: add unmount-guard and double-submit tests, reusing `renderLogin`/`fillAndSubmit`. Renders through `createMemoryRouter`/`RouterProvider` (not `<LoginForm />` directly) — the unmount test must not trigger a successful-login `navigate()` (avoids the known Node-version `AbortSignal` issue: mock a pending/never-resolving promise or an error response, don't mock success).
- `frontend/src/features/auth/RegisterForm.test.tsx` -- NEW: full suite mirroring `LoginForm.test.tsx`'s structure and mocking approach, plus the two new hardening tests.
- `frontend/src/features/quote/QuoteForm.test.tsx:40-53` -- MODIFY: add unmount-guard and double-submit tests, reusing `renderForm`/`fillAndSubmit`.

## Tasks & Acceptance

**Execution:**
- [x] `LoginForm.tsx` -- aria attrs + double-submit guard
- [x] `RegisterForm.tsx` -- aria attrs + double-submit guard
- [x] `QuoteForm.tsx` -- aria attrs + double-submit guard
- [x] `LoginForm.test.tsx` -- unmount-guard test, double-submit test
- [x] `RegisterForm.test.tsx` -- NEW file: happy path, `AUTH_EMAIL_TAKEN`, field errors, generic fallback, unmount-guard test, double-submit test
- [x] `QuoteForm.test.tsx` -- unmount-guard test, double-submit test
- [x] `npm run typecheck && npm test && npm run build` -- clean; only the 5 known pre-existing `LoginForm.test.tsx` failures remain (Node-version issue, untouched by this chore)

**Acceptance Criteria:** see the I/O & Edge-Case Matrix above — this chore has no `epics.md` story, so there is no separate AC section to cross-reference.

## Spec Change Log

- **Finding:** blind-hunter review (iteration 1) — the "Unmount mid-request" I/O matrix row's original assertion ("no console error/warning about a state update on an unmounted component") is trivially true on React 18+, which silently no-ops `setState` after unmount and no longer logs any warning for it. As written, the three unmount tests couldn't have failed even if `cancelledRef` were removed entirely — false confidence, not real coverage.
- **Amended:** split the row into two — `LoginForm` (has an external, spy-able side effect: `saveToken`) gets a real behavioral assertion (`getToken()` still `null` post-resolve); `RegisterForm`/`QuoteForm` (state-only side effects) get an honestly-scoped assertion (resolving post-unmount doesn't throw), with the row's own text now explaining why a stronger assertion isn't available for these two.
- **Known-bad state avoided:** a green test suite falsely implying the unmount guard is verified everywhere, when two of three forms had no real proof.
- **KEEP:** the aria-invalid/aria-describedby pattern, the double-submit guard's placement (before existing state resets), and `RegisterForm.test.tsx`'s new baseline coverage (happy path, `AUTH_EMAIL_TAKEN`, field errors, generic fallback) are all correct as implemented and must survive re-derivation unchanged.

## Design Notes

**Why `id="{input-id}-error"` and not a `useId()`-generated one:** every input already has a stable, human-readable `id` (`login-email`, `quote-driverAge`, etc.) used by existing `htmlFor` labels and tests (`getByLabelText`) — deriving the error id from it keeps the pattern grep-able and consistent with how the codebase already names things, no new ID-generation mechanism needed.

**Why the double-submit guard is a state check, not disabling faster:** the button's `disabled={submitting}` already exists but has a render-timing gap between the click event and React committing the disabled state. A synchronous check at the top of the handler closes that gap regardless of render timing, with no dependency on DOM update speed.

## Verification

**Commands:**
- `cd frontend && npm run typecheck && npm test && npm run build` -- expected: clean; exactly the 5 pre-existing `LoginForm.test.tsx` failures (Node 24 vs CI's pinned Node 20), no new failures -- CONFIRMED: 40/45 passing, exactly the 5 known failures, build clean

## Suggested Review Order

**The hardening pattern itself**

- Entry point: aria wiring, identical pattern across all three forms.
  [`LoginForm.tsx:136`](../../frontend/src/features/auth/LoginForm.tsx#L136)

- The double-submit guard — one line, before any state changes.
  [`LoginForm.tsx:69`](../../frontend/src/features/auth/LoginForm.tsx#L69)

**A finding caught mid-review and corrected (see Spec Change Log)**

- The original unmount-guard test assertion was trivially true on React 18+ — rewritten to spy on the one real side effect this form has (`saveToken`, via `getToken()` staying `null`).
  [`LoginForm.test.tsx:219`](../../frontend/src/features/auth/LoginForm.test.tsx#L219)

**RegisterForm's new baseline (previously zero coverage)**

- Entry point for the entire new test file.
  [`RegisterForm.test.tsx:43`](../../frontend/src/features/auth/RegisterForm.test.tsx#L43)

**Deferred**

- Five findings — guard-by-ref robustness, stale `aria-invalid` on edit, no focus management on error, duplicated boilerplate across the three forms, no `aria-live` on the button label change.
  [`deferred-work.md:245`](../../_bmad-output/implementation-artifacts/deferred-work.md#L245)
