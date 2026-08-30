---
title: 'Motor Insurance Quote & Claims Portal — Milestone 2: A Real Look for the Portal'
status: 'final'
created: '2026-08-30'
updated: '2026-08-30'
---

# PRD: Motor Insurance Quote & Claims Portal — Milestone 2: A Real Look for the Portal

## 0. Document Purpose

This PRD scopes **Milestone 2** for the two-person team and their Sirma Academy mentor. It builds directly on Milestone 1's PRD and the existing codebase — every screen this milestone touches already exists and works functionally; nothing here changes behavior, only appearance and layout. It feeds `bmad-architecture` (for the one real technical decision — Tailwind adoption) and `bmad-create-epics-and-stories` next.

## 1. Vision

Milestone 1 proved the architecture works: a client can register, get a transparent quote, and every role lands on its own guarded workspace — but it deliberately looked like a skeleton, because visual design was an explicit non-goal. Milestone 2 gives that skeleton a real face: a consistent design system, modeled on Sirma's own visual identity but toned toward the seriousness insurance products require, applied across every screen that already exists. When this milestone ships, the portal should read as a professional insurance product a client would trust with their car and their money — not a training exercise.

This is a visual and structural UI milestone only. No new backend capability, no new domain screens (Policy issuance and Claims/FNOL are Milestones 3 and 4) — the payoff is that those future milestones build new screens on top of a design system that already exists, instead of inventing styling twice.

## 2. Target User

### 2.1 Jobs To Be Done

- As the team, we want the portal to look credible to our mentor and any outside reviewer, so Milestone 1's structural proof is not undercut by a visibly unfinished appearance.
- As a future user of Milestones 3/4, I want the Policy and Claims screens to inherit an existing design system, so I never have to design a screen's look and its logic in the same pass.
- As the CLIENT/AGENT/LIQUIDATOR/ADMINISTRATOR roles from Milestone 1, I want the screens I already use to look and feel consistent, so the product feels like one system, not four disconnected shells.

### 2.2 Key User Journeys

Lighter scope dial: this milestone touches no new interaction paths, only the surfaces of ones Milestone 1 already delivered. No new UJs — every existing UJ (register → quote → breakdown, login → role shell, language toggle, logout) stays behaviorally identical; only its visual presentation changes.

## 3. Glossary

- **Design Token** — A named, reusable design decision (a color, a spacing step, a font size) defined once in the Tailwind config and referenced everywhere, never hardcoded inline.
- **Component Library** — The small set of reusable UI primitives (Button, Input, FormField, Card) every screen in this milestone is rebuilt from.
- **Visual Pass** — Applying the design system to an existing, functionally-complete screen: layout, spacing, color, typography — no logic changes.
- **Design System** — Design Tokens + Component Library together, the single source of truth this milestone establishes.

## 4. Aesthetic and Tone

