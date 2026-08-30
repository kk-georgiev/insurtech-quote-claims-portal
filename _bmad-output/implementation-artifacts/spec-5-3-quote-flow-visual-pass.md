---
title: 'Story 5.3: Quote Flow Visual Pass'
type: 'feature'
created: '2026-08-30'
status: 'done'
review_loop_iteration: 1
context: []
baseline_commit: '3ef657c'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `QuoteForm` and `QuoteResult` are the first authenticated screen a client sees, and they still use the legacy plain HTML/CSS baseline from Story 1.7 — unstyled inputs/button, and an unstyled `<dl>` breakdown that gives the total no visual weight. Stories 5.1/5.2 established the component library and the restyle pattern; this story applies it to the quote flow.

**Approach:** Rebuild `QuoteForm`'s JSX using `Card`/`FormField`/`Input`/`Button`, exactly mirroring Story 5.2's pattern. Restyle `QuoteResult`'s breakdown with a `Card` wrapper and Tailwind utility classes on the native `<dl>/<dt>/<dd>` structure, giving `totalPremium` clear visual emphasis. No behavior change — same validation, same error handling (`resolveFormError`/`resolveFieldErrors`), same submit flow, same `KNOWN_FIELDS` fallback logic. Every attribute an existing test observes (`id`, `aria-invalid`, `aria-describedby`, `data-testid`, `aria-label`, translated text) is preserved exactly.

## Boundaries & Constraints

**Always:** `QuoteForm.test.tsx` and every cross-suite test that renders these screens (`LanguageToggle.test.tsx`, `shells.test.tsx` insofar as it nests `QuoteForm`) pass unmodified — zero test file edits. The exact `id` values (`quote-driverAge-error`, `quote-regionCode-error`, `quote-engineCc-error`, `quote-installments-error`) and `data-testid` values (`quote-error`, `quote-result`, `quote-zoneName`, `quote-basePremium`, `quote-ageSurcharge`, `quote-oneTimePremium`, `quote-installments`, `quote-installmentFee`, `quote-totalPremium`, `quote-installmentAmount`) are preserved character-for-character. `QuoteResult`'s outer `<section data-testid="quote-result" aria-label={t('quote.result.label')}>` keeps that exact tag, testid, and `aria-label` — `LanguageToggle.test.tsx:262-270` asserts its accessible name literally via `toHaveAccessibleName`. Every existing input attribute (`name`, `type`, `min`, `max`, `required`) is untouched — `QuoteForm.test.tsx` asserts `min`/`max` literally (e.g. `toHaveAttribute('max', '100')`).

**Ask First:** None anticipated.

**Never:** No change to `handleSubmit`, the unmount-guard `cancelledRef` pattern, the `KNOWN_FIELDS` fallback-to-generic-error logic, or the render-time error-resolution contract (`formFailure`/`fieldFailure` held unresolved in state, re-derived every render). No change to how `QuoteResult` interpolates money (exactly as the API returns it — no re-derivation, re-rounding, or re-formatting client-side). No new screens, no routing changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Field-level error present | `fieldErrors.X` set | `Input` shows `invalid`, `FormField` renders the message at the exact pinned `id`, `aria-describedby` wired | N/A |
| Form-level error present (known-field or generic) | `formFailure` set | Error renders at the exact pinned `data-testid="quote-error"`, via styled markup (not a bare unstyled `<p>`) | N/A |
| Unknown field name in `fieldErrors` (`KNOWN_FIELDS` miss) | backend names a field this form doesn't render | Both field state AND form-level error set — unchanged fallback logic, untouched by restyle | N/A |
| Language switch with a visible error | error on screen, language toggled | Error text re-translates in place, no resubmit | N/A |
| Successful quote | `POST /api/v1/quotes` succeeds | `QuoteResult` renders inside a `Card`, `totalPremium` visually emphasized, all 8 `data-testid` values and the `<dl>/<dt>/<dd>` structure unchanged | N/A |
| Resubmit after a result is shown | user submits again | `quote` cleared to `null` before the request, exactly as today | N/A |

</frozen-after-approval>

## Code Map

