---
stepsCompleted: [1, 2, 3]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-motor-insurance-quote-claims-portal-milestone-2-2026-08-30/prd.md
  - _bmad-output/planning-artifacts/architecture/architecture-milestone-2-2026-08-30/ARCHITECTURE-SPINE.md
---

# Motor Insurance Quote & Claims Portal — Milestone 2 - Epic Breakdown

## Overview

This document provides the epic and story breakdown for Milestone 2 (A Real Look for the Portal), decomposing the requirements from the Milestone 2 PRD and its architecture spine into implementable stories. It is scoped separately from `epics.md` (Milestone 1) — its own FR numbering (FR-1..FR-9) is local to Milestone 2's PRD, not a continuation of Milestone 1's FR-1..FR-15.

## Requirements Inventory

### Functional Requirements

FR-1: Design tokens configured (Tailwind `@theme` block — navy/accent palette, Inter font family, spacing/type scale).
FR-2: Base component library built (`Button`, `Input`, `FormField`, `Card`).
FR-3: Login and Register screens restyled with the component library.
FR-4: Quote form restyled with the component library.
FR-5: Quote breakdown (`QuoteResult`) restyled as a structured summary.
FR-6: Root layout (header/nav, Logout/Register/Login swap, language toggle) restyled.
FR-7: Role shells (Client + the three staff placeholders) restyled.
FR-8: Every touched screen usable at mobile viewport widths (~375px+).
FR-9: Consistent loading/error-state treatment across the app (should-have).

### NonFunctional Requirements

No explicit NFRs — Milestone 2's PRD §6 Non-Goals sets the scope boundary instead (no WCAG audit, no dark mode, no animation/micro-interaction polish beyond basic transitions, no native mobile app).

### Additional Requirements (from Architecture Spine)

- AD-1: Tailwind CSS v4, CSS-first (`@theme` in `frontend/src/index.css`, no `tailwind.config.js`); every custom token uses a semantic name, never a bare scale re-derivation.
- AD-2: Component variants via `class-variance-authority` (cva); `className` overrides resolved through a `cn()` helper (`clsx` + `tailwind-merge`) and restricted to spacing/sizing/positioning only — never color/typography/border. `Card` uses flat props, never a compound-component API.
- AD-3: Components render native semantic elements (`<button>`, `<input>`, a real `<label>`) — never a styled non-semantic stand-in — so existing role-based test queries keep passing.
- AD-4: No automated lint enforcement of component-library adoption — convention + code review only.
- AD-5: `FormField` owns the field-level error message (`error?: string`); `Input` owns only a boolean `invalid` prop for visual styling.
- AD-6: Fixed semantic status-color vocabulary (`success | warning | danger | info`) even though no `Badge` component exists this milestone.
- Component library lives in `frontend/src/components/ui/`.
- Inherited from Milestone 1's spine (binding, unaffected by this milestone): AD-8 (i18n frontend-only), AD-10 (React Router v8, one role-guard wrapper, one typed fetch client, no data-fetching library).

### UX Design Requirements

No separate UX design document exists for this milestone (same team choice as Milestone 1) — the PRD's own §4 "Aesthetic and Tone" section (Sirma brand references + insurance-domain restraint direction, confirmed 2026-08-30) serves as the UX input.

### FR Coverage Map

FR-1: Epic 5 - Design tokens configured
FR-2: Epic 5 - Base component library built
FR-3: Epic 5 - Auth screens visual pass
FR-4: Epic 5 - Quote form visual pass
FR-5: Epic 5 - Quote breakdown visual pass
FR-6: Epic 5 - Root layout visual pass
FR-7: Epic 5 - Role shells visual pass
FR-8: Epic 5 - Responsive layout
FR-9: Epic 5 - Loading/error-state polish (should-have)

## Epic List

### Epic 5: A Real Look for the Portal
Every screen Milestone 1 shipped functionally now looks and behaves like a real, trustworthy insurance product — one design system, applied consistently, that Milestone 3/4 build new screens on top of instead of re-inventing styling.
**FRs covered:** FR-1, FR-2, FR-3, FR-4, FR-5, FR-6, FR-7, FR-8, FR-9

## Epic 5: A Real Look for the Portal

