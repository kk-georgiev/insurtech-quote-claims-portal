---
title: 'Story 3.2a: Screen Copy Translation'
type: 'feature'
created: '2026-08-29'
status: 'in-progress'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 3.1 built the i18n machinery but translated only the `RootLayout` chrome. Everything below the header is still hardcoded English: both auth forms, the quote form and its breakdown, the backend-health screen, and all four role shells. A Bulgarian visitor sees a Bulgarian header over an entirely English app.

**Approach:** Move every piece of *static* screen copy into the `bg`/`en` catalogs under the feature namespaces the architecture reserves — `auth.*`, `quote.*`, `shells.*`, plus `app.health.*` for the health screen. Purely mechanical string extraction against infrastructure that already exists and is proven. Error and validation messaging is **Story 3.2b**: it is code-driven, carries the architectural decisions, and is deliberately reviewed separately from ~38 rote string swaps.

## Boundaries & Constraints

**Always:**
- Reuse Story 3.1's infrastructure unchanged: no new dependency, no second i18next instance, no change to `i18n/index.ts`, `i18n/language.ts`, or `LanguageToggle`.
- Namespace by feature, mirroring the folder tree: `auth.*` (LoginForm, RegisterForm), `quote.*` (QuoteForm, QuoteResult), `shells.*` (all four), `app.health.*` (HealthStatus). Extend the existing `app.*` block; never open a fifth top-level namespace.
- Both catalogs stay key-for-key identical — the existing catalog-parity test enforces this.
- Translate accessible names too, not just visible text: `aria-label`, and any `<label>`/heading a test or screen reader resolves by name.
- **Pre-existing test suites will break, and that is expected.** Every suite currently queries by English text (`getByRole('heading', { name: 'Log in' })`, `getByLabelText('Email')`). Update those queries to read from the catalog — the pattern `LanguageToggle.test.tsx` already established with `bg.app.title` — never by hardcoding the new Bulgarian string, and never by weakening a query to `data-testid` to dodge the problem.
- Every `data-testid` stays exactly as it is. They are the stable hooks; copy is what changes.

**Ask First:**
- Any string that cannot be translated without touching the backend or an error path — that belongs to Story 3.2b, not here.
- Any wording change beyond translation. If the English copy is wrong or unclear, say so; do not silently improve it while translating.

**Never:**
- No backend change of any kind (AD-8/FR-15).
- No error-message work: the `INVALID_CREDENTIALS_MESSAGE` / `EMAIL_TAKEN_MESSAGE` / `GENERIC_ERROR_MESSAGE` constants, the backend `code`→message map, `fieldErrors` rendering, and the `zoneName` label are all Story 3.2b.
- No money or date reformatting. Amounts render exactly as the API returns them (`1234.56 BGN`), in both languages — an explicit Story 1.6/1.7 constraint that this story does not renegotiate.
- No layout, styling, routing, or component-structure change. Copy only.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Bulgarian pass | `bg` active, visit every screen | no English static copy visible anywhere outside error paths | N/A |
| English pass | `en` active, visit every screen | copy identical to today's wording | N/A |
| Live switch | quote form half-filled, toggle language | all labels switch in place; typed values and route survive | N/A |
| Staff shells | logged in as each staff role | that role's heading and coming-soon line are translated and still role-distinct | N/A |
| Submitting state | form mid-submit in `bg` | the busy label ("Влизане…") is translated, not just the idle one | N/A |
| Accessible names | screen reader in `bg` | `aria-label` and label-derived names are Bulgarian, matching visible text | N/A |

</frozen-after-approval>

## Code Map

Exact string inventory — ~38 keys. Every line number is from `dev` at the time of writing; re-verify after Story 3.1 merges.

