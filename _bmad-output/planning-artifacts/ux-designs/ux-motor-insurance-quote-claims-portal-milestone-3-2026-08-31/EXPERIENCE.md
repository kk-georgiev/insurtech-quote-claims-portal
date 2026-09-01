---
title: 'Motor Insurance Portal — Milestone 3 Experience Spine'
status: final
created: '2026-08-31'
updated: '2026-08-31'
design: './DESIGN.md'
sources:
  - '_bmad-output/planning-artifacts/prds/prd-motor-insurance-quote-claims-portal-milestone-3-2026-08-31/prd.md'
  - '_bmad-output/planning-artifacts/architecture/architecture-motor-insurance-quote-claims-portal-2026-08-23/ARCHITECTURE-SPINE.md'
  - '_bmad-output/planning-artifacts/architecture/architecture-milestone-2-2026-08-30/ARCHITECTURE-SPINE.md'
---

# EXPERIENCE.md — Milestone 3

## Foundation

**Form-factor:** responsive web SPA. One layout, phone through desktop. No native app, no separate mobile surface. Inherited from Milestone 2, which made every existing screen usable from ~375px up.

**UI system:** the Milestone 2 design system — Tailwind v4 CSS-first tokens in `frontend/src/index.css` plus the primitives in `frontend/src/components/ui/`. Visual identity is [DESIGN.md](./DESIGN.md); this document specifies only behavior. Where a primitive already defines a behavior (tap-target sizing, error display ownership, focus rings), Milestone 3 inherits it silently and does not restate it.

**Audience:** one role. Every surface in this milestone is CLIENT-only, behind the existing `RoleGuard`. Staff shells stay placeholders — the PRD makes that an explicit non-goal, and no screen here has a staff variant.

**What changes for the user:** until now the portal answered *what would this cost?* and forgot the answer. Milestone 3 makes the answer durable, gives it a deadline, and lets the user act on it. Three of the four new surfaces exist purely so an answer can be found again later.

## Information Architecture

The client shell gains two branches. Nothing else in the route table moves.

```text
/  (client shell — RoleGuard: CLIENT)
├── new quote            existing quote form + inline result
├── /quotes              My Quotes            ← new
│   └── /quotes/:id      Quote details        ← new  (acceptance happens here)
└── /policies            My Policies          ← new
    └── /policies/:id    Policy details       ← new
```

**Surface closure.** Every Milestone 3 requirement lands on a surface, and every surface serves a requirement:

| Surface | Delivers |
|---|---|
| My Quotes | FR-M3-01 history, FR-M3-02 validity, FR-M3-03 status |
| Quote details | FR-M3-16 bonus-malus in the breakdown, FR-M3-02/03 state, FR-M3-04 coverage start, FR-M3-05 acceptance, FR-M3-08 identity capture |
| My Policies | FR-M3-10 list, FR-M3-09 status |
| Policy details | FR-M3-06 number, FR-M3-07 snapshot, FR-M3-09 status, FR-M3-10 detail |
| Existing quote form | FR-M3-16 bonus-malus input |

**Navigation.** "My quotes" and "My policies" join the header nav for an authenticated CLIENT, beside the existing controls. Two links, not a nested menu — at this size a menu is more chrome than content. On a phone the nav wraps rather than collapsing behind a hamburger: at four items a disclosure control costs more than it saves.

**Entry points to acceptance.** Exactly one — the quote detail screen. The list never accepts inline. A contract is not issued from a row in a list, and the user must have seen the breakdown and the coverage dates on screen before committing.

**Where a new policy lands.** Issuance redirects to that policy's detail screen, not back to the list. The user's question at that moment is *"did it work, and what did I get?"* — the answer is the policy itself, with its number visible.

## Voice and Tone

Plain, specific, and calm. This is money and a contract; confidence comes from precision, not reassurance. Brand voice sits in [DESIGN.md](./DESIGN.md) `Brand & Style`.