Every screen Milestone 1 shipped functionally now looks and behaves like a real, trustworthy insurance product — one design system, applied consistently, that Milestone 3/4 build new screens on top of instead of re-inventing styling.

### Story 5.1: Design Tokens & Base Component Library

As a developer building any screen in this milestone,
I want a single source of design tokens and a small reusable component library,
So that every screen looks consistent without re-deriving color/spacing/typography decisions per screen.

**Acceptance Criteria:**

**Given** the Tailwind v4 `@theme` block in `frontend/src/index.css` (AD-1)
**When** any screen renders color, font, or spacing
**Then** it resolves to a token defined there — no hardcoded hex color or inline font-family exists in any screen this milestone touches
**And** given `Button`, `Input`, `FormField`, `Card` in `frontend/src/components/ui/` (AD-2/AD-3), when a screen needs one of these elements, then it uses the shared component — never a raw equivalent — rendering its underlying native semantic HTML element
**And** given a component's variant surface, when a new variant is needed, then it's added via `class-variance-authority`, never faked with ad hoc utility classes (AD-2)

### Story 5.2: Auth Screens Visual Pass

As a visitor,
I want the login and registration screens to look like a professional insurance product,
So that my first impression of the portal is trustworthy, not unfinished.

**Acceptance Criteria:**

**Given** `LoginForm` and `RegisterForm` rebuilt with Story 5.1's component library
**When** I view either screen
**Then** every button, input, and field-level error uses the shared components (`FormField` owns the error message per AD-5, `Input` signals invalid state visually)
**And** given the existing `LoginForm.test.tsx`/`RegisterForm.test.tsx` suites, when they run after this restyle, then they pass unmodified (FR-3)

### Story 5.3: Quote Flow Visual Pass

As an authenticated client,
I want my quote form and premium breakdown to read like a real financial summary,
So that I can trust and easily understand what I'd pay and why.

**Acceptance Criteria:**

**Given** `QuoteForm` rebuilt with the component library
**When** I fill in driver/vehicle parameters
**Then** fields are clearly grouped and validation states are visible per the existing field-level error contract (FR-4)
**And** given `QuoteResult` rebuilt as a structured summary, when my quote is calculated, then all seven breakdown fields are still rendered, correctly labeled/translated, inside a clear total + itemized layout (FR-5)
**And** given `QuoteForm.test.tsx`'s existing assertions, when run after this restyle, then they pass unmodified

### Story 5.4: Navigation & Role Shells Visual Pass

As any authenticated user,
I want the shared header/nav and my role's shell to look like one consistent system,
So that the product feels finished across every role, not just the client flow.

**Acceptance Criteria:**

**Given** `RootLayout`'s header/nav — including the Logout/Register/Login swap (Story 2.5) and `LanguageToggle`
**When** rebuilt with the component library
**Then** `RootLayout.test.tsx`'s existing assertions pass unmodified (FR-6)
**And** given the Client shell and the three staff placeholder shells, when restyled with a consistent "workspace" look, then each remains distinctly labeled — no cross-role text bleed, per `shells.test.tsx`'s existing guard (FR-7)
**And** given any status-like visual treatment in a shell, when colors are chosen, then they map to the fixed `success | warning | danger | info` vocabulary (AD-6) — no shell invents its own meaning

### Story 5.5: Responsive Layout

As a client using a phone,
I want every screen from this milestone to work on a small viewport,
So that I'm not forced to use a desktop to get a quote or log in.

**Acceptance Criteria:**

**Given** any screen restyled in Stories 5.2–5.4
**When** viewed at viewport widths from ~375px up
**Then** there is no horizontal scroll, no overlapping/cut-off content, and every tap target stays reachable (FR-8)
**And** given Tailwind's default breakpoint scale, when responsive behavior is implemented, then no custom breakpoints are introduced without a documented reason

### Story 5.6: Loading & Error-State Polish *(should-have)*

As a user waiting on any screen,
I want loading and error feedback to look and behave the same everywhere,
So that waiting or a failure never feels like the app is broken.

**Acceptance Criteria:**

**Given** the health check, form submissions, and quote calculation
**When** any of them is loading or fails
**Then** they all use one shared spinner and one shared error-banner pattern, built from the component library (FR-9)

**Notes:** should-have, not required to consider Epic 5 complete — pick up after 5.1–5.5 if time permits.
