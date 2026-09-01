---
title: 'Story 9.2: Documentation and Legacy-CSS Cleanup'
type: 'chore'
created: '2026-09-01'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '4f739c3d8e8cd0dbf478c63b3710c5827cf3ed00'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The README's `## Status` section is several epics stale and carries no bonus-malus/driving-experience provenance disclosure (FR-M3-15, NFR-8). `frontend/src/index.css`'s `@layer legacy` block still renders a hardcoded `#e2e8f0` hex on a live screen, leaving Milestone 2's own FR-1 ("no hardcoded hex on a touched screen") not literally true.

**Approach:** Rewrite the README's `Status` section to the real current state, with the two provenance disclosures folded in. Delete every legacy CSS rule with no live consumer, migrate the survivors to tokens/existing patterns, and correct the stale block comment. Investigation found the epic's own "four survivors" claim is stale: `main > section` is a fifth live rule, because `MyQuotes.tsx`/`MyPolicies.tsx` never got the `border-0 bg-transparent p-0` treatment the four role shells received in Story 5.4 — so this story also closes that gap, which is what actually makes `main > section` deletable.

## Boundaries & Constraints

**Always:**
- Every legacy rule confirmed dead (see Code Map) is deleted outright: `main`, `main > section h2`, `main section section h2`, `[data-testid='quote-result'] h3`, all `form *` rules, all `button`/`button:*` rules, `dl`, `dd`.
- The five confirmed-live rules are migrated, not just left in place: `box-sizing` reset and `body` stay as genuinely global rules (no component-level equivalent makes sense); `dt`'s `font-weight:600` becomes a Tailwind utility on every `<dt>` that currently relies on it (`QuoteResult.tsx` and `PolicyDetail.tsx`); `[data-testid='quote-result']`'s `padding-top`/`border-top:1px solid #e2e8f0` becomes `border-t border-border pt-6` (or equivalent) on `QuoteResult.tsx`'s own `<section>`, reusing the existing `--color-border` token exactly as `Card.tsx`'s own `footer` divider already does — no new hardcoded hex, no new token.
- `main > section`'s card chrome becomes dead, not migrated: add `border-0 bg-transparent p-0` to `MyQuotes.tsx:57` and `MyPolicies.tsx:64`'s `<section>`, matching the exact className the four role shells already use for the same purpose (dropping outer card chrome when the content already owns its own `Card`).
- The stale block comment (lines 83-90) is rewritten to name only what actually survives: `box-sizing`, `body`. No comment claims a rule is "still load-bearing" when it isn't.
- The README `Status` section states the real current epic/story state, records driving experience is deliberately excluded as a rating factor, and states the bonus-malus scale is this project's own demo model — not official or regulatorily determined Bulgarian market values (matching the wording already used in `BonusMalusClass.java`'s javadoc / the frontend `bonusMalusNote` string / `OpenApiConfig`'s description from Story 9.1).
- The full existing frontend test suite passes unmodified — no test file is edited as part of this story.

**Ask First:** none identified.

**Never:**
- Do not introduce a new Card prop for the divider — `Card.tsx`'s existing `footer` pattern (`border-t border-border pt-4`) is the established idiom; reuse it directly on `QuoteResult.tsx`'s wrapper rather than adding API surface for a single consumer.
- Do not touch any test file — the AC requires the suite to pass unmodified, which is itself the proof no observable behavior changed.
- Do not preserve the `#e2e8f0` hex value anywhere — the whole point of this story is that Milestone 2's FR-1 becomes literally true.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Quote breakdown divider | `/`, a calculated quote | `QuoteResult`'s breakdown still shows a visible top divider above it, now via `border-border` token, not `#e2e8f0` | N/A |
| Quote/policy `<dt>` weight | Any quote or policy detail screen | Every `<dt>` in the breakdown still renders semi-bold, now via a Tailwind utility, not legacy CSS | N/A |
| My Quotes / My Policies card chrome | `/quotes`, `/policies` | The outer section's own chrome is gone (now a plain, unstyled wrapper); each row's own `Card` is unaffected and still shows its own border/shadow | N/A |
| Full suite after cleanup | `npm test` | All existing tests pass, unmodified, 0 failures | N/A |

</frozen-after-approval>

## Code Map

