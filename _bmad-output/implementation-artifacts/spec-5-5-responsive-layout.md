---
title: 'Story 5.5: Responsive Layout'
type: 'feature'
created: '2026-08-31'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'f7085b1fa2ea7dfb104b0d6b7ec267815e92f7b5'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Milestone 1 explicitly excluded responsive layout, and Stories 5.2–5.4 restyled every screen at desktop width only. Measured at 375px before this story: nested `Card` padding consumed 96px of the viewport so `QuoteResult`'s two columns collapsed to 106px and nearly every Bulgarian label wrapped; header nav links and language pills were 20px tall; `Input`'s 14px font triggers iOS Safari's focus auto-zoom, which pans the page sideways.

**Approach:** Mobile-first utilities on the existing component library and layout shell, each paired with an `sm:` restore so every desktop rendering is byte-identical to Story 5.4. Padding and type step down below `sm`; interactive controls clear a 44px tap target below `sm`. Tailwind's default breakpoint scale only — `sm:` is the single breakpoint used, so the "no custom breakpoints" AC needs no documented exception.

## Boundaries & Constraints

**Always:** Every existing test passes unmodified — zero test-file edits. Desktop (≥640px) rendering is unchanged from Story 5.4: every mobile utility is paired with an `sm:` counterpart restoring the original value. Responsive rules live in the component's own cva/variant definition where one exists (AD-2), never leaked into a screen's `className`. Only Tailwind's default breakpoint scale (AD-1, FR-8's second clause).

**Ask First:** None anticipated.

**Never:** No behaviour change — routes, `RoleGuard`, auth, logout, translations, test IDs, and semantic HTML untouched. No custom breakpoints. No new components. No shared spinner/error-banner pattern (Story 5.6). No new product functionality. Do not weaken tests.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Any route at 375px | mobile viewport | `documentElement.scrollWidth === clientWidth`; no element's box extends past the viewport | N/A |
| Quote breakdown at 375px | successful quote rendered | Nested `Card`s fit; no `dt`/`dd` clipped (`scrollWidth <= width`) | N/A |
| Header controls at 375px | logged in and logged out | Every nav link, language pill, and button ≥44px tall | N/A |
| Text input focus on iOS | `Input` focused below `sm` | Font-size ≥16px so Safari does not auto-zoom the viewport | N/A |
| Any route at ≥640px | desktop viewport | Padding, type, and control sizes identical to Story 5.4 | N/A |
| Extra-narrow 320px | full quote flow rendered | Still no horizontal scroll and no clipped cells | N/A |

</frozen-after-approval>

## Code Map

