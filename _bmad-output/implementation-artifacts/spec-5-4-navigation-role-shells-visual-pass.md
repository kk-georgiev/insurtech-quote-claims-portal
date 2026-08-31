---
title: 'Story 5.4: Navigation & Role Shells Visual Pass'
type: 'feature'
created: '2026-08-31'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '5740af857d3370fc51f94d36fdc16e80394eb3f8'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `RootLayout` (header/nav, Logout/Register/Login swap, `LanguageToggle`) and the four role shells (`ClientShell` + the three staff placeholders) are the last Milestone 1 screens still on the legacy plain-HTML/CSS baseline. Stories 5.1–5.3 built the token/component foundation and restyled auth + quote; this story applies the same system to the shared chrome so every role's workspace reads as one product.

**Approach:** Rebuild `RootLayout`'s markup with semantic Tailwind tokens (navy `bg-primary` header, Inter via `font-sans`), route the Logout control through `Button` (new `ghost` variant for dark surfaces), and restyle `LanguageToggle` as a segmented pill control keyed off its existing `aria-pressed` state. Give all four shells one "workspace" shape: a page-level `<h2>` workspace heading plus a `Card` (staff coming-soon copy) or the existing `QuoteForm` card (client). No behavior change — same routes, guards, auth flow, logout, translations, test IDs, and semantic elements.

## Boundaries & Constraints

**Always:** `RootLayout.test.tsx`, `LanguageToggle.test.tsx`, `shells.test.tsx`, `router.test.tsx`, and every cross-suite consumer pass unmodified — zero test-file edits. Preserve character-for-character: the `<header>`/`<h1>`/`<nav>`/`<main>` elements, the Logout `<button type="button">` and its `onClick={handleLogout}` (clearToken + navigate `/login` replace), the two `<Link>`s to `/register` `/login` and the `/health` `<Link>`, `data-testid="language-toggle"`, each toggle `<button>`'s `type`/`lang`/`aria-pressed`/`onClick`, `role="group"` + `aria-label`, every shell's `<section data-testid="{role}-shell">`, the staff shells' `aria-labelledby` + `<h2 id="{role}-shell-heading">` + single `<p>`, and all `t('...')` keys. Staff shells stay 100% static — no `<a>`, `<button>`, `<input>`, `[role]`, or `[tabindex]` inside the `<section>` (guarded by `shells.test.tsx`), and clicking anywhere must not mutate the DOM. Colors/typography/spacing resolve only to `@theme` tokens or Tailwind's default scale (AD-1); component `className` overrides carry layout utilities only (AD-2). Any status-like color maps to `success | warning | danger | info` (AD-6).

**Ask First:** None anticipated.

**Never:** No change to routing (`router.tsx`), `RoleGuard`, `getCurrentRole`/`roleHome`, `handleLogout`, the i18n catalogs (`bg.json`/`en.json`), or `HealthStatus`. No responsive/breakpoint work (Story 5.5) or shared spinner/error-banner system (Story 5.6). No new components beyond a `Button` variant. No new product functionality. Do not weaken tests.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Logged-out nav | `getCurrentRole()` null | Register + Login + Health links render, no Logout; `RootLayout.test.tsx` passes | N/A |
| Logged-in nav | valid token | `Button` ghost Logout replaces Register/Login, Health unchanged; click clears token, routes to `/login`, nav flips | N/A |
| Language toggle | `aria-pressed` per active lang | Active pill visually distinct via `aria-pressed:` utilities; switch re-renders in place, route/form state kept | N/A |
| Staff shell | direct visit as matching role | Workspace `<h2>` + `Card` coming-soon `<p>`, no other role's stem in text, zero interactive nodes | N/A |
| Client shell | CLIENT at `/` | Workspace `<h2>` + `<QuoteForm />` card; `quote.form.heading` still present | N/A |

</frozen-after-approval>

## Code Map

