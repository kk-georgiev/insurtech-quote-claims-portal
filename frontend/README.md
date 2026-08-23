# Frontend

Vite 8 + React 19 + TypeScript 6.x SPA. Routing is owned by React Router 8
(AD-10); only a single near-empty route exists this milestone (a backend
health check), matching Story 1.1's scope.

## Prerequisites

- Node.js 20+
- Docker + Docker Compose (for local Postgres) and a running backend
  (see `../backend/README.md`), so the health round-trip has something to
  reach

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

3. Open the printed URL (default `http://localhost:5173`). The page calls
   the backend's `/actuator/health` endpoint at `VITE_API_URL` on load and
   shows "reachable" or "unreachable" - it never crashes if the backend is
   down.

## Project layout

```text
src/
  app/    # router setup (router.tsx), root layout, health check screen
  api/    # typed fetch wrapper (client.ts) - reads VITE_API_URL, never hardcoded
```

`features/` (auth, quote, role-based shells) and `i18n/` do not exist yet -
each is added in the story that first needs it.
