# Epic 5 Context: A Real Look for the Portal

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Give every screen Milestone 1 shipped functionally a consistent, trustworthy visual identity: a single design-token source and a small shared component library, applied across auth, quote, navigation, and role-shell screens, with responsive support down to ~375px. This is a visual/structural pass only — no backend change, no new screens, no behavior change to anything Milestone 1 already built. The payoff is that Milestones 3/4 build new screens on top of an existing design system instead of inventing styling twice.

## Stories

- Story 5.1: Design Tokens & Base Component Library
- Story 5.2: Auth Screens Visual Pass
- Story 5.3: Quote Flow Visual Pass
- Story 5.4: Navigation & Role Shells Visual Pass
- Story 5.5: Responsive Layout
- Story 5.6: Loading & Error-State Polish (should-have, not blocking)

## Requirements & Constraints

- Every color, font, and spacing value used by a touched screen must resolve to a design token — no hardcoded hex color or inline `font-family` anywhere in scope.
- `Button`, `Input`, `FormField`, `Card` are the only four components this milestone builds; every button/input across the touched screens renders through one of them, not a one-off element. No modal/tooltip/table/Badge components — explicitly out of scope.
- Auth (Login/Register), Quote form, Quote breakdown (all seven fields, correctly labeled/translated), Root layout (header/nav incl. Logout/Register/Login swap and language toggle), and all four role shells (Client + Agent/Liquidator/Administrator placeholders) must be restyled without altering behavior.
- Existing behavioral test suites (`LoginForm.test.tsx`, `RegisterForm.test.tsx`, `QuoteForm.test.tsx`, `RootLayout.test.tsx`, `shells.test.tsx`) must keep passing unmodified — markup/styling changes only.
- Each role shell must remain distinctly labeled with no cross-role text bleed (same regression guard as Milestone 1's Story 3.2a).
- All touched screens must work usably at viewport widths from ~375px up: no horizontal scroll, no overlapping/cut-off content, all tap targets reachable. Use Tailwind's default breakpoint scale; no custom breakpoints without a documented reason.
- Loading/error feedback (health check, form submissions, quote calculation) should share one spinner pattern and one error-banner pattern (should-have — Story 5.6, pick up only after 5.1–5.5 land).
- Out of scope for this epic: WCAG audit, dark mode, animation/micro-interaction polish beyond basic transitions, native mobile app, component-library documentation tooling (e.g. Storybook), new backend or domain functionality, new screens.
- i18n is unaffected: styling changes carry no copy changes; existing translation catalogs stay untouched, and both Bulgarian and English must still render correctly post-restyle.

## Technical Decisions

- Tailwind CSS v4, CSS-first: a single `@theme` token block in `frontend/src/index.css`, no `tailwind.config.js`. Every custom token uses a semantic name (e.g. `--color-primary`, `--spacing-card-padding`), never a bare re-derivation of Tailwind's default scale.
- Palette direction: Sirma navy `#2A2859` as base, a restrained bright accent, Inter (Google Fonts, variable font) for typography — chosen for strong Cyrillic glyph support. Exact hex values beyond the navy base are inferred from research, not color-picked, and may need adjustment during implementation — this is expected, not a defect.
- Visual tone: Sirma's pill-button/card vocabulary anchored, but accent/decoration usage dialed toward insurance-domain restraint (no gradient "blob" decoration, no playful illustration) — formal financial-product UI first, brand-styled second.
- Component variants are implemented with `class-variance-authority` (cva) — never faked with ad hoc utility class composition. A caller-supplied `className` override is merged via a `cn()` helper (`clsx` + `tailwind-merge`) and may only carry spacing/sizing/positioning utilities — never color, background, border, or typography, which live exclusively in the component's own variant definition.
- Variant prop naming is uniform across all four components: always `variant`, and `size` where applicable.
- `Card` exposes flat props (`title?`, `footer?`, `children`) only — never a compound-component API (`Card.Header`/`Card.Body`).
- `FormField` owns the field-level error message (`error?: string`, already-translated) and renders it below the label/control; `Input` owns only a boolean `invalid` prop for visual styling and never renders error text itself. A screen always passes the error message to `FormField`, never to `Input`.
- Components render real native semantic HTML (`<button>`, `<input>`, a real `<label>`) — never a styled non-semantic stand-in (e.g. a `<div>` with an ARIA role) — this is what keeps existing role-based test queries (`screen.getByRole('button', ...)`) passing unmodified.
- Status-like visual treatment (in shells or elsewhere) maps to a fixed vocabulary: `success | warning | danger | info`. No screen invents a fifth category or reassigns which color means "bad," even though no `Badge` component exists yet.
- No automated lint enforcement of component-library adoption in this milestone — adoption is enforced by code review convention only, per existing `CONTRIBUTING.md` process.
- Component library lives at `frontend/src/components/ui/`; screens (`frontend/src/features/`, `frontend/src/app/`) consume it and compose plain Tailwind utility classes for layout only — they never restyle around it or redefine a token.
- Stack additions: `tailwindcss` v4.3.3 + `@tailwindcss/vite`, `class-variance-authority` 0.7.1, `clsx` 2.1.1, `tailwind-merge` 3.6.0, Inter via Google Fonts.
- Unaffected/inherited from Milestone 1: i18n stays frontend-only via `react-i18next` (untouched by this milestone); React Router v8, the single role-guard wrapper, and the single typed fetch client are unaffected — this epic is presentation-only.

## UX & Interaction Patterns

- No separate UX design document exists for this milestone; the PRD's own "Aesthetic and Tone" direction is the UX input.
- Visual reference: Sirma's navy/cyan, pill-shaped buttons, and card-based stat callouts with rounded icon badges — tempered by insurance-domain references (dark-navy nav, white body, formal copy, restrained accent, trust signals) rather than playful decoration.
- Quote breakdown (`QuoteResult`) should read as a structured financial summary: a clear total plus an itemized list of its components (base premium, age surcharge, one-time premium, installment fee, total premium, installment amount, zone) — not an undifferentiated block.
- Quote form fields should be clearly grouped, with validation states visibly surfaced per the existing field-level error contract.
- Role shells get a consistent "workspace" look while staying clearly and distinctly labeled per role.

## Cross-Story Dependencies

- Story 5.1 (tokens + component library) is the foundation every other story in this epic consumes — 5.2–5.6 all build on its `Button`/`Input`/`FormField`/`Card` components and `@theme` tokens.
- Story 5.5 (responsive layout) applies to whatever Stories 5.2–5.4 produce, so it logically follows their restyle work.
- Story 5.6 (loading/error polish) is should-have and explicitly deferred until after Stories 5.1–5.5 are complete.
- This epic is a prerequisite for Milestones 3 (Policy issuance) and 4 (Claims/FNOL), which will build new screens directly on top of the design system established here.