- `frontend/src/components/ui/Card.tsx:29` -- `p-6` → `p-4 sm:p-6`. The load-bearing fix: `QuoteResult`'s `Card` nests inside `QuoteForm`'s, so padding is paid twice. Measured effect at 375px — breakdown columns 106px → 130px, two more labels stop wrapping.
- `frontend/src/components/ui/Input.tsx:7` -- `text-sm` → `text-base sm:text-sm`. Below 16px iOS Safari auto-zooms on focus and pans the page, the exact horizontal-scroll failure FR-8 rules out.
- `frontend/src/components/ui/Button.tsx:20` -- both `size` variants gain `min-h-11 sm:min-h-0`. Placed in the cva `size` definition (AD-2's sanctioned home for variant styling) so no screen needs a `className` override to get a mobile-sized button.
- `frontend/src/app/RootLayout.tsx:48` -- `navLinkClass` becomes `inline-flex min-h-11 items-center … sm:min-h-0`; the links carry no background so the extra mobile height is invisible. Header/`main` padding step down (`px-4 py-3 sm:px-6 sm:py-4`, `px-4 py-6 sm:px-6 sm:py-10`); `h1` `text-base sm:text-lg`; the nav/toggle wrapper gaps tighten below `sm` so nav + toggle share one row when authenticated (header 219px → 163px at 375px).
- `frontend/src/app/LanguageToggle.tsx:56` -- pills gain `inline-flex min-h-11 items-center … sm:min-h-0` and `px-3 sm:px-2.5`. Styled directly rather than through a primitive for the reason Story 5.4 recorded: a two-option segmented toggle is not one of the four base components.
- `frontend/src/app/RootLayout.tsx:53` -- header container **deliberately stays** `max-w-5xl`. Narrowing it to `max-w-2xl` to align with the content column was tried and reverted: it wraps the long Bulgarian title on desktop, doubling header height 84px → 130px. The misalignment is cosmetic and stays deferred.
- READ-ONLY: `Card.test.tsx` / `Input.test.tsx` / `Button.test.tsx` assert `rounded-lg`, `border-danger`, `rounded-full` and `className`-merge behaviour — none pins a padding or type class, so the variant edits are safe.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/components/ui/Card.tsx` -- `p-4 sm:p-6` -- reclaims 64px per nested pair on phones -- FR-8.
- [x] `frontend/src/components/ui/Input.tsx` -- `text-base sm:text-sm` -- prevents iOS focus auto-zoom -- FR-8.
- [x] `frontend/src/components/ui/Button.tsx` -- `min-h-11 sm:min-h-0` on both sizes -- 44px mobile tap target -- FR-8.
- [x] `frontend/src/app/RootLayout.tsx` -- responsive padding/type, 44px nav links, tighter mobile gaps -- FR-8.
- [x] `frontend/src/app/LanguageToggle.tsx` -- 44px mobile pills -- FR-8.

**Acceptance Criteria:**
- Given any restyled screen at 375px, when measured, then `scrollWidth === clientWidth` and no element overflows the viewport.
- Given the quote breakdown at 375px and 320px, when rendered, then no `dt`/`dd` is clipped.
- Given the header at 375px, when measured, then every link, pill, and button is ≥44px tall.
- Given any screen at ≥640px, when measured, then padding, type sizes, and control heights match Story 5.4 exactly.
- Given the built stylesheet, when inspected, then only Tailwind's default breakpoints appear.
- Given the full frontend suite, when run, then it is green with no test file modified.

## Design Notes

Every rule is mobile-first with an explicit `sm:` restore, which is what makes "desktop is unchanged" verifiable by inspection rather than by screenshot diffing:

```tsx
// tap target below sm, original desktop proportions from sm up
size: {
  md: 'min-h-11 px-4 py-2 text-sm sm:min-h-0',
  sm: 'min-h-11 px-3 py-1.5 text-xs sm:min-h-0',
}
```

Deliberately **not** done: collapsing `QuoteResult`'s `grid-cols-2` to one column on phones. Label-above-value doubles the breakdown's height and breaks the side-by-side reading a financial summary depends on; widening the columns via `Card` padding solved the cramping without that cost.

## Verification

**Commands:**
- `cd frontend; npm run typecheck` -- clean.
- `cd frontend; npm test` -- **208 passed / 15 files**; no test file modified.
- `cd frontend; npm run build` -- succeeds. Emitted media queries are `40rem / 48rem / 64rem / 80rem / 96rem` — Tailwind's default scale, no custom breakpoint.

**Manual checks (done, Browser pane at emulated viewports):**
- **375px, every route** (`/login`, `/register`, `/health`, `/` CLIENT, `/administrator`): `scrollWidth === clientWidth === 375`, zero overflowing elements, zero controls under 40px.
- **375px quote breakdown** (API stubbed to render the real component): columns 106px → **130px**, no clipped cells.
- **320px** with the full quote result: still no horizontal scroll, no overflow, no clipped cells.
- **Header at 375px**: 219px → **163px** tall; Logout / Health / both language pills all measured **44px**.
- **1280px desktop**: nav link 20px, pill 20px, input 14px/38px, `Card` padding 24px, submit 36px, header 84px — identical to Story 5.4.

## Suggested Review Order

**The component-library rules (highest leverage — they fix every screen at once)**

- Entry point: nested-`Card` padding, the measured root cause of the cramped breakdown.
  [`Card.tsx:29`](../../frontend/src/components/ui/Card.tsx#L29)
- The iOS auto-zoom guard, easy to mistake for a cosmetic type tweak.
  [`Input.tsx:7`](../../frontend/src/components/ui/Input.tsx#L7)
- Tap-target rule kept inside the cva `size` variant rather than pushed to callers (AD-2).
  [`Button.tsx:20`](../../frontend/src/components/ui/Button.tsx#L20)

**Layout shell**

- Responsive padding/type plus the invisible 44px link target.
  [`RootLayout.tsx:48`](../../frontend/src/app/RootLayout.tsx#L48)
- The reverted alignment change, with the reason it stays deferred.
  [`RootLayout.tsx:53`](../../frontend/src/app/RootLayout.tsx#L53)
- Mobile-sized language pills.
  [`LanguageToggle.tsx:56`](../../frontend/src/app/LanguageToggle.tsx#L56)
