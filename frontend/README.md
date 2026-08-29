# Frontend

Vite 8 + React 19 + TypeScript 6.x SPA. Routing is owned by React Router 8
(AD-10). Routes so far: the client shell at `/`, the three staff shells
(`/agent`, `/liquidator`, `/administrator`), the auth screens (`/login`,
`/register`), and the backend health round-trip at `/health`. The three
staff shells are static, role-labeled placeholder screens (Story 2.3); the
client shell hosts Story 1.7's quote flow. Each of the four shell routes is
nested under a `RoleGuard` instance (Story 2.4): only that route's own role
can render it, a logged-in visitor with a different role is redirected to
their own shell instead, and everyone else (anonymous or invalid token) is
redirected to `/login`. `/health`, `/register`, and `/login` are
intentionally left unguarded — they are not role-restricted.

## Language (i18n)

Translation is 100% frontend-owned (AD-8) — the backend never emits
user-facing prose. `react-i18next` is initialised in `src/i18n/`, from
bundled `bg.json` / `en.json` catalogs, so the very first paint is already
in the right language with no loading state.

- **Bulgarian is the default** for a visitor with no stored preference, and
  is also the `fallbackLng`: a Bulgarian visitor must never be shown an
  English fallback string.
- The **language toggle** lives in `RootLayout`'s header, so it is reachable
  from every screen — public routes and guarded shells, logged in or not.
  Switching re-renders in place: no reload, no navigation, no lost form
  state. `<html lang>` follows the active language.
- The choice persists in `localStorage` under `motorinsurance.ui.language`,
  **client-side only** — there is no server-side or per-account language
  preference this milestone.

Story 3.1 opened the `app.*` namespace for the chrome this header owns
(title, the three nav links, the toggle's own labels). **Story 3.2a** added
the screen copy under `auth.*`, `quote.*`, `shells.*`, and `app.health.*` —
both auth forms, the quote form and breakdown, the health screen, and all
four role shells.

Still English, and owned by **Story 3.2b**: backend error-`code` messages
(the `INVALID_CREDENTIALS_MESSAGE` / `EMAIL_TAKEN_MESSAGE` /
`GENERIC_ERROR_MESSAGE` constants in the forms), field-level validation text
rendered from `ApiFieldError.message`, and the tariff zone label, which
still renders the backend's English `zoneName` instead of a `zoneId`-keyed
catalog entry.

Two rules when adding copy:

1. **A key added to one catalog is added to the other in the same change.**
   `LanguageToggle.test.tsx` fails `npm test` if the two key sets diverge.
2. **A backend error `code` and its translation ship together** (AD-7) —
   never a new code without its i18n entry.

Language option labels (`Български`, `English`) are deliberately identical
in both catalogs: each option is named in its own language so a visitor who
cannot read the current one can still find theirs.

## Prerequisites

- Node.js 20 — pinned via `.nvmrc` (macOS/Linux: `nvm use` after `cd
  frontend`) and via the `volta` field in `package.json` (`volta pin
  node@20` already applied; Volta auto-switches on `cd` once installed, no
  extra command needed). CI runs on Node 20, which is the only version the
  suite is guaranteed against. Some newer Node versions have broken
  `npm test` while typechecking and building fine: jsdom ships its own
  `AbortController`/`AbortSignal` class, distinct from Node's, and Node's
  built-in `fetch`/`Request` only recognize their own, so any test that
  triggers a real React Router `navigate()` throws a `TypeError`
  (`RequestInit: Expected signal ... to be an instance of AbortSignal`).
  This was observed on Node 24; it does **not** reproduce on Node 22.14.0,
  where the full suite passes (verified in Story 3.1). Node 20 remains the
  safe default.
  **Windows note:** `nvm`/`fnm` need Developer Mode *and* a fresh logon for
  symlink creation to work, and on some machines the "Create symbolic
  links" user right still isn't granted even then. **Volta** (`winget
  install Volta.Volta`) avoids this entirely — no symlinks, works out of
  the box — and is the recommended path on Windows.
- Docker + Docker Compose (for local Postgres) and a running backend
  (see `../backend/README.md`), so the `/health` round-trip and the login
  flow have something to reach. Not needed to run the test suite (`npm
  test`) — it mocks the network.

## Run

1. From the repo root, copy the environment template if you haven't already:

   ```bash
   cp .env.example .env
   ```

   Vite is configured (`envDir` in `vite.config.ts`) to read `.env` from the
   repo root, not `frontend/` - one `.env` serves docker-compose, the
   backend, and the frontend. Only `.env` keys prefixed `VITE_` are exposed
   to the browser bundle (`VITE_API_URL`); Postgres/JWT values are not.

2. Install dependencies and start the dev server:

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

3. Open the printed URL (default `http://localhost:5173`). It lands on the
   client shell (`/`). The `/health` screen (header nav link) calls the
   backend's `/actuator/health` endpoint at `VITE_API_URL` and shows
   "reachable" or "unreachable" - it never crashes if the backend is down.

## Run tests

```bash
cd frontend
npm test          # run the Vitest suite once
npm run test:watch # re-run on change
```

Vitest + React Testing Library + jsdom. `apiFetch` is mocked, so no backend,
Docker, or `.env` is required. Story 2.2 added this first frontend suite
(`roleHome`/`isRole` units and the login-routing / route-table tests).

## Project layout

```text
src/
  app/       # route table (router.tsx), App (router instance), RootLayout,
             #   roleHome (Role union + isRole guard + roleHome map + getCurrentRole),
             #   RoleGuard (per-role route wrapper), LanguageToggle, HealthStatus
  api/       # typed fetch wrapper (client.ts), JWT storage/decode (authToken.ts)
  i18n/      # react-i18next setup (index.ts), bg.json/en.json catalogs,
             #   language.ts (supported set, guard, localStorage persistence)
  features/
    auth/    # LoginForm, RegisterForm
    quote/   # QuoteForm, QuoteResult
    shells/  # per-role navigation shells (see above)
  test/      # Vitest setup (jsdom matchers, RTL cleanup, i18n reset), seedToken
```
