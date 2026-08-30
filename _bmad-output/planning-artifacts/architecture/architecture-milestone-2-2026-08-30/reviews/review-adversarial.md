# Adversarial Review — Motor Insurance Portal Milestone 2 Design System Spine

**Target:** `architecture-milestone-2-2026-08-30/ARCHITECTURE-SPINE.md`
**Method:** For each Rule, construct two developer units one level down (each building a different Milestone-1 screen or shell from this spine) who each satisfy every AD, Convention, and Inherited invariant to the letter, and check whether their outputs are still guaranteed to compose. Any pair that diverges is a hole; every hole is mapped to the AD that fails to close it.
**Verdict:** CONCERNS — the spine is sound on styling mechanics (tokens, variants, semantics) but leaves the *vocabulary* and *ownership* layer underspecified. None of the found gaps are exotic; several will fire the first time two stories land in the same sprint.

---

## Finding 1 — Two incompatible token vocabularies land in the same `@theme` block

**Units:** Developer A (auth screens, FR-3) vs. Developer B (quote flow, FR-4/5), both adding tokens PRD §4 didn't pin down (spacing, secondary accent shades).

**Letter compliance:** AD-1 only requires that "every design token from PRD §4 ... is defined exactly once, in an `@theme` block." It says nothing about naming scheme.

- Dev A names by scale, Tailwind-idiomatic: `--spacing-4: 1rem`, `--color-accent-500: ...`.
- Dev B names semantically, component-idiomatic: `--spacing-card-padding: 1.5rem`, `--color-brand: ...` for what is visually the same accent Dev A already tokenized as `--color-accent-500`.

Both satisfy "defined exactly once" (each name individually is defined once) and "resolves to a Tailwind utility class sourced from that block." Nothing catches that the *same value* now has two names, or that the token file has no consistent naming grammar. `frontend/features/shells/` (a third unit, FR-6/7) now has no way to know which convention to follow and may introduce a third.

**Root cause:** AD-1 governs *where* tokens live and that hardcoded values are banned; it has no naming-convention rule (semantic-role names vs. scale names vs. per-component names).

**Fix:** Tighten AD-1 (or add AD-1a) with a token-naming grammar, e.g. "tokens are named by semantic role (`--color-brand`, `--color-danger`, `--spacing-sm/md/lg`), never by component or by raw scale index" — and require a check for an existing token of the same resolved value before adding a new name.

---

## Finding 2 — `className` override boundary is stated as intent, not as anything cva/tailwind-merge can enforce

**Units:** Developer A (auth submit button, needs a slightly different margin) vs. Developer B (quote result screen, needs a "muted" one-off button shade with no existing variant).

**Letter compliance:** The Consistency Convention says `className` is merged via `cn()` "for one-off layout positioning only — never to override the component's own variant styling." AD-2 separately says a screen needing a new variant "adds it to the component — it never fakes one by composing raw utility classes around an existing variant."

- Dev A passes `className="mt-4"` — obviously compliant.
- Dev B, facing a single edge-case button that needs `bg-slate-400`, reasons (correctly, by the letter) that this isn't a reusable "variant" — it's a one-off — so AD-2's "needing a new variant" clause doesn't apply, and passes `className="bg-slate-400 text-white"` through the same `cn()` path Dev A used.

`cn()` / tailwind-merge cannot distinguish a layout utility from a color utility — both are just class strings resolved by specificity/order. The Rule's boundary ("layout positioning only") is a review-time judgment call, not something the mechanism in AD-2 can catch, and AD-4 has already stated review is the *only* enforcement. Two components authored under the identical rule now diverge: one whose visual identity is fixed by its variant, one whose color is caller-defined per call site — exactly the ad hoc `className` drift AD-2 exists to prevent, achieved without technically violating AD-2's text.

**Root cause:** The layout-only restriction on `className` is a semantic distinction (what kind of utility) enforced by a mechanism (`cn()`) that only understands syntax (which utility wins). AD-4 already accepts no automated enforcement — but nothing even gives reviewers a checkable rule (e.g., an allowed prefix list) to enforce by eye.

**Fix:** Tighten the Convention: enumerate the utility categories `className` may carry (spacing/position/sizing prefixes only — `m-*`, `p-*`, `w-*`, `flex-*`, `grid-*`, `gap-*`) and explicitly ban color/typography/border utilities from caller-supplied `className`, so a reviewer (or later a lint rule) has a bright line instead of an intent statement.