- **Name the thing that happened, not the mechanism.** "This offer expired on 12 September" — not "Quote status is EXPIRED".
- **Never blame the user.** An expired quote is a fact with a next step, not a mistake.
- **Every dead end carries an exit.** Expired offer → "Calculate a new quote". Empty list → the action that fills it.
- **Never imply legal or regulatory authority.** The premium figures are a demo tariff (M3 PRD FR-M3-16 provenance constraint). Copy describes what the portal calculated; it never says what insurance "costs" as a market fact, and no screen presents the bonus-malus scale as an official or regulated rate.
- **No exclamation marks. No congratulation.** Issuing a policy is a transaction, not an achievement.

All copy is authored in both languages at once — see `Bilingual Behaviour`.

## Component Patterns

Behavioral contracts only. Visual specs are [DESIGN.md](./DESIGN.md) `Components`.

**`Badge` (new).** Renders a quote or policy status. Text carries the meaning; the `{colors.success|warning|danger|info}` variant reinforces it. Never interactive — a status is not a control.

**List row (composition, not a component).** `Card` + `Badge` + the identifying figures. The whole row is one link target, not a card with a "View" button inside it — a single large target beats a small one on a phone, and the row has exactly one destination.

**Breakdown summary.** The existing `QuoteResult` pattern, reused verbatim on both the quote detail and the policy detail screen. The policy shows the *snapshotted* breakdown, and it is presented identically to the quote's — a user comparing the two must be able to see they match, which is the visible proof of FR-M3-07.

**Acceptance form.** `FormField` + `Input` per field, `Button` to commit. No new form machinery. This milestone adds its second and third forms, which is the trigger the M3 PRD set for extracting the duplicated form-state logic into a shared hook — behavior stays identical, ownership moves.

**Status vocabulary.** Four quote states and three policy states, each with one label per language and one variant. Fixed here so no screen invents an eighth:

| Entity | State | Variant | Reads as |
|---|---|---|---|
| Quote | Calculated, still valid | `info` | Valid until \<date\> |
| Quote | Calculated, expiring soon | `warning` | Expires in \<n\> days |
| Quote | Accepted | `success` | Accepted — links to the policy |
| Quote | Expired | `danger` | Expired on \<date\> |
| Policy | Scheduled | `info` | Starts \<date\> |
| Policy | Active | `success` | Active until \<date\> |
| Policy | Expired | `text-muted` | Ended \<date\> |

An expired *policy* is deliberately neutral, not `danger`: a policy running its full term and ending is the normal, successful outcome. An expired *quote* is a lost opportunity the user may want to act on. Same word, different meaning, different treatment.

## State Patterns

Every new surface handles four states. None may be skipped, and none may be represented by a blank screen.

**Loading.** Standalone `Spinner` with a translated label while a list or detail loads. Inline `Spinner` inside the button, label retained, button disabled, while a submission is in flight — the existing pattern from Milestone 2's Story 5.6.

**Empty.** Distinct from loading and from error, and never an error tone. Each empty state names why it is empty and offers the one action that fills it:

- No quotes yet → "Calculate your first quote" → the quote form.
- No policies yet → "Accept one of your quotes to get a policy" → My Quotes. If the user *also* has no quotes, point at the quote form instead — never send someone to a second empty screen.

**Error.** `Alert` with `danger`, carrying a translated message keyed off the backend's stable error `code` (AD-7/AD-8), never raw backend prose. A failed load offers a retry; a failed submission leaves every entered value in place.

**Expired / accepted — the two states this milestone exists to express.** These are not errors and must not look like them:

- **Expired quote.** The detail screen still renders in full: the breakdown stays visible and readable. The acceptance form is replaced — not merely disabled — by an explanation and a "Calculate a new quote" action. A disabled button invites the user to hunt for what would enable it; there is nothing.
- **Accepted quote.** Same treatment, different tone. The breakdown stays; the acceptance form is replaced by a link to the resulting policy. The quote is not stale data — it is the origin of a contract, and the user's next question is where that contract is.

