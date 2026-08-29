---
title: 'Story 3.1: i18n Infrastructure and Language Toggle'
type: 'feature'
created: '2026-08-29'
status: 'in-review'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** There is no translation layer. `react-i18next` is pinned by AD-8 and `frontend/src/i18n/` is named in the architecture source tree, but neither exists: every string is a hardcoded English literal, `index.html` says `lang="en"`, and a user cannot pick a language. Bulgarian is supposed to be the default (FR-14).

**Approach:** Stand up the foundation the whole epic builds on — `react-i18next` initialised from `bg.json`/`en.json` with Bulgarian default and fallback, client-side-persisted selection, and one toggle control in `RootLayout` so it is reachable from every screen, logged in or not. Migrate only the app chrome (title, nav links, toggle labels) to catalog keys — enough to prove the mechanism. Story 3.2 migrates the remaining screens and the backend error-code mapping.

## Boundaries & Constraints

**Always:**
- **Scope decision (human-approved 2026-08-29 — do not re-litigate).** This story's translated surface is exactly the `RootLayout`-owned app chrome: the page title, the three nav links, and the toggle's own labels. `epics.md`'s Story 3.1 AC was reconciled the same day to say so. Read "all currently-visible text switches immediately" as a statement about the *switching mechanism* — immediate, in place, no reload, no navigation, no lost route state — applied *within that surface*. It is not a coverage requirement. Every feature screen, form, result, validation message, API-error-`code` mapping, and shell body belongs to Story 3.2. Story 3.1 is evaluated only against the translated surface defined above; complete feature-screen and message coverage remains Story 3.2.
- **Persistence decision (human-approved 2026-08-29).** A small hand-written `localStorage` helper in `i18n/language.ts`. `i18next-browser-languagedetector` is not to be added.
- **`RootLayout` decision (human-approved 2026-08-29).** Changes there are limited to i18n and the toggle — no logout, no auth-aware navigation, no header restructuring. Epic 2 retro item 5 owns that surface.
- `react-i18next` (AD-8), one catalog file per language under `frontend/src/i18n/` — never inline string maps in components.
- `bg` is both the no-preference default and the `fallbackLng`; a Bulgarian visitor must never see an English-fallback artifact.
- Keys namespaced by area. This story owns `app.*` only; `auth.*`, `quote.*`, `shells.*` are reserved for Story 3.2. Both catalogs stay key-for-key identical.
- Persist client-side only, in `localStorage`, using the existing `motorinsurance.*` key convention from `api/authToken.ts`. Wrap read *and* write in `try/catch` — i18n runs on first paint, so a throw in private-browsing mode would white-screen the app.
- Toggling re-renders in place: no reload, no `navigate()`, no route change, no lost form state or scroll position.
- `document.documentElement.lang` tracks the active language; `index.html` ships the `bg` default.
- Language state comes from `react-i18next`'s context — no second parallel store, no prop drilling.

**Ask First:**
- Any apparent need to translate copy outside the approved surface, or to edit a feature screen, in order to satisfy an AC — that means the boundary above is wrong. Stop and ask; do not widen it silently.
- Any i18n dependency beyond `i18next` and `react-i18next`.
- Any backend error `code` the chrome turns out to need mapped — none is expected in this story. If one appears, stop.

**Never:**
- No backend change of any kind (AD-8/FR-15) — no `Accept-Language`, no message bundles, no new error codes.
- No translation of `LoginForm`, `RegisterForm`, `QuoteForm`, `QuoteResult`, `HealthStatus`, or the four shells; no `code`→message mapping. All Story 3.2.
- No third language; no server-side or per-account preference.
- No routing change — language is not a URL segment or query param, and the route table is untouched.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| First visit | empty storage, no token | renders Bulgarian; `<html lang="bg">` | N/A |
| Toggle to EN | `bg` active, EN control activated | chrome switches immediately, same route, no reload; `<html lang="en">` | N/A |
| Toggle back | `en` active, BG control activated | switches back immediately | N/A |
| Reload after toggle | `en` stored, reload | renders English on first paint | N/A |
| Corrupt stored value | stored `de` / `""` / non-string | treated as absent → Bulgarian | ignore silently |
| Storage unavailable | read or write throws | app still renders and toggles for the session | swallow, use Bulgarian |
| Unmapped key | component requests a missing key | `bg` fallback behaviour, never a raw English literal as UI copy | dev-visible only |

</frozen-after-approval>

## Code Map