---

## Finding 3 — Two owners for the same "field error" state, with two incompatible shapes

**Units:** Developer A (auth login/register forms, FR-3) vs. Developer B (quote form, FR-4/5), each building the error-display wiring between `Input` and `FormField` first, since neither AD-2 nor AD-3 assigns ownership of validation-error rendering to one or the other.

- Dev A puts the error state on `Input`: `<Input error={boolean} />` — a cva variant on `Input` itself (`variant: 'default' | 'error'`) — and renders the message text as a sibling inside `FormField`, keyed only by whether the parent passed a truthy error.
- Dev B puts the error state on `FormField`: `<FormField error={string}>` where the string *is* the message, and `FormField` clones the child `Input` to inject `aria-invalid`/`aria-describedby`.

Both comply with AD-2 (variant surface added to the appropriate `components/ui/` file, named `variant`/no naming violation — Dev A's variant is even named per the Convention) and AD-3 (both still render a real `<input>` and `<label>`). Convention "Naming" only fixes the *prop name* for variant axes, not the *shape* of a cross-cutting concern like error. The result: `Input.error` is a `boolean` in one code path and implicitly expected to be a `string` message in the other. The first screen that needs both a field with an inline message AND matches the other screen's usage (e.g. a later FR-9 error-polish story touching both auth and quote) cannot reuse either pattern without a rewrite — two owners, two shapes, for one concern.

**Root cause:** No AD or Convention assigns ownership of cross-cutting field state (error, disabled-reason, helper text) to a single component in the four-component set, and none constrains its prop shape.

**Fix:** Add a Rule (or extend AD-3) naming `FormField` as the sole owner of error/helper-text rendering and ARIA wiring, with a fixed prop contract (e.g. `error?: string`), and stating `Input` never grows its own `error` variant — only a generic `invalid` boolean it receives from `FormField`, not from screen code directly.

---

## Finding 4 — AD-4 explicitly licenses four incompatible ad hoc "status" vocabularies across the four role shells

**Units:** Any two of the four role-shell developers (FR-6/7 — e.g., Agent shell vs. Underwriter shell), each needing to show a claim/quote status indicator (approved/pending/rejected, or similar) that has no home in the base set (`Button`, `Input`, `FormField`, `Card` — no `Badge`/`Chip`/`Pill`).

**Letter compliance:** AD-4 states plainly: "nothing lints against a raw `<button>`/`<input>` outside `components/ui/`... enforced by code review only — an explicitly accepted risk, not a silent gap." That license extends to any element, not just button/input — there is no base "status" primitive to adopt in the first place, so there's nothing for a reviewer to redirect either shell dev *toward*.

- Agent shell dev hand-builds a status pill with Tailwind utilities resolving to tokens (fully AD-1-compliant, fully AD-3-compliant — a real `<span>`), using `success`/`warning`/`danger` as the color-key vocabulary.
- Underwriter shell dev, working in parallel, hand-builds the same concept with `approved`/`pending`/`rejected` as the color-key vocabulary and a different color mapping (e.g. rejected → orange, not red).

Both are 100% compliant with every AD in the spine — AD-1 (tokens only), AD-3 (native `<span>`), AD-4 (explicitly no enforcement outside `components/ui/`) — yet the app now ships two status-color systems that don't even agree on which color means "bad." The Capability Map assigns FR-6/7 to "AD-2, AD-3, AD-4, Inherited AD-10" — none of which requires a shared status primitive to exist before shells are built.

**Root cause:** The base component set (Button/Input/FormField/Card) does not cover the one atom (status indicator) that is almost certain to be needed independently by multiple role shells in the same milestone, and AD-4's accepted risk is scoped ("component-library adoption") without noting that *undefined* primitives create a worse failure mode than *unadopted* ones.

**Fix:** Either (a) add a fifth base primitive (`Badge`/`StatusTag`) to AD-2's governed set with a fixed status-vocabulary enum shared by all shells, or (b) add a narrow AD stating that any visual pattern needed by two or more of the four role shells must be promoted to `components/ui/` before the second shell implements it — closing the "first past the post owns the ad hoc pattern" gap AD-4 leaves open.

---

## Finding 5 — `Card`'s internal slot API is unconstrained, inviting two incompatible extensions of the same file

**Units:** Developer A (quote result screen, FR-4/5 — "quote summary card") vs. Developer B (a role shell's "claim summary card", FR-6/7), both extending the single shared `Card` component in parallel stories.

**Letter compliance:** AD-2 requires the variant surface to live in the component via cva, and AD-3 requires native elements — neither constrains a component's *composition* API (compound-component slots vs. flat props).

- Dev A extends `Card` with a compound-component pattern: `<Card><Card.Header/><Card.Body/></Card>`, matching how they'd naturally lay out a multi-section quote summary.
- Dev B, unaware of Dev A's in-flight branch, extends the same `Card.tsx` with a flat `title`/`footer` prop API: `<Card title="Claim #123">...</Card>`.

Both changes are individually AD-2/AD-3-compliant (cva variants where relevant, native `<div>`/`<section>` elements, `variant`/`size` naming untouched). But they are two structurally incompatible extensions of the *same single file* the spine designates as "the only variant surface" — whichever merges second either breaks the other screen's usage or forces a redundant dual API (`title` prop *and* `Card.Header` child) bolted onto one component, which is exactly the "two components... expressing an axis through incompatible logic" AD-2 was written to prevent, just at the composition-API layer AD-2's text doesn't reach.

**Root cause:** AD-2 fixes the *variant prop* vocabulary (`variant`, `size`) but not the *composition* vocabulary (props vs. slots/compound components) for the same four primitives, and nothing requires a single owner or a pre-registered shape for `Card`'s content model before two features build against it concurrently.

**Fix:** Add a Rule fixing each of the four components' composition style up front (e.g., "all four components are flat-prop only; no compound/slot sub-components are introduced in Milestone 2") so two concurrent stories cannot each invent a different extension shape for the same shared file.

---

## Additional lower-severity observations (not full findings)

- **FR-9 loading-state spinner**: "Icon library" is explicitly Deferred, but FR-9 (loading/error polish) plausibly needs a spinner glyph. Two screens built under AD-2/AD-3 with no shared icon primitive will each invent their own spinner (inline SVG vs. CSS-border-spin `<div>`), which is spine-permitted divergence given the Deferred section explicitly punts icons to "the story that first needs one" — except two stories in the same milestone (auth and quote) may hit that need simultaneously, and Deferred doesn't say what happens when a second story needs it after the first invented a bespoke, non-`components/ui/` answer.
- **`@theme` block as a single-file bottleneck**: AD-1's single-file requirement is right for consistency but guarantees every concurrent story branch touches `index.css`, producing routine merge conflicts across the two-person team's parallel stories. Process risk, not a compatibility hole — flagged for awareness only.

---

## Summary Table

| # | Two units | Shared artifact that diverges | AD that fails to prevent it | Suggested closing move |
| - | --- | --- | --- | --- |
| 1 | Auth-screen dev vs. quote-flow dev | `index.css` `@theme` token names | AD-1 (no naming grammar) | Add semantic-role naming rule + duplicate-value check |
| 2 | Auth-screen dev vs. quote-flow dev | `className` override intent on any `ui/` component | AD-2 / Convention (intent, not mechanism) | Enumerate allowed utility-class prefixes for `className` |
| 3 | Auth-screen dev vs. quote-flow dev | `Input`/`FormField` error-state shape | AD-2 / AD-3 (no ownership assignment) | Assign `FormField` sole ownership + fixed `error?: string` contract |
| 4 | Any two of the four role-shell devs | Status/badge color vocabulary | AD-4 (explicitly unenforced, and no primitive exists to adopt) | Promote a shared `Badge`/`StatusTag` primitive, or a "second-user promotes to `ui/`" rule |
| 5 | Quote-flow dev vs. role-shell dev | `Card`'s composition API (slots vs. props) | AD-2 (fixes variant vocabulary, not composition vocabulary) | Fix composition style (flat-props only) for all four components up front |

**Overall verdict: CONCERNS.** The spine correctly locks down *where* styling lives (tokens, `components/ui/`, native elements) and heads off the specific over-engineering risk it names (linting). It does not yet lock down the *vocabulary* two independent units will need to share — token names, error-state shape, status semantics, and component composition style — and AD-4's accepted-risk framing is broader than its stated scope, since it also covers primitives that don't exist yet for anyone to "adopt." None of the five holes require new tooling; each is closeable with a short vocabulary/ownership rule added to an existing AD or Convention.