**Race between the two.** A quote can expire while its detail screen is open. Acceptance is refused server-side, and the client re-reads the quote and re-renders it as expired, with the refusal explained in the same beat. The UI never asserts a quote is acceptable on the strength of what it fetched a minute ago; the backend is the authority (M1 AD-4).

**Session loss.** A 401 on any call clears the token and returns the user to login (FR-M3-12). From the acceptance screen this is the most consequential instance, and the guarantee that matters is stated in the flow below: **nothing was issued.**

## Interaction Primitives

**Acceptance is a screen, not a dialog.** The most conventional pattern here would be a confirmation modal. Rejected, for three reasons: the identity and vehicle fields are real input, not a yes/no, and a modal is a poor container for a form on a phone; a modal would put the breakdown — the thing being agreed to — behind an overlay at the exact moment it matters most; and the system has no modal primitive, so adding one is a design-system change this milestone's own non-goals forbid. Acceptance is therefore a section of the quote detail screen, below the breakdown, in reading order: *what you are buying → who you are → when it starts → commit.*

**Committing action is single and unambiguous.** One `primary` button per screen. Its label names the outcome ("Accept and issue policy"), never a generic "Submit" or "Confirm".

**Double-submit.** Guarded in the UI as Milestone 2's forms already do — but the UI guard is a courtesy, not the guarantee. The guarantee is the database constraint behind FR-M3-05. Two accepted clicks produce one policy and one success screen, never a duplicate or a confusing second error.

**Dates.** The coverage start date is a native date input. No custom picker — a custom picker is a component, a locale problem, and an accessibility liability at once, and the native control already handles all three. Past dates are refused with a field-level message (FR-M3-04).

**Bonus-malus selection.** A select with the five classes (FR-M3-16), each labeled in words rather than as a code, with the plain-language meaning attached. `NEUTRAL` is the default because it is the neutral position, not because it is first alphabetically. The field carries a short note that the scale is the portal's own demo model — the copy-level half of the PRD's provenance constraint.

**Navigation after commit.** Redirect to the new policy's detail screen. The user should not have to find what they just created.

**No optimistic UI anywhere in this milestone.** Nothing is shown as done before the backend confirms it. A policy that appears and then vanishes is worse than a two-second spinner.

## Accessibility Floor

The product owner has deprioritized accessibility for this milestone: the mentor treats it as a bonus rather than a graded requirement, and remaining time goes to core logic (M3 PRD §6 Q-3). **This section is therefore a floor, not an audit** — the deferred a11y items in the PRD's G-4 group stay deferred, and no new work is planned against them.

The floor is what the existing system already gives for free, and it must not regress:

- Native semantic elements throughout, via the component library (M2 AD-3). Links are `<a>`, buttons are `<button>`, the status chip is a `<span>`.
- Every input has a real `<label>`, via `FormField`.
- Error messages render in `Alert`'s `role="alert"`.
- Focus rings come from the primitives; nothing suppresses them.
- Status is never signaled by color alone — the label carries the meaning ([DESIGN.md](./DESIGN.md) `Components`).
- Tap targets clear 44px below the `sm` breakpoint, from `Button`'s own size variants.

Nothing here costs implementation time; all of it is lost by *not* using the primitives. That is the argument for the floor even under a deprioritization.

## Bilingual Behaviour

Bulgarian default, English toggle, unchanged from Milestone 1 (FR-14/FR-15, AD-8) — and the toggle already works on every route, so new routes inherit it.

Milestone 3 adds four rules, because it is the first milestone whose content is mostly *data* rather than copy:

1. **No untranslated fallback.** Every new string ships in `bg.json` and `en.json` in the same change. New namespaces: `quotes.*` (list, detail, statuses, acceptance) and `policies.*` (list, detail, statuses), beside the existing `app` / `auth` / `quote` / `shells` / `errors`.
2. **Every new backend error code ships with both translations in the same change.** CI's error-code contract check already enforces this; the acceptance failures (expired, already accepted, not found) are the codes that matter most here.
3. **Dates render in the active language's convention** — not one hardcoded format. Coverage periods and expiry dates are the most-read data on these screens.
4. **Money renders identically in both languages:** the same numeral formatting and an explicit `EUR`. A premium must not appear to differ because the interface language changed. The currency comes from the API, never from the locale.

Switching language stays immediate and in place: no reload, no navigation, nothing typed into the acceptance form lost.

## Responsive & Platform

One layout, `max-w-2xl`, from 375px up. Inherited rules hold; three are specific to this milestone's new content:

- **Lists are single-column cards at every width.** No table, no horizontal scroller. A table of quotes is the obvious desktop pattern and the reason it is rejected is the phone.
- **The breakdown stays a two-column definition list on desktop and stacks on narrow screens** — the existing `QuoteResult` behavior, reused rather than re-solved.
- **The acceptance form is single-column at every width.** Side-by-side fields on a commitment form buy nothing and break first.

## Key Flows

### KF-1 — Elena accepts the quote she got last week *(PRD UJ-4)*

1. Elena logs in and lands on the client shell. The header now offers **My quotes**.
2. She opens My Quotes. Three cards, newest first: total premium, the vehicle, the date, a status chip. The top one reads *Expires in 6 days*; the oldest reads *Expired on 24 August*.
3. She opens the valid one. The full breakdown — base premium, age surcharge, **bonus-malus factor**, installment fee, total — exactly as she saw it at calculation time.
4. Below it: who is the policyholder, which vehicle, and when coverage starts. She fills in her name and the registration number and picks a start date.
5. She presses **Accept and issue policy**. The button holds its label, disables, and shows a spinner.
6. **Climax:** the screen becomes her policy — `MI-2026-00000001`, coverage 15 September 2026 to 15 September 2027, the premium she was quoted, and the same breakdown she just read.
7. **My policies** now has one row in it.

*Edge — double-click:* one policy, one success screen. *Edge — expired:* step 3 shows the breakdown with the acceptance section replaced by an explanation and a route to a fresh quote. *Edge — expired while open:* the server refuses, the screen re-reads and re-renders as expired, and says so in the same beat.

### KF-2 — Elena's session expired overnight *(PRD UJ-5)*

1. The tab has been open since yesterday and still shows her authenticated workspace.
2. She presses **Accept and issue policy**.
3. **Climax:** instead of a silent failure, she is returned to the login screen with her stored token cleared.
4. She logs back in, reopens the quote, and completes the acceptance. **Nothing was half-created** — she does not find a stray policy, and the quote is exactly as she left it.

*The guarantee in step 4 is the point of the flow.* The signed-out redirect is only tolerable because the transaction is atomic behind it (FR-M3-05).

### KF-3 — Elena checks what she is covered for

1. Two months later she opens **My policies**.
2. One card: policy number, vehicle, *Active until 15 September 2027*.
3. **Climax:** she opens it and finds the coverage dates and the premium breakdown unchanged from the day it was issued — the visible face of the immutable snapshot (FR-M3-07).

*This flow is why the policy detail screen exists and why it reuses the quote's breakdown presentation verbatim.* It is also Milestone 4's entry point: filing a claim starts from this screen.

---

**Conflict rule.** This spine and [DESIGN.md](./DESIGN.md) win over any mock, wireframe, or import. DESIGN.md owns how it looks; this document owns how it works.

## Open Items

- **No mocks or wireframes were rendered.** This run was explicitly lean — no creative tools, no subagents, no reviewer gate. Every surface here is spine-only. The four new surfaces are compositions of existing primitives in a single column, which is the case where a spine table carries enough; if any screen turns out to need a visual reference during implementation, the quote detail screen is the one to render first, since it carries the most stacked content.
- **`Badge` is the only new component.** If implementation finds a second one is genuinely needed, that is a design-system change and should come back through this spine rather than being added ad hoc.
