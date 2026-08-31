---
title: 'Story 5.6: Loading & Error-State Polish'
type: 'feature'
created: '2026-08-31'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'ffdd58d00814b5eeed8662a44433ae14a40893e9'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Loading and failure feedback is still invented per screen. Three forms render a bare `<p role="alert" className="text-sm text-danger">`, `RegisterForm`'s success message is coloured by a `[data-testid='register-success']` rule in the legacy stylesheet, `HealthStatus` is the one screen never migrated off the Story 1.1 baseline at all, and nothing anywhere shows a loading indicator — a submit only swaps its button label. Nothing shared owns any of it.

**Approach:** Add the two missing primitives to `components/ui/` — `Spinner` (one loading indicator) and `Alert` (one message banner, variants restricted to AD-6's `success | warning | danger | info`) — and adopt them in every place that currently improvises: the three form-level errors, the register success state, the submit buttons' loading state, and `HealthStatus`. No behaviour change; every pinned `data-testid`, `role`, id, and translated string is preserved.

## Boundaries & Constraints

**Always:** Every existing test passes unmodified — zero edits to existing test files. `login-error`, `register-error`, `quote-error`, `register-success`, and `health-status` keep their exact `data-testid` values, and `health-status` keeps rendering the reachable/unreachable copy verbatim. `HealthStatus` keeps an `<h2>` carrying `app.health.heading`. Submit buttons keep their exact accessible name in both idle and submitting states. `Alert`'s variants are exactly AD-6's four (AD-6). New components render native semantic elements (AD-3) and define variants via cva (AD-2).

**Ask First:** None anticipated.

**Never:** No new i18n keys — the catalogs are untouched (AD-8), so the spinner must not need a translated label of its own. No change to `handleSubmit`, the `cancelledRef` unmount guard, the render-time error-resolution contract, or `HealthStatus`'s fetch/`console.error` behaviour. No `FormField` change — AD-5 makes it the owner of field-level errors and it is already a single shared treatment. No responsive regressions to Story 5.5. No new product functionality.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Form-level failure | any of the three forms errors | Shared `Alert variant="danger"` at the pinned `data-testid`, `role="alert"` | N/A |
| Submit in flight | `phase === 'submitting'` | `Spinner` inside the button; accessible name stays the submitting label alone | N/A |
| Registration succeeds | `phase === 'success'` | Same banner, `variant="success"`, at `data-testid="register-success"` | N/A |
| Health check in flight | `phase === 'checking'` | `Spinner` beside the existing checking copy | N/A |
| Backend unreachable | health check fails | `Alert variant="danger"` at `data-testid="health-status"` | Reason still goes to `console.error`, never to the user (AD-7) |
| Backend reachable | health check succeeds | Plain styled text at `data-testid="health-status"` — deliberately **not** an alert | N/A |
| Field-level error | `fieldErrors.X` set | Unchanged `FormField` inline text (AD-5), not a banner | N/A |

</frozen-after-approval>

## Code Map

- `frontend/src/components/ui/Spinner.tsx` -- new. cva `size` (`sm`/`md`), `animate-spin`, colour from `border-current` so one component works on a navy button and beside muted body text. `aria-hidden="true"`: every caller pairs it with visible text that already states the state, which is what keeps a submit button's accessible name equal to the submitting label alone — and why no i18n key is needed.
- `frontend/src/components/ui/Alert.tsx` -- new. Renders `<div role="alert">`; cva `variant` is exactly AD-6's four. Spreads `...props`, which is what lets callers pass the pinned `data-testid` straight through so existing suites keep passing.
- `frontend/src/components/ui/{Spinner,Alert}.test.tsx` -- new. Cover the contracts nothing else protects: `aria-hidden`, that it actually animates, colour inheritance, testid forwarding, and one distinct treatment per AD-6 variant.
- `frontend/src/features/auth/LoginForm.tsx:158` / `RegisterForm.tsx:148` / `quote/QuoteForm.tsx:217` -- the bare `<p role="alert" …>` becomes `<Alert variant="danger" data-testid="…">`; the submit button gains `<Spinner className="mr-2" />` beside the submitting label (`className` carries spacing only, AD-2).
- `frontend/src/features/auth/RegisterForm.tsx:90` -- success branch becomes `<Alert variant="success" data-testid="register-success">`, replacing the legacy colour rule.
- `frontend/src/app/HealthStatus.tsx:54` -- the last unmigrated screen: `<section><h2>` becomes `<Card title titleAs="h2">`; checking gains a `Spinner`; unreachable becomes `Alert variant="danger"`. Reachable stays plain text on purpose — it renders on load, so `role="alert"` would announce a non-event assertively every visit.
- `frontend/src/index.css:206` -- remove `[role='alert']` and `[data-testid='register-success']`. The colour/size half was already losing to the new component's utilities, but the `margin` half was not, so spacing was being applied to the shared banner from outside it. Everything else in `@layer legacy` is left alone — `dt { font-weight: 600 }` in particular is still live for `QuoteResult`.
- READ-ONLY: `role="alert"` queries in `FormField.test.tsx`/`Input.test.tsx` are scoped to isolated single-component renders, so introducing another alert elsewhere cannot collide. No test asserts `HealthStatus`'s `<section>` or a region landmark.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/components/ui/Spinner.tsx` + test -- the one loading indicator -- FR-9.
- [x] `frontend/src/components/ui/Alert.tsx` + test -- the one message banner, AD-6 vocabulary -- FR-9.
- [x] `frontend/src/features/auth/LoginForm.tsx` -- adopt both -- FR-9.
- [x] `frontend/src/features/auth/RegisterForm.tsx` -- adopt both, including the success state -- FR-9.
- [x] `frontend/src/features/quote/QuoteForm.tsx` -- adopt both -- FR-9.
- [x] `frontend/src/app/HealthStatus.tsx` -- migrate off the legacy baseline; adopt `Card`/`Spinner`/`Alert` -- FR-9.
- [x] `frontend/src/index.css` -- drop the two superseded legacy rules -- FR-9.

**Acceptance Criteria:**
- Given a failure on any of the four screens, when it renders, then it uses the same `Alert` treatment and keeps its pinned `data-testid`.
- Given any in-flight request, when it is pending, then the same `Spinner` renders and the control's accessible name is unchanged from before this story.
- Given the i18n catalogs, when compared before and after, then they are byte-identical.
- Given the full frontend suite, when run, then it is green with no pre-existing test file modified.
- Given 375px, when any touched screen renders, then Story 5.5's guarantees still hold (no horizontal scroll, nothing clipped).

## Design Notes

The split this story deliberately preserves: **field-level** errors stay `FormField`'s compact inline text (AD-5), **form- and screen-level** messages become banners. Collapsing both into `Alert` would put a bordered, filled box under every invalid input and drown the form.

`animate-spin` was the one real technical risk: Story 5.1 imports Tailwind selectively (`theme.css` + `utilities.css`, no preflight), so the `@keyframes spin` the utility references could have been dropped. Verified present in the built stylesheet — `@keyframes spin` emitted, `.animate-spin{animation:var(--animate-spin)}`.

## Verification

**Commands:**
- `cd frontend; npm run typecheck` -- clean.
- `cd frontend; npm test` -- **220 passed / 17 files** (208/15 before; +12 tests in 2 new files). No pre-existing test file modified.
- `cd frontend; npm run build` -- succeeds; `@keyframes spin` present in the emitted CSS.

**Manual checks (done, Browser pane against `npm run dev`, backend intentionally down):**
- `/health` unreachable: renders as a `Card` with the shared danger banner — the last legacy screen is gone.
- `/login` with a stubbed slow 401: spinner inside the button measured `animationName: spin`, `1s`, `border-left-color: rgb(255,255,255)` (inherited white) and `border-top-color: transparent`; button text stayed exactly `Влизане…`. On resolve, the error renders in the same banner treatment as `/health`.
- `/register` field errors: unchanged inline text, `margin: 0` after the legacy rule removal, spacing handled by the surrounding `space-y-*`; `aria-describedby="register-email-error"` and `aria-invalid="true"` intact.
- `/register` success: `role="alert"`, green success variant, same banner shape as the danger one.
- 375px `/health`: no horizontal scroll, banner 309px wide, not clipped — Story 5.5 intact.

## Suggested Review Order

**The two new primitives (everything else is adoption)**

- Entry point: why the spinner is `aria-hidden` — it is what keeps every button's accessible name unchanged and adds no i18n keys.
  [`Spinner.tsx:31`](../../frontend/src/components/ui/Spinner.tsx#L31)
- The banner, with variants locked to AD-6's four and `...props` forwarding the pinned testids.
  [`Alert.tsx:34`](../../frontend/src/components/ui/Alert.tsx#L34)

**Adoption — the judgement calls**

- `HealthStatus`: the last legacy screen, and the one place a status is deliberately *not* an alert.
  [`HealthStatus.tsx:54`](../../frontend/src/app/HealthStatus.tsx#L54)
- A representative form: banner plus in-button spinner.
  [`LoginForm.tsx:158`](../../frontend/src/features/auth/LoginForm.tsx#L158)
- Register's success state reusing the same banner in its success variant.
  [`RegisterForm.tsx:90`](../../frontend/src/features/auth/RegisterForm.tsx#L90)

**Peripheral**

- The legacy rules removed, and why only these two.
  [`index.css:206`](../../frontend/src/index.css#L206)