- `frontend/package.json:22-26` -- `dependencies` currently only `react`, `react-dom`, `react-router`; add `i18next` + `react-i18next`.
- `frontend/package-lock.json` -- tracked, not gitignored. `npm install` regenerates it; both it and `package.json` are expected changed files and both get committed together. Never hand-edit it.
- `frontend/src/i18n/language.ts` -- **new**. `SUPPORTED_LANGUAGES`, `Language`, `DEFAULT_LANGUAGE`, `isLanguage()`, `getStoredLanguage()`, `saveLanguage()`. Mirror `app/roleHome.ts` (`ROLES`/`isRole` closed-set shape) and `api/authToken.ts` (storage key style).
- `frontend/src/i18n/bg.json`, `en.json` -- **new**. `app.title`, `app.nav.{register,login,health}`, `app.language.{label,bg,en}`.
- `frontend/src/i18n/index.ts` -- **new**. Side-effect init: both catalogs, `lng: getStoredLanguage() ?? 'bg'`, `fallbackLng: 'bg'`, `interpolation.escapeValue: false`.
- `frontend/src/app/LanguageToggle.tsx` -- **new**. `useTranslation()`; `changeLanguage()` + `saveLanguage()` + `documentElement.lang`. Needs an accessible name and an active-state indication (`aria-pressed`/`aria-current`) — a11y precedent set by `3b758a5` in `RegisterForm.tsx`/`QuoteForm.tsx`.
- `frontend/src/app/RootLayout.tsx:14-27` -- swap the 4 hardcoded strings (`h1`, `Register`/`Login`/`Health`) for `t('app.*')`; mount `<LanguageToggle />` in `<header>`. Leave `<Link>` targets, `<nav>`/`<main>`, `<Outlet />` alone.
- `frontend/src/main.tsx:1-4` -- `import './i18n';` before render.
- `frontend/index.html:2` -- `lang="en"` → `lang="bg"`.
- `frontend/src/test/setup.ts:9-16` -- add `import '../i18n';`; the existing `afterEach` `localStorage.clear()` already resets language between tests.
- **Read-only evidence:** `api/client.ts:38-45` already surfaces the AD-7 envelope's `code`/`fieldErrors`, so Story 3.2 needs no client plumbing and this story adds none. `features/shells/shells.test.tsx` asserts English shell copy — those shells are untranslated here, so that suite must stay green with zero edits.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/package.json`, `frontend/package-lock.json` -- add `i18next` + `react-i18next` to `dependencies`, then run `npm install` so npm regenerates the lockfile; commit both -- AD-8's pinned library, currently absent. The lockfile is npm-generated; never hand-edit it.
- [x] `frontend/src/i18n/language.ts` -- supported set, guard, safe storage get/set -- one owner for "which languages" and "where the choice lives".
- [x] `frontend/src/i18n/bg.json`, `en.json` -- the `app.*` chrome keys, identical key sets -- the catalogs 3.2 extends.
- [x] `frontend/src/i18n/index.ts` -- init from the above -- depends on both prior tasks.
- [x] `frontend/src/main.tsx`, `frontend/index.html` -- import i18n before render; flip static `lang` -- wires init into the real app.
- [x] `frontend/src/app/LanguageToggle.tsx` -- accessible control; changes, persists, syncs `<html lang>` -- the user-facing half of the AC.
- [x] `frontend/src/app/RootLayout.tsx` -- chrome copy via `t()`; mount the toggle -- makes it reachable everywhere.
- [x] `frontend/src/test/setup.ts` -- import i18n in global setup -- component suites need an initialised instance.
- [x] `frontend/src/i18n/language.test.ts` -- **new**; covers the corrupt-value, missing-value, and storage-throws matrix rows.
- [x] `frontend/src/app/LanguageToggle.test.tsx` -- **new**; covers default-Bulgarian, immediate in-place switch, persistence across remount, `<html lang>` sync.
- [x] `README.md`, `frontend/README.md` -- document the default, the toggle, and the both-catalogs rule -- these already carry the per-story caveat convention.

**Acceptance Criteria:**
- Given a first-time visitor with no stored preference, when the app loads, then the chrome renders in Bulgarian and `<html lang>` is `bg`.
- Given the toggle is used, when the language changes, then chrome text updates in place on the same route, with no reload and no navigation.
- Given the toggle is reached from any route — including a guarded shell and while logged out — when rendered, then it is present and operable.
- Given `npm run typecheck`, `npm run build`, and `npm test` in `frontend/`, when run, then all pass and every pre-existing suite stays green with no assertion changes.

## Spec Change Log

## Verification

**Commands:**
- `cd frontend; npm install` -- expected: both packages resolve, lockfile updated.
- `cd frontend; npm run typecheck; npm run build; npm test` -- expected: all clean; new suites pass, pre-existing suites pass unchanged.

**Manual checks:**
- `npm run dev` in a fresh browser profile -- Bulgarian header, `<html lang="bg">`. Toggle to English -- switches with no reload, URL unchanged. Reload -- still English. Log in as a staff role -- the toggle still works on the guarded shell.
- Known local caveat: on Node 24 this machine cannot run tests that trigger a real `<Navigate>` (pre-existing jsdom/undici `AbortSignal` issue, in `deferred-work.md` under "Story 2.4 verification"). Nothing here navigates, so the new suites are unaffected; CI on Node 20 runs everything.
