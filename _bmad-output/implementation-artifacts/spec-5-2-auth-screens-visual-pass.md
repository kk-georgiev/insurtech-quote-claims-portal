---
title: 'Story 5.2: Auth Screens Visual Pass'
type: 'feature'
created: '2026-08-30'
status: 'done'
review_loop_iteration: 1
context: []
baseline_commit: '9c72c89556131ec50b7e468390b8f09c4845e8ab'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `LoginForm` and `RegisterForm` are the first screens any visitor sees, and they still use the legacy plain HTML/CSS baseline from Story 1.1 — unstyled-looking inputs/buttons, no card treatment beyond legacy's global `main > section` rule. Story 5.1 built the token/component foundation; nothing consumes it yet.

**Approach:** Rebuild both screens' JSX using `Button`, `Input`, `FormField`, `Card` from Story 5.1's library. No behavior change — same validation, same error handling (`resolveFormError`/`resolveFieldErrors`), same submit flow. Every attribute an existing test observes (`id`, `aria-invalid`, `aria-describedby`, `data-testid`, translated text) is preserved exactly.

## Boundaries & Constraints

**Always:** `LoginForm.test.tsx`/`RegisterForm.test.tsx` (and every cross-suite test that renders these screens — `LanguageToggle.test.tsx`, `RootLayout.test.tsx`, `router.test.tsx`, `shells.test.tsx`) pass unmodified — zero test file edits. The exact `id` values (`login-email-error`, `login-password-error`, `register-email-error`, `register-password-error`) and `data-testid` values (`login-error`, `register-error`, `register-success`) are preserved character-for-character, since `LoginForm.test.tsx:168-169`/`295` and `RegisterForm.test.tsx`'s equivalents assert them literally, not just presence. Both forms keep every existing input attribute (`name`, `type`, `autoComplete`, `required`, `minLength`/`maxLength` on the register password) untouched.

**Ask First:** None anticipated.

