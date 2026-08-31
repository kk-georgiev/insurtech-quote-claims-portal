---
title: 'Story 5.1: Design Tokens & Base Component Library'
type: 'feature'
created: '2026-08-30'
status: 'done'
review_loop_iteration: 1
context: []
baseline_commit: '4a7690b8aceefd7450751ca5ff6f636cfa216169'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The frontend has no design-token source and no shared component library — every screen hardcodes its own colors/spacing via a global, element-selector-based `index.css` (Story 1.1's "no framework" baseline). Epic 5's later stories (5.2–5.6) need a single token source and a small library to restyle from, or each screen reinvents its own look.

**Approach:** Add Tailwind CSS v4 (CSS-first, `@theme` tokens in `frontend/src/index.css`) and build four native-element components — `Button`, `Input`, `FormField`, `Card` — in `frontend/src/components/ui/`, using `class-variance-authority` for variants. No existing screen is touched or restyled by this story; it only builds the foundation Stories 5.2–5.6 consume.

## Boundaries & Constraints

**Always:** Tailwind v4 CSS-first only — `@theme` block in `frontend/src/index.css`, no `tailwind.config.js` (AD-1). Every custom token is semantically named, never a bare scale re-derivation. Components render real native elements (`<button>`, `<input>`, a real `<label>`) — never a styled non-semantic stand-in (AD-3). Variant props are named `variant`/`size` uniformly. `className` overrides merge through a `cn()` helper (`clsx` + `tailwind-merge`) and may only carry spacing/sizing/positioning utilities (AD-2). `FormField` owns `error?: string`; `Input` owns only a boolean `invalid` for styling (AD-5). `Card` uses flat props only, never a compound-component API (AD-2).

**Ask First:** None anticipated — every technical decision here was already fixed by the architecture spine.

**Never:** Do not restyle any existing screen (Login, Register, Quote, RootLayout, shells) — that is Stories 5.2–5.4. Do not remove or rewrite the existing legacy CSS rules in `index.css` — only isolate them into their own cascade layer so they don't win over the new components once adopted. No `Badge`/status component, no icon library, no Storybook — all explicitly out of scope per the PRD.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| `Button` with a `variant` prop | `variant="primary"` vs `variant="secondary"` | Renders a real `<button>`; the two variants produce visibly different, non-overlapping class sets | N/A |
| `Input` with `invalid` | `invalid={true}` vs `invalid={false}`/omitted | Renders a real `<input>`; only the invalid case carries the error-visual class | N/A |
| `FormField` with/without `error` | `error="…"` vs `error` omitted | Renders a real `<label>` + its control; error text renders only when `error` is provided | N/A |
| `className` override on any component | e.g. `className="mt-4"` | Merged via `cn()` without dropping the component's own variant classes | N/A |
| Legacy global CSS vs. a `components/ui/` element | A `components/ui/` component renders a `<button>`/`<input>` | The component's own Tailwind styling wins over the pre-existing bare-element rules in `index.css` | N/A |

</frozen-after-approval>

## Code Map

- `frontend/package.json` -- add `tailwindcss`, `@tailwindcss/vite` (dev deps) and `class-variance-authority`, `clsx`, `tailwind-merge` (deps) -- the exact versions verified in the architecture spine's Stack table.
- `frontend/vite.config.ts:1-15` -- import `@tailwindcss/vite` and add `tailwindcss()` to the `plugins` array alongside `react()`.
- `frontend/src/index.css` -- add `@import "tailwindcss";` and an `@theme` block defining the navy/accent palette, Inter font family, and spacing/type scale (PRD §4). Wrap ALL of the existing element-selector rules (everything currently in this file, lines 7-241) in an explicit `@layer legacy` block, and declare layer order (`@layer legacy, tailwind-utilities;` or equivalent Tailwind v4 layer-ordering syntax) so Tailwind's utility layer outranks it -- otherwise the legacy bare `button { background: #2563eb }`-style rules (unlayered by default) would keep winning over the new components' Tailwind classes by CSS layer precedence, silently defeating this story's own purpose the moment Story 5.2 adopts `Button`.
- `frontend/index.html:6` -- add a Google Fonts `<link>` for Inter (variable font), per the architecture spine's typeface decision.
- `frontend/src/components/ui/cn.ts` -- new. `cn(...inputs: ClassValue[]): string` combining `clsx` + `tailwind-merge` -- the one merge helper every component below uses for `className` props.
- `frontend/src/components/ui/Button.tsx` -- new. cva-based `variant`/`size`, renders a real `<button type="button">` by default (accepts `type` override for `submit`).
- `frontend/src/components/ui/Input.tsx` -- new. Renders a real `<input>`; `invalid?: boolean` prop drives only visual (border/ring) styling via cva.
- `frontend/src/components/ui/FormField.tsx` -- new. Renders a real `<label>` wrapping its `children` (the control), plus `error?: string` rendered below when present.
- `frontend/src/components/ui/Card.tsx` -- new. Flat props: `title?: string`, `footer?: ReactNode`, `children`.
- `frontend/src/components/ui/*.test.tsx` -- new, one per component, covering the I/O matrix.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/package.json` -- add the 5 new dependencies -- gives the project the exact stack the architecture spine pinned.
- [x] `frontend/vite.config.ts` -- wire the `@tailwindcss/vite` plugin -- makes Tailwind actually process on build/dev.
- [x] `frontend/src/index.css` -- add `@theme` tokens + layer the legacy CSS -- single token source, and protects new components from being silently overridden.
- [x] `frontend/index.html` -- add the Inter Google Fonts link -- makes the chosen typeface actually available.
- [x] `frontend/src/components/ui/cn.ts` -- add the merge helper -- shared by every component below.
- [x] `frontend/src/components/ui/Button.tsx` + test -- first component, proves the cva pattern.
- [x] `frontend/src/components/ui/Input.tsx` + test -- proves the `invalid`-boolean-only contract (AD-5).
- [x] `frontend/src/components/ui/FormField.tsx` + test -- proves the `error`-message-ownership contract (AD-5).
- [x] `frontend/src/components/ui/Card.tsx` + test -- proves the flat-props contract (AD-2).

**Acceptance Criteria:**
- Given the `@theme` block in `index.css`, when any of the four new components render, then every color/font/spacing value they use resolves to a token defined there — no hardcoded hex or inline `font-family` in any new file.
- Given `Button`, `Input`, `FormField`, `Card`, when instantiated with different `variant`/`invalid`/`error` inputs, then each renders its correct native semantic element and the correct visual/behavioral state, per the I/O matrix.
- Given the full existing frontend test suite (all screens, unaffected by this story), when run after these changes, then it still passes unmodified — this story adds files and touches shared config only, it does not change any existing screen's behavior.
- Given `npm run build`, when run, then it completes successfully with Tailwind wired in.

## Spec Change Log

- **2026-08-30, review-loop finding (verification-gap, iteration 1):** the original Design Notes' layer declaration (`@layer legacy, base, components, utilities;`) named Tailwind's own internal layers (`base`/`components`/`utilities`) ourselves, which collides with Tailwind's own `@layer theme, base, components, utilities;` registered by `@import "tailwindcss"` — this re-slots Tailwind's Preflight resets (in `base`) ABOVE `legacy`, silently breaking every existing screen's appearance immediately, contradicting this story's own "no screen is restyled yet" boundary. **First attempted fix (`@layer legacy;` standalone before the plain `@import "tailwindcss"`) turned out to be insufficient** — manual browser verification after that change showed the button/input styling was STILL broken, because `legacy` declared first is *lowest* priority, so Preflight (in the later-declared `base`) still won regardless. There is no layer-order arrangement that makes `legacy` beat Preflight for old markup AND lose to Tailwind utility classes for new `components/ui/` markup, when both target the exact same bare element types (`button`, `input`) — layer priority overrides specificity entirely, so ordering can only pick one winner per element type, not per "old vs. new" intent. **Final fix:** import Tailwind's `theme.css` and `utilities.css` selectively, skipping `preflight`/`base` entirely (Tailwind's own documented pattern for adopting Tailwind inside an existing stylesheet) — `legacy`'s own pre-existing reset already covers what Preflight would have done, so there is no competing global reset, and `utilities` (highest layer) still wins for any element using a `components/ui/` primitive. Confirmed live in the browser: existing screens render byte-for-byte as before (button `#2563eb`/6px radius/etc. computed-style-verified), and a scratch render of `Button`/`Input`/`FormField`/`Card` shows correct Tailwind styling with no console errors. **KEEP:** the rest of the plan (token block shape, four-component set, cva/cn pattern) needed no change — this was purely a CSS-import-strategy error, not a design flaw.
- **2026-08-30, review-loop findings (edge-case-hunter + blind-hunter, iteration 1):** `Input`'s `invalid` prop had no corresponding `aria-invalid`, and `FormField`'s error text had no `aria-describedby` link to its control — both real accessibility gaps in foundational components every later story depends on, cheap to close now. **Amended:** `Input` sets `aria-invalid` from `invalid`; `FormField` wires `aria-describedby` onto a single-element `children` via `useId`/`cloneElement` when `error` is present. `Button` also gained a `focus-visible` ring (was previously unstyled for keyboard focus). Implementing the `aria-describedby` fix surfaced a second, self-caught bug: `FormField`'s error `<span>` was nested *inside* the `<label>`, so a `<label>`'s accessible-name-from-content computation folded the error text into the field's label name itself (breaking `getByLabelText('Email')`-style queries and conflating "label" with "error" for assistive tech) — fixed by moving the error text to a sibling `<div>` wrapper alongside the `<label>`, linked only via `aria-describedby`. **KEEP:** component prop shapes (`variant`/`size`/`invalid`/`error`) are unchanged — these were additive fixes, not shape changes.

## Design Notes

`Button` shape (the pattern `Input`/`Card` follow):

```tsx
const buttonVariants = cva('inline-flex items-center justify-center rounded-full font-medium transition-colors', {
  variants: {
    variant: { primary: 'bg-primary text-white hover:bg-primary-dark', secondary: 'bg-transparent border border-primary text-primary' },
    size: { md: 'px-4 py-2 text-sm', sm: 'px-3 py-1.5 text-xs' },
  },
  defaultVariants: { variant: 'primary', size: 'md' },
});

export function Button({ className, variant, size, ...props }: ButtonProps) {
  return <button className={cn(buttonVariants({ variant, size }), className)} {...props} />;
}
```

`FormField`/`Input` error-ownership split (AD-5):

```tsx
// FormField owns the message:
<FormField label={t('quote.form.driverAge')} error={fieldErrors.driverAge}>
  <Input invalid={Boolean(fieldErrors.driverAge)} {...} />
</FormField>
```

Tailwind v4 layering, in `index.css`. `legacy` must be declared **standalone**, before `@import "tailwindcss"` registers its own `theme, base, components, utilities` layers — naming `base`/`components`/`utilities` ourselves in that same declaration (as an earlier draft of this spec incorrectly showed) collides with Tailwind's own layer names and re-slots Tailwind's Preflight resets (in `@layer base`) ABOVE `legacy`, breaking every existing screen's look immediately:

```css
@layer legacy;

@import "tailwindcss";

@theme {
  --color-primary: #2A2859;
  /* ...accent, Inter, spacing/type scale per PRD §4 */
}

@layer legacy {
  /* existing lines 7-241, unchanged */
}
```

## Verification

**Commands:**
- `cd frontend; npm run typecheck; npm run build; npm test` -- all clean; new component tests pass alongside the full unmodified existing suite (182 tests going in).

**Manual checks:**
- `npm run dev`, temporarily render each new component on a scratch page (or via a quick unused import) to confirm Tailwind classes actually apply and the legacy `@layer` doesn't silently win — revert the scratch render before finishing, since no real screen consumes these components until Story 5.2. **Done** — confirmed live: existing screens computed-style-verified unchanged (`button` background `rgb(37,99,235)`/6px radius, matching the pre-Tailwind legacy values exactly), and a scratch render of all four components showed correct Tailwind styling with no console errors.

## Suggested Review Order

**CSS import strategy (the load-bearing fix)**

- Entry point: Tailwind imported selectively (theme + utilities, no Preflight) to avoid the reset-vs-reset conflict two review-loop iterations found.
  [`index.css:14`](../../frontend/src/index.css#L14)

- The `@theme` token block PRD §4 traces to — the single source every component below consumes.
  [`index.css:25`](../../frontend/src/index.css#L25)

- The pre-existing legacy CSS, now isolated in its own layer, unchanged in content.
  [`index.css:63`](../../frontend/src/index.css#L63)

- The plugin that makes Tailwind actually process on build/dev.
  [`vite.config.ts:4`](../../frontend/vite.config.ts#L4)

**Component library**

- `Button`: the cva variant pattern every other component follows, plus the review-added `focus-visible` ring.
  [`Button.tsx:36`](../../frontend/src/components/ui/Button.tsx#L36)

- `Input`: the `invalid`-boolean-only contract (AD-5) and its review-added `aria-invalid`.
  [`Input.tsx:26`](../../frontend/src/components/ui/Input.tsx#L26)

- `FormField`: the error-message-ownership contract (AD-5) — note the error `<span>` sits *outside* the `<label>`, a review-loop fix for label-name pollution.
  [`FormField.tsx:36`](../../frontend/src/components/ui/FormField.tsx#L36)

- `Card`: the flat-props contract (AD-2), no compound-component API.
  [`Card.tsx:15`](../../frontend/src/components/ui/Card.tsx#L15)

**Peripherals**

- Google Fonts Inter link.
  [`index.html:6`](../../frontend/index.html#L6)

- New dependencies pinned to the architecture spine's exact versions.
  [`package.json:17`](../../frontend/package.json#L17)