- `frontend/src/i18n/bg.json`, `en.json` -- add the `auth`, `quote`, `shells` blocks and `app.health`. Keep the existing `app.title`/`app.nav`/`app.language` keys untouched.
- `frontend/src/features/auth/LoginForm.tsx:123,126,146,171` -- `auth.login.*`: heading "Log in", labels "Email"/"Password", button "Log in" + busy "Logging in…". Leave lines 21-22 (the two message constants) alone — 3.2b.
- `frontend/src/features/auth/RegisterForm.tsx:97,109,112,132,159` -- `auth.register.*`: headings "Create an account" and "Registration successful", labels, button "Register" + busy "Creating account…". Leave lines 18-19 — 3.2b.
- `frontend/src/features/quote/QuoteForm.tsx:141,144,167,186,209,235` -- `quote.form.*`: heading "Get a quote", four field labels ("Driver age", "Region code", "Engine size (cc)", "Number of installments"), button + busy state. Leave line 45 — 3.2b.
- `frontend/src/features/quote/QuoteResult.tsx:15,17` + the eight `<dt>` labels -- `quote.result.*`: `aria-label="Quote breakdown"`, heading "Your quote", and Zone / Base premium / Age surcharge / One-time premium / Installments / Installment fee / Total premium / Installment amount. **Leave `{quote.zoneName}` as-is** — 3.2b replaces it with a `zoneId`-keyed label.
- `frontend/src/app/HealthStatus.tsx:46-50` -- `app.health.*`: "Backend status", "Checking backend...", "Backend is reachable.", and the unreachable sentence. The unreachable branch currently interpolates `reason`, which is `ApiRequestError.message` — raw dev-facing prose that AD-7 says must never reach a user. Drop the interpolation and render a plain translated sentence; that is removal of untranslated content, not error-handling work.
- `frontend/src/features/shells/{agent,liquidator,administrator}/*Shell.tsx:15-16` -- `shells.<role>.*`: heading and coming-soon line for each. Keep them three separate files and three distinct key sets — they must stay visibly different per role (Story 2.3's AC).
- `frontend/src/features/shells/client/ClientShell.tsx` -- `shells.client.heading` ("Client").
- **Test files needing query updates:** `features/auth/LoginForm.test.tsx`, `features/auth/RegisterForm.test.tsx`, `features/quote/QuoteForm.test.tsx`, `features/shells/shells.test.tsx`, `app/router.test.tsx`, `app/LanguageToggle.test.tsx`. `router.test.tsx` asserts `'Get a quote'` and `'Log in'`; `LanguageToggle.test.tsx` asserts `'Log in'` and `'Email'` in its state-preservation and redirect cases.
- **Read-only reference:** `app/LanguageToggle.test.tsx` — the `import bg from '../i18n/bg.json'` + `bg.app.title` query pattern every updated suite should copy.

## Tasks & Acceptance

**Execution:**
- [ ] `frontend/src/i18n/bg.json`, `en.json` -- add all `auth`/`quote`/`shells`/`app.health` keys in one pass -- every task below depends on the keys existing.
- [ ] `frontend/src/features/auth/LoginForm.tsx`, `RegisterForm.tsx` -- swap static copy to `t('auth.*')` -- the two most-visited screens.
- [ ] `frontend/src/features/quote/QuoteForm.tsx`, `QuoteResult.tsx` -- swap to `t('quote.*')`; leave `zoneName` and the message constant -- the client's core flow.
- [ ] `frontend/src/app/HealthStatus.tsx` -- swap to `t('app.health.*')`; drop the raw `reason` interpolation -- removes dev prose from the UI.
- [ ] `frontend/src/features/shells/**/*Shell.tsx` -- swap to `t('shells.*')`, one key set per role -- keeps roles visibly distinct.
- [ ] `frontend/src/**/*.test.tsx` (6 suites) -- re-point English text queries at catalog imports -- the suites must assert the same behaviour, not weaker behaviour.
- [ ] `frontend/src/features/quote/QuoteForm.test.tsx` or a sibling -- add a Bulgarian-pass test asserting a rendered screen contains no leftover English static copy -- pins the AC that motivates this story.
- [ ] `README.md`, `frontend/README.md` -- update the "Story 3.2 owns those" note to reflect what is now translated and what 3.2b still owns.

**Acceptance Criteria:**
- Given Bulgarian is active, when I visit login, registration, the quote form and breakdown, the health screen, and all four shells, then no English static copy is visible on any of them.
- Given English is active, when I visit the same screens, then the copy reads exactly as it does today.
- Given a screen reader in Bulgarian, when it announces headings, form labels, and the quote-breakdown region, then those names are Bulgarian.
- Given `npm run typecheck`, `npm run build`, and `npm test` in `frontend/`, when run, then all pass — with every pre-existing behavioural assertion preserved, only its query re-pointed at the catalog.

## Spec Change Log

## Verification

**Commands:**
- `cd frontend; npm run typecheck; npm run build; npm test` -- expected: all clean; no suite loses a case.
- `cd frontend; grep -rnE ">[A-Z][a-z]{3,}" src/features src/app --include=*.tsx | grep -v test` -- expected: no hits outside error paths and `zoneName`; a fast net for missed literals.

**Manual checks:**
- `npm run dev`: walk every screen in Bulgarian, then toggle to English on each and confirm the wording matches today's. Register an account and reach the success screen; submit a valid quote and read the whole breakdown in both languages.