**Visual references** (researched against the live sites, 2026-08-30): [sirma.com](https://sirma.com/) and [academy.sirma.com](https://academy.sirma.com/) (parent brand and the trainee program this project is built for) share one design system — navy `#2A2859` base, a bright cyan/sky-blue accent, pill-shaped buttons, card-based stat callouts with rounded icon badges, and abstract geometric decoration. [ASSUMPTION: exact hex values are inferred from page structure/markdown extraction, not measured with a color picker against rendered CSS — may need minor adjustment during implementation.] The Academy sub-brand layers a warmer, more colorful accent (orange/lavender/teal gradient "blobs") on the same structure.

Insurance-domain references — [bulstrad.bg](https://www.bulstrad.bg/), [vig-sb.bg](https://www.vig-sb.bg/en/), [twinformatics.at](https://www.twinformatics.at/) — lean the opposite way: dark-navy nav, white body, formal copy, restrained accent use, and trust signals (certifications, named clients, phone numbers) standing in for playfulness. Even the most modern of the three (twinformatics, an Austrian insurtech) stays bold-but-serious rather than playful.

**Direction for this milestone** [confirmed with the user, 2026-08-30]: anchor on Sirma's navy `#2A2859` and its pill-button/card visual vocabulary — the on-brand connection to the trainee program — but dial the accent usage and decoration toward insurance-domain restraint. No gradient "blob" decoration, no playful illustration. Cards, badges, and buttons read as formal financial-product UI first, Sirma-branded second.

**Typography:** Sirma's TT Norms Pro is proprietary. This milestone uses **Inter** (Google Fonts) as the open substitute — geometrically close, strong Cyrillic glyph support (required: Bulgarian is the default Display Language, per Milestone 1's i18n work).

## 5. Features

### 5.1 Design Tokens & Base Component Library

**Description:** Establishes the single visual source of truth every other feature in this milestone consumes. Tailwind CSS is added to the frontend build [ASSUMPTION: this is the one new dependency this milestone introduces — `bmad-architecture` should record it as a formal decision, not just inherit it from this PRD]; its config encodes the navy/accent palette and type scale from §4 as tokens. A small component library — `Button`, `Input`, `FormField`, `Card` — is built once, styled from those tokens, and becomes the only way any screen in this milestone renders those elements.

**Functional Requirements:**

#### FR-1: Design tokens configured

Tailwind's config defines the color palette (navy primary, restrained accent, neutral grays), the Inter font family, and a spacing/type scale, all traceable to §4's direction.

**Consequences (testable):**
- No screen touched by this milestone contains a hardcoded hex color or inline font-family — every value resolves to a Tailwind token.

#### FR-2: Base component library built

`Button`, `Input`, `FormField`, and `Card` exist as reusable components styled from the tokens in FR-1, replacing every ad-hoc `<button>`/`<input>` styling in the touched screens.

**Consequences (testable):**
- Every button and form input across the screens in Features 5.2–5.4 renders through one of these four components, not a one-off element.

**Out of Scope:** A general-purpose component library covering every possible UI need (e.g. modals, tooltips, tables) — only what Features 5.2–5.4 actually require this milestone.

### 5.2 Auth Screens Visual Pass

**Description:** Applies the design system to the login and registration screens — the first thing any visitor sees. No behavior change: the same validation, the same error handling from Story 3.2b, now laid out and styled with FR-1/FR-2's tokens and components.

**Functional Requirements:**

#### FR-3: Login and Register screens restyled

`LoginForm` and `RegisterForm` are rebuilt using the Feature 5.1 component library — real layout, spacing, and styled loading/error states, still fully translated (Bulgarian/English) per Milestone 1.

**Consequences (testable):**
- Existing `LoginForm.test.tsx`/`RegisterForm.test.tsx` behavioral assertions (validation, error messages, submit flow) still pass unmodified — only markup/styling changes.

### 5.3 Quote Flow Visual Pass

**Description:** The highest-value screen for a demo: a client enters driver/vehicle parameters and sees the calculated premium breakdown. This feature makes that breakdown genuinely easy to read — the seven fields (base premium, age surcharge, one-time premium, installment fee, total premium, installment amount, zone) laid out as a real financial summary, not a plain list.

**Functional Requirements:**

#### FR-4: Quote form restyled

`QuoteForm` rebuilt with Feature 5.1 components — clear field grouping, visible validation states per the existing field-level error contract.

#### FR-5: Quote breakdown restyled

`QuoteResult` presents the full breakdown as a structured summary (e.g. a card with a clear total and an itemized list of the components it's built from) rather than an undifferentiated block.

**Consequences (testable):**
- Every field `QuoteResult` renders today is still rendered and still correctly labeled/translated after restyling.
- `QuoteForm.test.tsx` behavioral assertions still pass unmodified.

### 5.4 Navigation & Role Shells Visual Pass

**Description:** The shared chrome every authenticated screen sits inside — header, nav (including Story 2.5's Logout control and the language toggle), and the four role shells (Client's real quote flow entry point, and the three staff placeholder screens).

**Functional Requirements:**

#### FR-6: Root layout restyled

`RootLayout`'s header/nav — including the auth-aware Logout/Register/Login swap and `LanguageToggle` — rebuilt with Feature 5.1 components.

#### FR-7: Role shells restyled

The three staff placeholder shells (Agent/Liquidator/Administrator) and the Client shell wrapper get a consistent, on-brand "workspace" look, still clearly labeled per-role.

**Consequences (testable):**
- `RootLayout.test.tsx` and `shells.test.tsx` behavioral assertions still pass unmodified.
- Each staff shell remains distinctly labeled — no cross-role text bleed (same regression guard Story 3.2a established).

### 5.5 Responsive Layout

**Description:** Milestone 1 explicitly excluded mobile/responsive layout. This feature closes that gap for every screen touched above, since a "real" insurance product must be usable on a phone.

**Functional Requirements:**

#### FR-8: Screens usable at mobile viewport widths

Every screen from Features 5.2–5.4 renders usably (no horizontal scroll, no overlapping/cut-off content, tap targets reachable) from ~375px width up.

**Out of Scope:** Native mobile app, mobile-specific interaction patterns (swipe gestures, bottom sheets) — responsive web layout only.

### 5.6 Loading & Error-State Polish *(should-have, not blocking)*

**Description:** Unifies the visual treatment of loading and error feedback across the app (health check, form submission, quote calculation) so waiting/failure never looks broken or inconsistent between screens.

**Functional Requirements:**

#### FR-9: Consistent loading/error treatment

A single spinner and error-banner pattern (built from Feature 5.1 components) used everywhere a screen currently shows an ad-hoc loading or error state.

**Notes:** [ASSUMPTION: treated as should-have rather than must-have, consistent with how Milestone 1's own PRD graded similar polish items as lower priority.] Not required to consider this milestone complete — pick up after Features 5.1–5.5 if time permits.

## 6. Non-Goals (Explicit)

- **No new backend or domain functionality.** Policy issuance and Claims/FNOL remain out of scope — Milestones 3 and 4.
- **No new screens.** Every screen this milestone touches already exists from Milestone 1.
- **Formal accessibility (WCAG) audit** is not required — components use semantic HTML by default, but a dedicated audit pass is a later concern, consistent with how Milestone 1 treated accessibility.
- **Dark mode** is not part of this milestone.
- **Animation / micro-interaction polish** beyond basic, functional transitions (e.g. a loading spinner) is out of scope.
- **Native mobile app** — responsive *web* layout only (§5.5).
- **A formal component-library documentation tool** (e.g. Storybook) is not required — the component library itself is required, its documentation is not.

## 7. MVP Scope

### 7.1 In Scope
- Features 5.1–5.5 (design tokens, component library, and a visual+responsive pass over every M1 screen).

### 7.2 Out of Scope for MVP
- Feature 5.6 (loading/error-state unification) — should-have, deferred if time-constrained.
- Everything listed in §6 Non-Goals.

## 8. Success Metrics

**Primary**
- **SM-1**: The mentor's/reviewer's first reaction to the portal is that it looks like a real product, not a prototype. Validates FR-1–FR-8.

**Counter-metrics (do not optimize)**
- **SM-C1**: Time spent on this milestone should not indefinitely delay starting Milestone 3 (Policy issuance) — this is a design-system investment, not an open-ended polish project. Counterbalances SM-1.

## 9. Open Questions

1. Should the component library gain lightweight usage documentation (even just code comments with examples) before Milestone 3 starts building new screens from it, or is reading the source enough for a two-person team?
2. Should this PRD's navy/accent token values be pinned to exact hex codes now, or resolved during `bmad-architecture`/implementation against the actual rendered Sirma site (colors here were inferred from page structure, not a color picker)?

## 10. Assumptions Index

- §4 — Exact hex values for Sirma's palette are inferred from research, not measured directly off rendered pages; may need minor adjustment during implementation. [ASSUMPTION]
- §5.1 — Tailwind CSS is added as a new frontend dependency; this is the one item `bmad-architecture` should formally record as a decision. [ASSUMPTION]
- §5.6 — Treated as should-have rather than must-have, consistent with how Milestone 1's own PRD graded similar polish items as lower priority. [ASSUMPTION]
