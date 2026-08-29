---
title: 'Story 4.2: One-Command Full-Stack Startup'
type: 'feature'
created: '2026-08-29'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '736a7d0f85d188a9bded6b824ecc50583b9a06db'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 4.1 built `backend/Dockerfile` and `frontend/Dockerfile`, but `docker-compose.yml` still only defines `postgres` — there is no single command that brings up the whole stack from a clean checkout.

**Approach:** Add `backend` and `frontend` services to `docker-compose.yml`, built from their Story 4.1 Dockerfiles, wired to `postgres` and to each other purely through environment variables (no hardcoded addresses), with startup ordering so the backend never races an unready database.

## Boundaries & Constraints

**Always:** `backend` connects to `postgres` via the Docker-internal service name (`postgres:5432`) — that traffic never leaves the Compose network. `backend` waits on `postgres`'s existing healthcheck (`depends_on: condition: service_healthy`) before starting. `frontend`'s build receives `VITE_API_URL` as a build arg sourced from `.env`, and that value must be the **browser-reachable, host-mapped** backend origin (e.g. `http://localhost:8080`) — never the `backend` service name, since the browser sits outside the Compose network (AD-9). Both new services expose their ports to the host via configurable, defaulted variables so `docker compose up` works immediately after `cp .env.example .env` with no edits. `docker compose up postgres` must keep starting only Postgres, unchanged from Story 1.1.

**Ask First:** None anticipated.

**Never:** Do not modify the contents of `backend/Dockerfile` or `frontend/Dockerfile` (Story 4.1, frozen) — only reference them from `docker-compose.yml`. Do not add a reverse proxy, TLS, or any other production-hardening layer. Do not touch `.github/workflows/ci.yml`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Clean checkout, one command | `cp .env.example .env && docker compose up` | postgres, backend, frontend all start; Flyway migrations apply; the app is reachable end-to-end at the frontend's exposed host port | N/A |
| Partial start still works | `docker compose up postgres` (alone) | Only postgres starts — identical to Story 1.1's behavior | N/A |
| Browser calls the backend | Frontend page loaded from its exposed host port | Bundled JS calls the host-mapped `VITE_API_URL`, not a Compose service name | N/A |
| Backend starts before Postgres is ready | `docker compose up` on a fresh volume | Backend container waits for `postgres`'s healthcheck before starting, instead of crash-looping | N/A |

</frozen-after-approval>

## Code Map

- `docker-compose.yml` — currently `postgres`-only, with a working healthcheck (`pg_isready`); its own header comment already states backend/frontend services land in Epic 4.
- `backend/Dockerfile` (Story 4.1) — `EXPOSE 8080`; reads `POSTGRES_HOST/PORT/DB/USER/PASSWORD` and `JWT_SECRET` at runtime, not build time.
- `backend/src/main/resources/application.yml` — confirms those exact env var names, plus `server.port: 8080` and `/actuator/health`.
- `frontend/Dockerfile` (Story 4.1) — `EXPOSE 80` (nginx); `ARG`/`ENV VITE_API_URL` baked in at build time, not runtime.
- `.env.example` — currently documents `POSTGRES_DB/USER/PASSWORD/PORT/HOST`, `JWT_SECRET`, `VITE_API_URL=http://localhost:8080`; no backend/frontend host-port variables exist yet.
- `README.md` — "Getting started" documents only the native/partial-start path (`docker compose up postgres` + `mvn spring-boot:run` + `npm run dev`); no one-command path is documented yet.

## Tasks & Acceptance

**Execution:**
- [x] `docker-compose.yml` -- add a `backend` service: `build: ./backend`; env vars for Postgres (`POSTGRES_HOST=postgres`, internal port/db/user/password from `.env`) and `JWT_SECRET`; `depends_on: postgres: condition: service_healthy`; a healthcheck hitting `/actuator/health`; host port via a new `${BACKEND_PORT:-8080}` variable -- brings the backend into the one-command stack (FR-12)
- [x] `docker-compose.yml` -- add a `frontend` service: `build: { context: ./frontend, args: { VITE_API_URL } }`; `depends_on: backend`; host port via a new `${FRONTEND_PORT:-5173}` variable -- completes the stack; `VITE_API_URL` must resolve to the host-mapped backend origin, never the `backend` service name
- [x] `.env.example` -- document `BACKEND_PORT`/`FRONTEND_PORT` with safe defaults consistent with the existing `VITE_API_URL` default -- keeps `cp .env.example .env` sufficient for a working `docker compose up` out of the box
- [x] `README.md` -- add a short note under "Getting started" pointing at `docker compose up` as the one-command full-stack path, alongside (not replacing) the existing native-dev steps -- FR-12 requires this command to be documented

**Acceptance Criteria:**
- Given a clean checkout with `.env` copied from `.env.example`, when `docker compose up` runs, then postgres/backend/frontend all start, Flyway migrations apply, and the app is reachable end-to-end (register, log in, get a quote) at the frontend's exposed host port.
- Given that same command, when the browser-served frontend calls the backend, then it does so via the host-mapped `VITE_API_URL`, not a Docker-internal service name.
- Given `docker compose up postgres` run alone, when observed, then only postgres starts, exactly as before this story.
- Given `backend/Dockerfile` and `frontend/Dockerfile`, when this story is done, then their contents are unmodified.

## Spec Change Log

## Verification

**Commands:**
- `cp .env.example .env && docker compose up --build` -- expected: all three services start, no crash loops
- `curl http://localhost:8080/actuator/health` -- expected: `{"status":"UP"}`
- `curl -o /dev/null -s -w "%{http_code}" http://localhost:5173/` -- expected: `200`
- `docker compose down && docker compose up postgres` -- expected: only the postgres container starts

**Manual checks (if no CLI):**
- Open the frontend's exposed host port in a browser; register a CLIENT, log in, submit a quote, and confirm the breakdown renders — validates Epics 1-3's flows end-to-end through the containerized stack.

## Suggested Review Order

**Backend service wiring**

- Entry point: the `backend` service definition — build context, Postgres env wiring, and health-gated startup ordering.
  [`docker-compose.yml:37`](../../docker-compose.yml#L37)

- Health-gate on Postgres: why `backend` waits for `service_healthy` instead of racing the database.
  [`docker-compose.yml:49`](../../docker-compose.yml#L49)

**Frontend service wiring**

- Build-time `VITE_API_URL` injection — must be the host-mapped origin, never the `backend` service name.
  [`docker-compose.yml:64`](../../docker-compose.yml#L64)

**Configuration surface**

- New `BACKEND_PORT`/`FRONTEND_PORT` defaults and the sync caveat with `VITE_API_URL`.
  [`.env.example:31`](../../.env.example#L31)

**Peripherals**

- One-command path documented alongside (not replacing) native dev steps.
  [`README.md:55`](../../README.md#L55)
