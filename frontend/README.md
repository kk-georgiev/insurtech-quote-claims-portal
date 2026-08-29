# Frontend

Vite 8 + React 19 + TypeScript 6.x SPA. Routing is owned by React Router 8
(AD-10). Routes so far: the client shell at `/`, the three staff shells
(`/agent`, `/liquidator`, `/administrator`), the auth screens (`/login`,
`/register`), and the backend health round-trip at `/health`. The three
staff shells are static, role-labeled placeholder screens (Story 2.3); the
client shell hosts Story 1.7's quote flow. There are no route guards yet
(Story 2.4).

## Prerequisites

- Node.js 20 (pinned via `.nvmrc` — run `nvm use` after `cd frontend`, or
  point your version manager at it directly). CI runs on Node 20. Newer
  Node (22+) typechecks/builds fine but breaks `npm test`: jsdom ships its
  own `AbortController`/`AbortSignal` class, distinct from Node's, and
  Node's built-in `fetch`/`Request` only recognize their own — any test
  that triggers a real React Router `navigate()` throws a `TypeError`
  (`RequestInit: Expected signal ... to be an instance of AbortSignal`).
  Stick to Node 20 locally to avoid it.
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
             #   roleHome (Role union + isRole guard + roleHome map), HealthStatus
  api/       # typed fetch wrapper (client.ts), JWT storage/decode (authToken.ts)
  features/
    auth/    # LoginForm, RegisterForm
    shells/  # per-role navigation shells (see above)
  test/      # Vitest setup (jsdom matchers, RTL cleanup)
```

`features/quote` and `i18n/` do not exist yet - each is added in the story
that first needs it.
