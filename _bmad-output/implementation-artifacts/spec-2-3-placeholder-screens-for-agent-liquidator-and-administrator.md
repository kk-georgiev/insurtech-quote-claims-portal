---
title: 'Story 2.3: Placeholder Screens for Agent, Liquidator, and Administrator'
type: 'feature'
created: '2026-08-28'
status: 'done'
review_loop_iteration: 0
baseline_commit: 'e5302a17115700af3ba3f847021f90eb22f52dc7'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 2.2 routes each staff role to its own URL, but all three destinations are bare stubs rendering only a one-word `<h2>` (`Agent`, `Liquidator`, `Administrator`). A staff user — or a mentor watching the demo — cannot tell whether they have landed in the right area or whether the app is simply unfinished. FR-6 requires each role to have a reachable, distinctly role-labeled screen.

**Approach:** Grow the three existing staff shell components into static, clearly role-labeled placeholder screens: a "<Role> workspace" heading plus one line saying the real functionality is not part of this milestone. Keep the components separate, keep the routes and `roleHome` untouched, and add a test that each staff route shows its own screen and none of another role's.

## Boundaries & Constraints

**Always:**
- Keep each shell's existing `data-testid` (`agent-shell`, …) — Story 2.2's `router.test.tsx` asserts on them.
- Each screen labels **the area**, not the viewer: "Agent workspace", never "You are signed in as AGENT". There is no route guard until Story 2.4, so an anonymous visitor can reach `/agent`; the copy must not assert an auth state the app has not verified.
- Static and non-interactive (epics.md AC, PRD §4.2): no buttons, links, inputs, or handlers inside the shell. Exactly one screen per staff role, no sub-navigation.
- `app/roleHome.ts` stays the single source of role→route mapping; this story touches neither it nor `router.tsx`.

**Ask First:**
- Adding a role-specific navigation menu (see Design Notes — the sources conflict; the spec assumes **no** menu).
- Changing `ClientShell` beyond its current behaviour.
- Introducing any shared/consolidated placeholder component across the three shells.