- `frontend/src/features/quote/QuoteForm.tsx:136-238` -- replace `<section><h2>...</h2><form>...{quote && <QuoteResult .../>}</section>` with `<Card title={t('quote.form.heading')} titleAs="h2"><form>...</form>{quote && <QuoteResult quote={quote} />}</Card>` (the outer `<section>` is dropped in favor of `Card`'s own div, same precedent as Story 5.2 -- no landmark-role test depends on it, confirmed by grep). Each of the 4 field blocks (`driverAge`, `regionCode`, `engineCc`, `installments`) becomes `<FormField label={...} error={fieldErrors.X} errorId="quote-X-error"><Input id="quote-X" name="X" ... invalid={Boolean(fieldErrors.X)} /></FormField>`, preserving every existing `<input>` attribute (`type`, `min`, `max`, `required`) via prop spread. The form-level error `<p role="alert" data-testid="quote-error">` becomes `<p role="alert" data-testid="quote-error" className="text-sm text-danger">` (same treatment as `login-error`/`register-error` -- styled markup, not a fifth component). The submit `<button>` becomes `<Button type="submit" disabled={submitting}>`.
- `frontend/src/features/quote/QuoteResult.tsx:14-63` -- keep the outer `<section data-testid="quote-result" aria-label={t('quote.result.label')}>` exactly as-is (tag, testid, aria-label untouched -- load-bearing per `LanguageToggle.test.tsx`), but wrap its content in `<Card title={t('quote.result.heading')}>` (default `titleAs="h3"`, matching the original literal `<h3>` -- no heading-level change needed here, unlike Story 5.2's auth screens). Restyle the `<dl>` with Tailwind utility classes (`grid grid-cols-2 gap-x-4 gap-y-2` on the `dl`, muted text on `dt`, right-aligned/tabular values on `dd`) and give the `totalPremium` row visual emphasis (larger/bold text, top border, extra padding) to read as "the clear total" per the story's own AC -- all 8 `data-testid` values and the native `<dl>/<dt>/<dd>` tag structure unchanged (AD-3, and no test depends on a tag change).
- `frontend/src/index.css` -- no changes needed: the legacy `[data-testid='quote-result']`/`main > section` rules live in the `legacy` layer, which the Tailwind cascade order (`legacy, theme, base, components, utilities`) already places below `utilities` -- the new classes on `QuoteResult`'s children win without editing legacy CSS, same as Story 5.2 left `index.css` untouched.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/features/quote/QuoteForm.tsx` -- rebuild with `Card`/`FormField`/`Input`/`Button` -- FR-4 (Milestone 2 PRD).
- [x] `frontend/src/features/quote/QuoteResult.tsx` -- wrap in `Card`, restyle the `<dl>` breakdown with total emphasis -- FR-4.

**Acceptance Criteria:**
- Given `QuoteForm.test.tsx`, when run against the restyled screen, then every test passes unmodified.
- Given every other suite that renders these screens (`LanguageToggle.test.tsx`, `shells.test.tsx`), when run, then all pass unmodified.
- Given a field-level or form-level error, when displayed, then the exact `id`/`data-testid` values existing tests pin are unchanged.
- Given a successful quote, when `QuoteResult` renders, then `totalPremium` is visually distinguished from the other breakdown rows.
- Given the full frontend suite, when run after this story, then it is 100% green with no test file modified.

## Design Notes

`QuoteForm`'s field-block shape (repeated per field, mirroring Story 5.2's `LoginForm` pattern):

```tsx
<FormField label={t('quote.form.driverAge')} error={fieldErrors.driverAge} errorId="quote-driverAge-error">
  <Input
    id="quote-driverAge"
    name="driverAge"
    type="number"
    min={18}
    max={100}
    required
    value={driverAge}
    onChange={(event) => setDriverAge(event.target.value)}
    disabled={submitting}
    invalid={Boolean(fieldErrors.driverAge)}
  />
</FormField>
```

`QuoteResult`'s breakdown, with `totalPremium` visually distinguished from the other 7 rows:

```tsx
<section data-testid="quote-result" aria-label={t('quote.result.label')}>
  <Card title={t('quote.result.heading')}>
    <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
      <dt className="text-text-muted">{t('quote.result.zone')}</dt>
      <dd data-testid="quote-zoneName" className="text-right">...</dd>
      {/* ...remaining rows... */}
      <dt className="col-span-2 mt-2 border-t border-border pt-2 text-base font-semibold text-text">
        {t('quote.result.totalPremium')}
      </dt>
      <dd data-testid="quote-totalPremium" className="col-span-2 text-right text-base font-semibold text-text">
        ...
      </dd>
    </dl>
  </Card>
</section>
```

Grid layout is a styling choice only -- `dt`/`dd` stay adjacent siblings in the same order, so no test that walks the DOM by testid or text content is affected.

## Verification

**Commands:**
- `cd frontend; npm run typecheck; npm run build; npm test` -- all clean; **207/207 tests pass**, `QuoteForm.test.tsx` and every cross-suite consumer pass with zero test file changes.

**Manual checks:**
- **Done** -- confirmed live: registered a client, logged in, and drove the full quote flow at `/` (client shell). Submitted an unknown region code -- `Input` shows the red invalid border, `FormField` renders the pinned-id error message. Corrected the region and resubmitted -- `QuoteResult` renders inside its own `Card`, "Total premium" clearly set apart from the other 7 rows (bold, top border, own block), matching the Design Notes mock. Verified via `document.querySelector('[data-testid="quote-result"]')` in the live page that the tag (`SECTION`), `aria-label`, and all `data-testid` values survived unchanged.

## Suggested Review Order

**The pinned-id/testid preservation (the load-bearing constraint)**

- `QuoteForm`'s field blocks, each passing its exact pre-existing id via `FormField`'s `errorId` override.
  [`QuoteForm.tsx`](../../frontend/src/features/quote/QuoteForm.tsx)

- `QuoteResult`'s outer `<section>` -- tag, `data-testid`, and `aria-label` unchanged, since `LanguageToggle.test.tsx` asserts its accessible name literally.
  [`QuoteResult.tsx`](../../frontend/src/features/quote/QuoteResult.tsx)

**The two restyled screens**

- `QuoteForm`'s full JSX rebuild.
  [`QuoteForm.tsx`](../../frontend/src/features/quote/QuoteForm.tsx)

- `QuoteResult`'s `Card` wrap and `totalPremium` emphasis.
  [`QuoteResult.tsx`](../../frontend/src/features/quote/QuoteResult.tsx)

**Review-loop fix (verification-gap finding)**

- Explicit `titleAs="h3"` on `QuoteResult`'s `Card` -- self-documenting, guards against a future change to `Card`'s default.
  [`QuoteResult.tsx`](../../frontend/src/features/quote/QuoteResult.tsx)

## Spec Change Log

- **2026-08-30, review-loop finding (verification-gap, iteration 1):** `QuoteResult`'s `Card` call passed no `titleAs`, relying silently on `Card`'s default (`h3`) to match the original literal `<h3>` heading. The default happens to be correct today, but nothing made that explicit or guarded it from a future change to `Card`'s default -- the exact same unguarded shape that caused a real, silent heading-level regression in Story 5.2 (there, the default was *wrong* for `LoginForm`/`RegisterForm`). **Amended:** `QuoteResult.tsx` now passes `titleAs="h3"` explicitly, matching `QuoteForm.tsx`'s adjacent explicit `titleAs="h2"` call. No behavior change (h3 was already the effective output); self-documenting and regression-proof against a future default change. Verified via `npm test` (207/207 still green) after the change.
- **2026-08-30, review-loop findings considered and rejected (blind-hunter, iteration 1):** Ten findings raised (Card-in-Card visual nesting, `<section>`→`<div>` landmark loss on `QuoteForm`, `totalPremium` row spanning both grid columns, error-text size mismatch between form-level/field-level messages, conditional-title empty-heading edge case, label-wrapped number-input spinner quirk, vertical-spacing stack-up, region/heading label duplication, missing `aria-live` on the result panel, and the Card-in-Card structural asymmetry vs. `QuoteForm`). All ten are either (a) tradeoffs already made and accepted in Story 5.2 for the identical pattern (dropping the bare `<section>` for `Card`'s div; the `text-sm`/`text-xs` error-size split; accessibility auditing explicitly deferred as a milestone non-goal), (b) pre-existing behavior inherited unchanged from the Story 5.1 component library (conditional title rendering, aria-label/heading duplication), or (c) confirmed non-issues via the live browser check (the `totalPremium` two-line block renders as the intended emphasized summary, not a broken row; the Card-in-Card nesting reads as a clean divider, not a jarring double-border box, since both cards share the same `bg-surface`/light-`border-border` palette). No code changes made for these. `edge-case-hunter` (iteration 1) returned no findings.