**Never:** No change to `handleSubmit`, the unmount-guard `cancelledRef` pattern, or the render-time error-resolution contract (`formFailure`/`fieldFailure` held unresolved in state, re-derived every render — the exact regression `LoginForm.test.tsx`'s "error messages follow a language change" tests guard against). No new screens, no routing changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Field-level error present | `fieldErrors.email` set | `Input` shows `invalid`, `FormField` renders the message at the exact pinned `id` | N/A |
| Form-level error present | `formFailure` set | Error renders at the exact pinned `data-testid`, via a component (not a bare `<p>`) | N/A |
| Language switch with a visible error | error on screen, language toggled | Error text re-translates in place, no resubmit (existing contract, must survive the restyle) | N/A |
| Register success | registration succeeds | Success screen (`data-testid="register-success"`) still renders, now inside a `Card` | N/A |

</frozen-after-approval>

## Code Map

- `frontend/src/components/ui/FormField.tsx` -- add an optional `errorId?: string` prop, used (instead of the internally generated `useId()` value) for both the error `<span id>` and the `aria-describedby` it wires onto the control, falling back to the generated id when omitted. Required because `LoginForm`/`RegisterForm`'s existing tests assert the *literal* string `'login-email-error'` etc. (`LoginForm.test.tsx:169`), not just that some id exists — a Story 5.1 gap this story's actual usage surfaces.
- `frontend/src/components/ui/FormField.test.tsx` -- add one test: `errorId` override is used verbatim for both the error element's `id` and the control's `aria-describedby`.
- `frontend/src/features/auth/LoginForm.tsx:121-176` -- replace `<section><h2>...</h2><form>...` with `<Card title={t('auth.login.heading')}><form>...`; each `<div><label><input>` block becomes `<FormField label={...} error={fieldErrors.X} errorId="login-X-error"><Input id="login-X" name="X" ... invalid={Boolean(fieldErrors.X)} /></FormField>`; the form-level error `<p role="alert" data-testid="login-error">` becomes a plain `<p role="alert" data-testid="login-error" className="text-sm text-danger">` (a `role="alert"` announcement banner isn't one of the four base components — kept as styled markup, not a fifth component, per Story 5.1's explicit scope limit); the submit `<button>` becomes `<Button type="submit" disabled={submitting}>`.
- `frontend/src/features/auth/RegisterForm.tsx:85-163` -- identical treatment, plus the success branch (`phase === 'success'`) wrapped in `<Card>` for visual consistency with the rest of the screen.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/components/ui/FormField.tsx` + test -- add `errorId` override -- unblocks preserving LoginForm/RegisterForm's pinned id strings.
- [x] `frontend/src/features/auth/LoginForm.tsx` -- rebuild with `Card`/`FormField`/`Input`/`Button` -- FR-3.
- [x] `frontend/src/features/auth/RegisterForm.tsx` -- rebuild with `Card`/`FormField`/`Input`/`Button`, including the success state -- FR-3.
- [x] `frontend/src/components/ui/Card.tsx` + test -- add `titleAs` prop (review-loop finding) -- lets a screen preserve its own heading level.

**Acceptance Criteria:**
- Given `LoginForm.test.tsx` and `RegisterForm.test.tsx`, when run against the restyled screens, then every test passes unmodified.
- Given every other suite that renders these screens (`LanguageToggle.test.tsx`, `RootLayout.test.tsx`, `router.test.tsx`, `shells.test.tsx`), when run, then all pass unmodified.
- Given a field-level or form-level error, when displayed, then the exact `id`/`data-testid` values existing tests pin are unchanged.
- Given the full frontend suite, when run after this story, then it is 100% green with no test file modified.

## Spec Change Log

- **2026-08-30, review-loop findings (blind-hunter + verification-gap, both independently, iteration 1):** `Card`'s `title` always renders `<h3>`, but `LoginForm`/`RegisterForm` previously used a literal `<h2>` for their screen heading — a silent heading-level demotion (h1 → h3, skipping h2) that no existing test caught, since every heading query on these screens omits `level`. Verification-gap independently confirmed the codebase *does* test heading level elsewhere (the staff shells use `level: 2` checks) via evidence, making this a real, not hypothetical, regression. **Amended:** `Card` gained an optional `titleAs?: 'h2' | 'h3'` prop (default `h3`, unchanged for any other consumer); `LoginForm`/`RegisterForm` pass `titleAs="h2"` to preserve their original heading level exactly. Confirmed live in the browser (`document.querySelector('main h2')` renders `<h2>Log in</h2>`) and via a new `Card.test.tsx` case. **KEEP:** this is still a flat prop, not a compound-component API — does not violate AD-2.

## Design Notes

`Card`'s `title` prop renders an `<h3>`, not the `<h2>` these screens used directly — checked against every test that queries these headings (`getByRole('heading', { name: ... })` across `LanguageToggle.test.tsx`, `RootLayout.test.tsx`, `router.test.tsx`, `shells.test.tsx`): none specify a `level`, so any heading level matches by name alone. Safe.

`FormField` field-block shape (repeated per field):

```tsx
<FormField label={t('auth.login.email')} error={fieldErrors.email} errorId="login-email-error">
  <Input
    id="login-email"
    name="email"
    type="email"
    autoComplete="email"
    required
    value={email}
    onChange={(e) => setEmail(e.target.value)}
    disabled={submitting}
    invalid={Boolean(fieldErrors.email)}
  />
</FormField>
```

The outer `<section>` element is dropped in favor of `Card`'s `<div>` — a deliberate simplification (no landmark-role test depends on `<section>`, and formal accessibility auditing is an explicit PRD non-goal this milestone); revisit only if a later, dedicated accessibility pass adds landmark requirements.

## Verification

**Commands:**
- `cd frontend; npm run typecheck; npm run build; npm test` -- all clean; `LoginForm.test.tsx`/`RegisterForm.test.tsx` and every cross-suite consumer pass with zero test file changes.

**Manual checks:**
- `npm run dev`, view `/login` and `/register` in both Bulgarian and English, submit invalid credentials/a taken email to see the restyled error states. **Done** — confirmed live: `Card`/`FormField`/`Input`/`Button` render correctly (navy pill button, bordered card, red invalid state); `document.querySelector('main h2')` confirms the heading-level fix.

## Suggested Review Order

**The pinned-id preservation (the load-bearing constraint)**

- Entry point: `errorId` override, so `FormField` can honor a caller's exact id string instead of generating one.
  [`FormField.tsx:48`](../../frontend/src/components/ui/FormField.tsx#L48)

- Where it's consumed: each field passes its exact pre-existing id (`login-email-error`, etc.).
  [`LoginForm.tsx:128`](../../frontend/src/features/auth/LoginForm.tsx#L128)

**Heading-level fix (review-loop finding)**

- `Card`'s new `titleAs` prop, defaulting to `h3` for every other consumer.
  [`Card.tsx:25`](../../frontend/src/components/ui/Card.tsx#L25)

- Where a screen overrides it to preserve its own heading level.
  [`LoginForm.tsx:126`](../../frontend/src/features/auth/LoginForm.tsx#L126)

**The two restyled screens**

- `LoginForm`'s full JSX rebuild.
  [`LoginForm.tsx:38`](../../frontend/src/features/auth/LoginForm.tsx#L38)

- `RegisterForm`'s full JSX rebuild, including the success-state `Card`.
  [`RegisterForm.tsx:28`](../../frontend/src/features/auth/RegisterForm.tsx#L28)