- `frontend/src/index.css:63-232` -- the entire `@layer legacy` block to rewrite. Exact per-rule live/dead verdict (investigation-confirmed, corrects `epic-9-context.md`'s stale "four survivors" claim):
  - **DEAD, delete outright:** `main` (94-98, overridden by `RootLayout.tsx`'s own max-width/padding utilities), `main > section h2` (117-119, redundant — consumers already carry `mt-0`), `main section section h2` (125-127, no such nesting exists anywhere), `[data-testid='quote-result'] h3` (139-141, redundant — `Card`'s own heading already carries `mt-0`), `form div`/`form label`/`form input,select,textarea`/`:focus`/`:disabled` (145-179, every form now uses `FormField`/`Input`/`Select`), `button`/`:hover`/`:focus-visible`/`:disabled` (181-204, every button now uses `components/ui/Button` or its own token-based styling), `dl` (217-222, both consumers set their own `display`/layout), `dd` (229-231, no consumer needs the `margin:0` reset).
  - **LIVE, migrate:** `box-sizing` reset (64-68, keep as-is — genuinely global), `body` (70-81, keep as-is — genuinely global), `main > section` (109-115, live via `MyQuotes.tsx`/`MyPolicies.tsx` — NOT dead as the epic context claims; becomes dead only after this story's `MyQuotes.tsx`/`MyPolicies.tsx` className change), `[data-testid='quote-result']` (133-137, migrate `padding-top`/`border-top` to a Tailwind utility on `QuoteResult.tsx`), `dt` (224-227, migrate `font-weight:600` to a utility on every `<dt>` consumer — `color:#475569` is already dead, overridden by `text-text-muted`).
  - Stale comment to correct: lines 83-90 (claims `main`, `main > section`, forms, `button`, `[role='alert']`, `dl` are "still load-bearing" — `[role='alert']`/register-success are already noted removed two paragraphs below in the same file, and most of the rest are dead per this investigation).
- `frontend/src/features/quote/QuoteResult.tsx:42` -- `<section data-testid="quote-result" ... className="mt-6">`; add `border-t border-border pt-6` alongside `mt-6` (drop `mt-6`'s redundant overlap with the old `margin-top`, keep spacing equivalent).
- `frontend/src/features/quote/QuoteResult.tsx` -- every `<dt className="text-text-muted">` (lines ~45,52,57,62,70,75,80,95) needs a `font-semibold` (or equivalent weight utility) added; the total-row `dt` at line 85 already has its own `font-semibold` and needs no change.
- `frontend/src/features/policy/PolicyDetail.tsx` -- every `<dt>` (lines ~115,121,130,137,142,147) needs the same weight utility added.
- `frontend/src/features/quote/MyQuotes.tsx:57` -- add `className="border-0 bg-transparent p-0"` to the outer `<section>`, matching `ClientShell.tsx:22`'s exact pattern (its own comment: "drops the legacy `main > section` card chrome... so `QuoteForm`'s own `Card` is the only card").
- `frontend/src/features/policy/MyPolicies.tsx:64` -- same `border-0 bg-transparent p-0` addition.
- `frontend/src/components/ui/Card.tsx:41` -- reference pattern for the divider migration (`border-t border-border pt-4` on the `footer` block) — reuse this exact idiom, don't invent a new one.
- `README.md:8-18` -- the `## Status` section to rewrite; current text is the stale "Milestone 1 — Epic 1 complete, Epic 2 in progress" paragraph. Real state to describe: Epics 1-8 complete (client quote flow, staff roles, i18n, Docker deployment, quote validity/My Quotes, session handling, accept-a-quote/issue-a-policy/My Policies), Epic 9 (this epic) in progress. Keep the existing pointer to `## Planning & progress` (line 152's link to `sprint-status.yaml`) as the live source rather than re-deriving every story's status inline.
- `README.md` -- no existing bonus-malus/driving-experience text anywhere (confirmed by grep) — both disclosures are net-new sentences in `Status`, not edits.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/index.css` -- delete all confirmed-dead legacy rules, migrate `dt`'s weight and `[data-testid='quote-result']`'s divider out of the block, correct the stale comment -- closes Milestone 2's FR-1 gap and removes dead CSS
- [x] `frontend/src/features/quote/QuoteResult.tsx` -- add `border-t border-border pt-6` to the outer section, add a weight utility to every `<dt>` -- replaces the two migrated legacy rules with their token-based equivalents
- [x] `frontend/src/features/policy/PolicyDetail.tsx` -- add the same `<dt>` weight utility -- this consumer relies on the same legacy `dt` rule and would otherwise silently lose its bold labels once the rule is deleted
- [x] `frontend/src/features/quote/MyQuotes.tsx` -- add `border-0 bg-transparent p-0` to the outer section -- drops reliance on `main > section`, matching the established role-shell pattern
- [x] `frontend/src/features/policy/MyPolicies.tsx` -- same `border-0 bg-transparent p-0` addition -- same reason, second consumer of the same legacy rule
- [x] `README.md` -- rewrite the `Status` section: real current epic/story state, the driving-experience-excluded note, the bonus-malus demo-data disclosure -- closes FR-M3-15 and NFR-8's documentation-surface requirement

**Acceptance Criteria:**
- Given `frontend/src/index.css` after this story, when the `@layer legacy` block is read, then it contains only the `box-sizing` reset and the `body` rule — nothing else, and no rule anywhere in the file sets `#e2e8f0`.
- Given the full existing frontend test suite, when it runs after this story's changes, then every test passes with no test file modified (proves no observable behavior changed).
- Given `/quotes` and `/policies` rendered in a browser, when compared to their appearance before this story, then each individual quote/policy row's own `Card` chrome is visually unchanged, and the outer section's now-removed card-within-a-card chrome is the only visual difference.
- Given the README `Status` section, when read after this story, then it names the real current epic state (not three epics stale), states driving experience is deliberately excluded as a rating factor, and states the bonus-malus scale is this project's own demo model, not official Bulgarian market values.

## Spec Change Log

## Design Notes

**Why `main > section` is reclassified from dead to live-then-migrated:** the epic context (compiled from planning docs, which predate this investigation) repeated Epic 5 retro's "four survivors" count. Direct investigation found `MyQuotes.tsx:57` and `MyPolicies.tsx:64` never received the `border-0 bg-transparent p-0` treatment the four role shells got in Story 5.4 — an apparent oversight when Stories 6.3/8.3 built those screens, not a deliberate choice (nothing in either story's spec discusses it). Fixing that omission is what actually makes the legacy rule deletable, and is squarely in this story's own scope (a screen still depending on the legacy stylesheet for its chrome is exactly the gap Story 9.2 exists to close) — treated as part of this story rather than a separately deferred item, since deleting `main > section` without this fix would visibly regress two live screens.

**Why `border-border` (`#d8d9e4`) replaces `#e2e8f0` instead of a new token:** no existing token equals the old hex, but `Card.tsx`'s own `footer` divider already uses `border-border` for the identical visual role (a top divider). Introducing a second "divider gray" token for one screen would fragment the palette for no visual gain the design ever asked for; reusing the established token is the smaller, more consistent change.

## Verification

**Commands:**
- `npm run typecheck` -- expected: clean
- `npx vitest run --maxWorkers=2` -- expected: full existing suite passes, 0 failures, no test file changed in the diff
- `npm run build` -- expected: clean production build (catches any Tailwind class typo the dev build might tolerate)

**Manual checks (if no CLI):**
- Open `/`, calculate a quote, confirm the breakdown still shows a visible divider above it and every label (`<dt>`) still reads semi-bold.
- Open `/quotes` and `/policies` (as a logged-in client with at least one quote/policy), confirm each row still renders its own card border/shadow, and the outer page no longer shows a second, redundant white card behind the list.
- Open `README.md`, confirm the `Status` section reads current and includes both disclosures.

## Suggested Review Order

**Legacy CSS retirement**

- Entry point: the `@layer legacy` block shrinks from 20 rules to 2 (`box-sizing` + `body`) — everything else deleted or migrated below.
  [`index.css:63`](../../frontend/src/index.css#L63)

- The corrected block comment, naming only what actually survives — was previously stale, claiming rules load-bearing that weren't.
  [`index.css:83`](../../frontend/src/index.css#L83)

**Migrating the survivors (token-based replacements for the deleted hex/weight rules)**

- The `#e2e8f0` divider becomes `border-t border-border pt-6`, reusing `Card.tsx`'s own existing footer-divider token rather than a new one.
  [`QuoteResult.tsx:45`](../../frontend/src/features/quote/QuoteResult.tsx#L45)

- Every `<dt>` in the quote breakdown gains `font-semibold`, replacing the deleted `dt { font-weight: 600 }`.
  [`QuoteResult.tsx:47`](../../frontend/src/features/quote/QuoteResult.tsx#L47)

- The same `<dt>` weight restoration for the policy detail screen — a second, easy-to-miss consumer of the same legacy rule.
  [`PolicyDetail.tsx:118`](../../frontend/src/features/policy/PolicyDetail.tsx#L118)

**Closing the gap the epic context got wrong (`main > section` was live, not dead)**

- `MyQuotes.tsx`'s outer section drops the legacy card chrome it was still silently depending on — the fix that actually makes `main > section` deletable.
  [`MyQuotes.tsx:64`](../../frontend/src/features/quote/MyQuotes.tsx#L64)

- Same fix, second consumer — found by direct investigation, not by the epic's own (stale) survivor count.
  [`MyPolicies.tsx:71`](../../frontend/src/features/policy/MyPolicies.tsx#L71)

**Documentation**

- The README `Status` rewrite: real current epic state plus both provenance disclosures (bonus-malus demo data, driving experience excluded).
  [`README.md:8`](../../README.md#L8)