**Never:**
- No real staff functionality (agent-assisted quoting, claim queues, tariff admin).
- No route guards or authorization logic — Story 2.4. Direct visits to staff routes stay open.
- No backend changes, no quote-flow frontend, no `react-i18next` / i18n catalogs (Epic 3), no responsive or production visual polish.
- No new test tooling — reuse Story 2.2's Vitest + RTL setup.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Agent area | navigate to `/agent` | screen shows the Agent workspace heading and its coming-soon line | N/A |
| Liquidator area | navigate to `/liquidator` | screen shows the Liquidator workspace heading and its line | N/A |
| Administrator area | navigate to `/administrator` | screen shows the Administrator workspace heading and its line | N/A |
| No cross-contamination | any staff route | the rendered screen contains **no** other staff role's name | N/A |
| Non-interactive | any staff route | the shell contains no button, link, or form control | N/A |
| Still unguarded | direct visit to a staff route while logged out | the screen renders — no redirect (Story 2.4's job) | N/A |

</frozen-after-approval>

> **Compatibility note (added 2026-08-28, after approval — outside the frozen section).**
> Story 1.7 ("Client quote flow — submit and see the breakdown", merged to `dev` as #21)
> landed *after* this spec was frozen but *before* Story 2.3 was rebased onto `dev`.
> It gave `ClientShell` the real quote flow (`QuoteForm`), which supersedes this spec's
> background premise that the client shell had no destination and that Epic 1's
> quote-flow frontend did not exist. Nothing in the frozen Intent, Boundaries, or I/O
> Matrix has been rewritten. Two clarifications for the reader:
> - The **Never** item "no quote-flow frontend" still holds exactly as written — it scopes
>   *this* story out of building one. Story 1.7 built it independently.
> - The **Ask First** item "Changing `ClientShell` beyond its current behaviour" also still
>   holds, and was honoured more strictly than originally planned: the rebase drops Story
>   2.3's `ClientShell` doc-comment edit entirely, so `ClientShell.tsx` does not appear in
>   this story's diff at all.

## Code Map

- `frontend/src/features/shells/{agent,liquidator,administrator}/*Shell.tsx` — each is today `<section data-testid="…-shell"><h2>Role</h2></section>`. These three are the story.
- `frontend/src/features/shells/client/ClientShell.tsx` — **out of scope and untouched**: Story 1.7 (merged first) filled it with the real quote flow, so it is not a placeholder and this story does not appear in its history.
- `frontend/src/app/router.test.tsx:28-34` — asserts `findByTestId(\`${role.toLowerCase()}-shell\`)` for every role, so preserving the testids keeps it green with no edit. `LoginForm.test.tsx` asserts only `pathname`, never shell copy.
- `frontend/src/app/router.tsx:25-31` and `roleHome.ts` — read-only; routes and mapping already exist and are correct. `vitest.config.ts` + `src/test/setup.ts` — existing toolchain, reused unchanged.
- `README.md:70-72` and `frontend/README.md` "Project layout" — both call the staff shells *bare* placeholders pending Story 2.3; need a one-line refresh.
- Sources: `epics.md` Story 2.3 AC; `epic-2-context.md:23` ("static and non-interactive … exactly one placeholder per staff role, with no sub-navigation"); PRD FR-6 / §4.2.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/features/shells/agent/AgentShell.tsx` -- heading "Agent workspace" + one static coming-soon line; keep `data-testid`; add `aria-labelledby` so the section has an accessible name -- makes the area unambiguously Agent's.
- [x] `frontend/src/features/shells/liquidator/LiquidatorShell.tsx` -- same treatment, Liquidator copy.
- [x] `frontend/src/features/shells/administrator/AdministratorShell.tsx` -- same treatment, Administrator copy.
- [x] `frontend/src/features/shells/shells.test.tsx` -- new: for each staff role, mount `routes` at `roleHome(role)` and assert (a) its own heading renders, (b) no other staff role's name appears anywhere in the screen, (c) the shell contains no interactive element. One file, driven by a table, so the cross-contamination matrix is symmetric.
- [x] `README.md` + `frontend/README.md` -- refresh the two lines that call the staff shells "bare" placeholders pending Story 2.3; keep the "no route guards yet — Story 2.4" caveat intact.

**Acceptance Criteria:**
- Given I navigate to `/agent`, when the screen renders, then I see a clearly Agent-labeled static screen stating the real functionality is not yet available.
- Given the same for `/liquidator` or `/administrator`, when rendered, then I see my own distinctly labeled screen and no other role's label anywhere on it.
- Given any staff placeholder screen, when rendered, then it contains no interactive control.
- Given `npm run typecheck`, `npm run build`, and `npm test` in `frontend/`, when run, then all pass and Story 2.2's 20 existing tests still pass unchanged.

## Design Notes

**Proposed copy** (plain English; Epic 3 lifts these into `shells.*` i18n keys later):

| Route | Heading | Line |
|---|---|---|
| `/agent` | Agent workspace | Coming soon — Agent tools are not part of this milestone. |
| `/liquidator` | Liquidator workspace | Coming soon — Liquidator tools are not part of this milestone. |
| `/administrator` | Administrator workspace | Coming soon — Administrator tools are not part of this milestone. |

Shape (one per role, deliberately near-identical but separate files):

```tsx
export function AgentShell() {
  return (
    <section data-testid="agent-shell" aria-labelledby="agent-shell-heading">
      <h2 id="agent-shell-heading">Agent workspace</h2>
      <p>Coming soon — Agent tools are not part of this milestone.</p>
    </section>
  );
}
```

**Conflict in the sources — resolved as "no menu".** `epic-2-context.md:44` (the UJ-2 journey narrative) says a staff user sees "a role-labeled placeholder screen **and a role-specific navigation menu**". But `epic-2-context.md:23` and the PRD §4.2 assumption both say "exactly one placeholder per staff role, with **no sub-navigation** inside a role". The concrete constraint wins over the narrative: no per-role menu. Flagged under **Ask First**.

**Three files, not one.** The three screens are near-identical today, so a shared `PlaceholderShell({ role })` is tempting. They stay separate on purpose: each grows different real functionality in a later milestone, and the DRY saving here is three lines of JSX.

## Verification

**Commands:**
- `cd frontend; npm run typecheck; npm run build; npm test` -- all clean; test count goes from 20 to 20 + the new shell cases, with no pre-existing test modified.

**Manual checks:**
- `npm run dev`, visit `/agent`, `/liquidator`, `/administrator` -- each shows its own workspace heading and coming-soon line, and nothing clickable inside the screen body.

## Suggested Review Order

**The three screens**

- Entry point: the shape all three follow — area-labeled heading, one static line, `aria-labelledby`, testid preserved.
  [`AgentShell.tsx`](../../frontend/src/features/shells/agent/AgentShell.tsx)

- Same treatment, Liquidator and Administrator copy.
  [`LiquidatorShell.tsx`](../../frontend/src/features/shells/liquidator/LiquidatorShell.tsx) · [`AdministratorShell.tsx`](../../frontend/src/features/shells/administrator/AdministratorShell.tsx)

**The test that pins them**

- Copy stated verbatim from the Design Notes table, never derived from the role name.
  [`shells.test.tsx:26`](../../frontend/src/features/shells/shells.test.tsx#L26)

- Cross-contamination over the full `ROLES`, word-boundary matched — "Client" on a staff screen is the same defect as "Agent" on the Liquidator screen.
  [`shells.test.tsx:75`](../../frontend/src/features/shells/shells.test.tsx#L75)

- The AC's "non-interactive" clause, driven from outside: a DOM selector cannot see React handlers.
  [`shells.test.tsx:95`](../../frontend/src/features/shells/shells.test.tsx#L95)

- Pins the pre-guard behaviour Story 2.4 must deliberately flip: renders logged out, no redirect.
  [`shells.test.tsx:117`](../../frontend/src/features/shells/shells.test.tsx#L117)

**Docs & deferred**

- Both READMEs: staff shells are no longer "bare"; the client shell hosts Story 1.7's quote flow; guards are still Story 2.4.
  [`README.md:70`](../../README.md#L70)

- Two deferred entries: per-route `document.title`, and exporting `StaffRole` from `roleHome.ts` for Story 2.4.
  [`deferred-work.md`](deferred-work.md)
