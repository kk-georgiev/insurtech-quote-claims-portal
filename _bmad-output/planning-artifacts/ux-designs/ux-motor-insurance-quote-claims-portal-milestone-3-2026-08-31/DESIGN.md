---
title: 'Motor Insurance Portal — Milestone 3 Visual Identity (inherited)'
status: final
created: '2026-08-31'
updated: '2026-08-31'
inherits: 'Milestone 2 design system — frontend/src/index.css (@theme) + frontend/src/components/ui/'
sources:
  - '_bmad-output/planning-artifacts/prds/prd-motor-insurance-quote-claims-portal-milestone-3-2026-08-31/prd.md'
  - '_bmad-output/planning-artifacts/prds/prd-motor-insurance-quote-claims-portal-milestone-2-2026-08-30/prd.md'
  - '_bmad-output/planning-artifacts/architecture/architecture-milestone-2-2026-08-30/ARCHITECTURE-SPINE.md'
  - 'frontend/src/index.css'
colors:
  primary: '#2a2859'
  primary-dark: '#1e1d40'
  primary-light: '#4a4780'
  accent: '#3e7c8c'
  accent-dark: '#2e5d69'
  surface: '#ffffff'
  surface-muted: '#f4f5f9'
  border: '#d8d9e4'
  text: '#1a1a2e'
  text-muted: '#5c5e73'
  success: '#16a34a'
  warning: '#d97706'
  danger: '#dc2626'
  info: '#2563eb'
typography:
  sans: "'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
rounded:
  control: 'rounded-full'
  surface: 'rounded-md'
spacing:
  scale: 'Tailwind default — not re-derived'
components:
  - Button
  - Input
  - FormField
  - Card
  - Alert
  - Spinner
---

# DESIGN.md — Milestone 3

## Brand & Style

**This milestone authors no new visual identity.** Milestone 2 established the design system; Milestone 3 consumes it. This document exists so Milestone 3's screens have a named visual contract to build against — it records the inherited identity by reference and adds only what Milestone 3 genuinely needs that did not exist before.

The identity itself is unchanged and stated once, in the Milestone 2 PRD §4: anchored on Sirma's navy, dialled toward insurance-domain restraint. Formal financial-product UI first, Sirma-branded second. No gradient decoration, no playful illustration.

Milestone 3 raises the stakes on that restraint rather than the decoration: this is the milestone where a click issues a contract. Screens should read as *documentary* — the premium, the number, the coverage dates, and the vehicle are the content; chrome recedes.

## Colors

Every value in the frontmatter is the **existing** `@theme` block in `frontend/src/index.css`, quoted here so this document is readable standalone. **These are not new decisions and must not be re-picked.** Consume them as Tailwind utilities (`bg-primary`, `text-text-muted`, `border-border`), never as literals.

The status quartet — `success` / `warning` / `danger` / `info` — was fixed by Milestone 2's AD-6 before any component consumed it. **Milestone 3 is the first consumer.** It maps as:

| Meaning in Milestone 3 | Token | Where |
|---|---|---|
| Quote accepted; policy active | `success` | Quote status chip, policy status chip |
| Quote nearing expiry | `warning` | Quote list and detail, when validity is running out |
| Quote expired; acceptance refused | `danger` | Quote status chip, acceptance error banner |
| Policy scheduled (coverage not yet started) | `info` | Policy status chip |

No new color is introduced for these. If a state seems to need a fifth color, it does not — reuse one and differentiate by label.

## Typography

Inter, via the existing `--font-sans`. No new face, no new scale.

One Milestone-3-specific rule: **monetary figures and identifiers are never the same weight as their labels.** A premium total, a policy number, and a coverage period are the content a reader is scanning for; render them heavier and larger than the surrounding label text, using the existing type scale. This is the typographic half of the "documentary" direction above and the only typography decision this milestone makes.

## Layout & Spacing

Tailwind's default scale, unchanged. One inherited defect to fix in passing, already logged as deferred work: the header's inner container is `max-w-5xl` while `<main>` is `max-w-2xl`, so the header's left edge does not line up with the body column. Milestone 3 adds list and detail screens where that misalignment becomes more visible; align them.

`max-w-2xl` remains the content column. A quote list and a policy list both fit it comfortably — neither needs a wide table layout, which is what keeps them workable on a phone without a horizontal scroller.

## Elevation & Depth

Unchanged: `Card` provides the only surface elevation in the system. Milestone 3 adds no shadow, no overlay, and **no modal** — see EXPERIENCE.md's `Interaction Primitives`, where the decision not to put acceptance behind a dialog is made and justified.

## Shapes

Unchanged. Controls are pill-shaped (`rounded-full`, from `Button`); surfaces are `rounded-md`. Milestone 3 introduces one new shape need — a **status chip** — and it takes the control shape, not a new one.

## Components

Milestone 3 builds from the six existing primitives. The full behavioral contract is EXPERIENCE.md's `Component Patterns`; this section records only what each one looks like and the one visual addition.

| Component | Milestone 3 use | Visual change |
|---|---|---|
| `Button` | Accept, submit, navigate. `primary` for the single committing action on a screen; `secondary` for everything beside it; `ghost` only on the navy header. | None |
| `Input` | Identity and vehicle capture, coverage start date. `invalid` drives the error border. | None |
| `FormField` | Owns label + error display for every new field (M2 AD-5). | None |
| `Card` | Every list row, every detail panel, the breakdown summary. | None |
| `Alert` | Acceptance failure, expired-quote refusal, empty-list guidance where it is informational rather than an error. | None |
| `Spinner` | Inline in a submitting button; standalone while a list loads. | None |
| **`Badge`** *(new)* | Quote status and policy status. | **The one new component this milestone adds.** |

### Badge — the single new primitive

Milestone 2 deliberately fixed the status color vocabulary *without* building a `Badge`, because nothing consumed it yet (AD-6). Milestone 3 is that consumer: quotes and policies both carry a status a user must read at a glance in a list.

- Takes the four AD-6 status variants, mapped per the Colors table above.
- Control shape (`rounded-full`), small text, tinted background with a readable foreground — matching how `Alert` already renders its variants, one size down.
- Text always carries the meaning. **The color is redundant reinforcement, never the sole carrier** — a status must survive being read in grayscale, and the label is translated like any other string.
- Built exactly like the other primitives: `cva` variants, native `<span>`, tokens only (M2 AD-2, AD-3).

## Do's and Don'ts

**Do**
- Reach for an existing primitive first. If a Milestone 3 screen seems to need a new component, it almost certainly needs a composition of `Card` + `Badge` + `Button`.
- Let the total premium, the policy number, and the coverage period dominate their surfaces visually.
- Keep every value a token reference.

**Don't**
- Introduce a color, a radius, a shadow, or a typeface. The palette is closed for this milestone.
- Restyle anything Milestone 2 already shipped. The header/`<main>` alignment fix is a bug fix, not an invitation.
- Build a table for the quote or policy list. Cards in a single column survive 375px; a table does not.
- Signal a status with color alone.
- Add a modal. See EXPERIENCE.md `Interaction Primitives`.
- Add motion beyond the existing functional transitions. Milestone 2's non-goal stands.

---

**Conflict rule.** This spine and EXPERIENCE.md win over any mock, wireframe, or import. Between the two: DESIGN.md owns how it looks, EXPERIENCE.md owns how it works.