- `frontend/src/components/ui/Button.tsx` -- add a `ghost` variant to `buttonVariants` cva (`bg-transparent text-white hover:bg-white/10`) for controls on the navy header. No prop-shape change; `primary`/`secondary` untouched.
- `frontend/src/components/ui/Button.test.tsx` -- add one case: `variant="ghost"` renders a real `<button>` with a class set distinct from `primary`/`secondary`.
- `frontend/src/app/RootLayout.tsx:40-63` -- rebuild the returned JSX. Root `<div className="flex min-h-screen flex-col bg-surface-muted font-sans text-text">`; `<header className="bg-primary text-white">` wrapping a centered `<div>` (flex, wrap, `max-w-5xl`, `px-6 py-4`) with `<h1 className="text-lg font-semibold tracking-tight">`, a `<nav>` of `<Link className="text-sm font-medium text-white/80 transition-colors hover:text-white">` items, the Logout `<Button variant="ghost" size="sm" onClick={handleLogout}>`, and `<LanguageToggle />`. `<main className="mx-auto w-full max-w-2xl flex-1 px-6 py-10">`. Keep `handleLogout`, `useNavigate`, `getCurrentRole`, `<Outlet />` exactly.
- `frontend/src/app/LanguageToggle.tsx:34-48` -- keep structure; add `className` to the `<div>` (`flex items-center gap-1 rounded-full border border-white/20 p-1`) and to each `<button>` (`rounded-full px-2.5 py-0.5 text-xs font-medium text-white/70 transition-colors hover:text-white aria-pressed:bg-white aria-pressed:text-primary`). A segmented toggle is not one of the four base primitives (Button/Input/FormField/Card) — style its native `<button>`s directly, per architecture "compose plain utilities for layout". Add a comment saying so.
- `frontend/src/features/shells/client/ClientShell.tsx` -- `<section data-testid="client-shell" aria-labelledby="client-shell-heading" className="border-0 bg-transparent p-0">` (strip legacy `main > section` card chrome), `<h2 id="client-shell-heading" className="mb-4 mt-0 text-2xl font-semibold tracking-tight text-text">`, then `<QuoteForm />` unchanged. `aria-labelledby`+`id` added for parity with staff shells (additive, no test depends on their absence).
- `frontend/src/features/shells/{agent,liquidator,administrator}/{Agent,Liquidator,Administrator}Shell.tsx` -- same wrapper treatment: `<section data-testid aria-labelledby className="border-0 bg-transparent p-0">`, restyled `<h2 id>`, then `<Card><p className="text-sm text-text-muted">{t('shells.X.comingSoon')}</p></Card>`. Keep the exact testid/id/aria/keys. Refresh the stale "no route guard until Story 2.4" doc comments while here.
- `frontend/src/index.css:83-147` -- delete the now-dead `@layer legacy` rules for `header`, `header h1`, `header nav`, `header nav a(:hover)`, and every `[data-testid='language-toggle'] ...` selector (grep-confirmed: `<header>` and that testid exist only in `RootLayout`/`LanguageToggle`, both restyled here). Leave all other legacy rules (`main`, `main > section`, forms, `button`, `[role='alert']`, `dl`) untouched — `HealthStatus` still depends on them.
- `frontend/src/app/RootLayout.test.tsx`, `LanguageToggle.test.tsx`, `shells.test.tsx` -- READ-ONLY. Load-bearing literals: heading-by-name, link/button-by-role, `data-testid`, `aria-pressed`, `toHaveAccessibleName(heading)`, and the `INTERACTIVE_SELECTOR`/click-does-not-mutate guards.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/components/ui/Button.tsx` + `Button.test.tsx` -- add `ghost` variant + test -- FR-6 (dark-header control).
- [x] `frontend/src/app/RootLayout.tsx` -- rebuild header/nav/main markup with tokens; Logout via `Button` ghost -- FR-6.
- [x] `frontend/src/app/LanguageToggle.tsx` -- segmented-pill restyle keyed off `aria-pressed` -- FR-6.
- [x] `frontend/src/features/shells/client/ClientShell.tsx` -- workspace heading + strip legacy chrome around `QuoteForm` -- FR-7.
- [x] `frontend/src/features/shells/{agent,liquidator,administrator}/*Shell.tsx` -- unified workspace look via `Card` -- FR-7.
- [x] `frontend/src/index.css` -- remove dead legacy header/nav/toggle rules -- FR-1 (no competing styles).

**Acceptance Criteria:**
- Given `RootLayout.test.tsx`, `LanguageToggle.test.tsx`, `shells.test.tsx`, `router.test.tsx`, when run after the restyle, then every test passes with no test file modified.
- Given the full frontend suite (`npm test`), when run, then it is 100% green (207+ tests).
- Given `npm run typecheck` and `npm run build`, when run, then both succeed.
- Given each staff shell rendered for its role, when its text is scanned, then it shows its own workspace label and no other role's name stem, and contains zero interactive controls.
- Given the header in Bulgarian and English, when viewed, then the title, nav labels, Logout, and language pills all render from tokens with no hardcoded hex or inline font-family.

## Design Notes

Unified shell shape (staff variant shown; client swaps the `Card` for `<QuoteForm />`):

```tsx
<section data-testid="agent-shell" aria-labelledby="agent-shell-heading"
         className="border-0 bg-transparent p-0">
  <h2 id="agent-shell-heading" className="mb-4 mt-0 text-2xl font-semibold tracking-tight text-text">
    {t('shells.agent.heading')}
  </h2>
  <Card>
    <p className="text-sm text-text-muted">{t('shells.agent.comingSoon')}</p>
  </Card>
</section>
```

`border-0 bg-transparent p-0` neutralizes the legacy `main > section` card chrome (the `utilities` layer outranks `legacy`, so plain utilities win — same cascade-order fact Story 5.1 established) so the inner `Card` is the only card, not a card-in-card.

`Button` `ghost` variant is navy-surface-only: transparent fill, white text, faint white hover wash — the existing `focus-visible:ring-accent` still reads against navy.

## Verification

**Commands:**
- `cd frontend; npm run typecheck` -- clean.
- `cd frontend; npm test` -- **208 passed** (207 baseline + 1 new `Button` ghost case); no existing test file modified.
- `cd frontend; npm run build` -- production build succeeds; Tailwind emitted the `aria-pressed:` / `bg-white/10` / `border-white/20` utilities (grep-verified in `dist/assets/*.css`).

**Manual checks (done, Browser pane against `npm run dev`):**
- `/login` logged out: navy header, white nav links (Register/Login/Health), segmented language pills — active pill white bg + navy text (`rgb(255,255,255)` / `rgb(42,40,89)`), inactive transparent.
- CLIENT at `/`: ghost Logout pill replaces Register/Login; "Клиент" workspace `<h2>` sits above `QuoteForm`'s card with the shell `<section>` chrome stripped (bg transparent, border 0, padding 0) — no card-in-card.
- AGENT at `/agent` (also spot-checked in English): "Работно място на агента" / "Agent workspace" `<h2>` + a single `Card` with the coming-soon `<p>` — same shape as the client shell.
- Logout click: token cleared, routed to `/login`, nav flipped to Register/Login/Health.
- `/health` (untouched legacy screen): still renders its legacy `main > section` card unchanged.

## Suggested Review Order

**Header / nav restyle (the entry point)**

- Entry point: the full header/nav/main rebuild — navy `bg-primary`, `font-sans`, nav-link utility, `<main>` column.
  [`RootLayout.tsx:50`](../../frontend/src/app/RootLayout.tsx#L50)
- Logout routed through the component library instead of a raw `<button>`.
  [`RootLayout.tsx:58`](../../frontend/src/app/RootLayout.tsx#L58)
- The `ghost` variant that makes that possible — transparent-on-navy, added via cva (AD-2).
  [`Button.tsx:17`](../../frontend/src/components/ui/Button.tsx#L17)

**Language toggle**

- Segmented-pill restyle; active state driven off the existing `aria-pressed` via the `aria-pressed:` variant. `bg-transparent`/`hover:bg-white/10` are needed because removing the legacy toggle rules re-exposes the generic `button {}` fallback.
  [`LanguageToggle.tsx:56`](../../frontend/src/app/LanguageToggle.tsx#L56)

**Role shells — one workspace shape**

- Staff shell: `<section>` chrome stripped, workspace `<h2>`, content in a single `Card`.
  [`AgentShell.tsx:24`](../../frontend/src/features/shells/agent/AgentShell.tsx#L24)
- Client shell: same shape, `QuoteForm`'s own card as the content; `aria-labelledby` added for parity.
  [`ClientShell.tsx:19`](../../frontend/src/features/shells/client/ClientShell.tsx#L19)
- `LiquidatorShell.tsx` / `AdministratorShell.tsx` are byte-parallel to `AgentShell`.

**Peripherals**

- Dead legacy `header` / `[data-testid='language-toggle']` rules removed; the kept rules are annotated.
  [`index.css:83`](../../frontend/src/index.css#L83)
- New `Button` ghost-variant test.
  [`Button.test.tsx:45`](../../frontend/src/components/ui/Button.test.tsx#L45)
