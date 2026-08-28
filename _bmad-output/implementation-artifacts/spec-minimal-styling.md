---
title: 'Minimal Styling'
type: 'chore'
created: '2026-08-28'
status: 'done'
route: 'one-shot'
review_loop_iteration: 0
context: []
---

# Minimal Styling

## Intent

**Problem:** Every screen (LoginForm, RegisterForm, QuoteForm/QuoteResult, the role shells) is unstyled HTML — functional but not readable enough even for a mentor demo. Visual design is explicitly a PRD non-goal for this milestone, so this stays baseline-only, not a real UI pass.

**Approach:** One global `frontend/src/index.css`, imported once in `main.tsx`, targeting existing semantic elements and attributes (`role="alert"`, `data-testid`) already present in the markup — zero JSX/className changes anywhere else.

## Suggested Review Order

- Entry point: reset + base typography/color.
  [`index.css:7`](../../frontend/src/index.css#L7)

- The card/nesting fix (post-review) — `QuoteForm`'s own `<section>` sits nested inside `ClientShell`'s, one level deeper than `LoginForm`/`RegisterForm`'s. Card styling targets only the outermost section under `main`; anything nested flows as part of the same card instead of duplicating chrome.
  [`index.css:73`](../../frontend/src/index.css#L73)

- The one nested block that does need separation — the quote breakdown — targeted by `data-testid`, not nesting depth, so it doesn't break if a shell's structure changes.
  [`index.css:97`](../../frontend/src/index.css#L97)

- Forms: inputs, focus states, disabled states (extended post-review to `select`/`textarea` for forward-compat).
  [`index.css:120`](../../frontend/src/index.css#L120)

- Errors (post-review: bottom margin added so a form-level error, a direct `<form>` child, doesn't sit flush against the submit button).
  [`index.css:175`](../../frontend/src/index.css#L175)

- Four findings deferred (input error-state border, bare-selector scoping fragility, skip-to-content link, required-field indicator) — each needs either a design decision or a JSX change this CSS-only pass deliberately avoided.
  [`deferred-work.md:221`](../../_bmad-output/implementation-artifacts/deferred-work.md#L221)
