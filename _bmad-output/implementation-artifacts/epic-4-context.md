# Epic 4 Context: The Team Demos From a Clean Machine

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Anyone should be able to clone the repository onto a machine that has never run the project, run a single documented command, and reach a fully working system covering everything built in Epics 1–3 (auth, roles, quote flow, i18n) — with no manual database setup or config editing beyond copying `.env.example`. This exists specifically so the mentor demo never depends on any one person's machine state. Alongside this, the fast native dev loop (backend/frontend running directly against a containerized database only) must keep working exactly as it did from Story 1.1, so day-to-day iteration isn't sacrificed for demo convenience.

## Stories

- Story 4.1: Backend and Frontend Dockerfiles
- Story 4.2: One-Command Full-Stack Startup
- Story 4.3: Local Dev Workflow Preserved Alongside Docker

## Requirements & Constraints

- From a clean checkout with only Docker installed (plus `.env` copied from `.env.example`), one documented command must bring up the database, backend, and frontend, fully wired to each other, with no manual database creation, migration step, or config edit beyond that.
- The full Docker stack should become usable within roughly a couple of minutes on a typical dev laptop on first run (image pulls aside), and near-instantly on subsequent runs. This target is an assumption, not a hard spec.
- The native dev workflow (backend and frontend run directly, database containerized only) must remain independently runnable with no undocumented manual step, documented in `backend/README.md` and `frontend/README.md`.
- No password, token secret, or credential may appear in source control, logs, or committed config; `.env`-style files only, with `.env.example` documenting every required variable with no real values.
- Success is measured by a dry run of the one-command startup on a machine that has never run the project, before the mentor meeting.

## Technical Decisions

- Exactly one `docker-compose.yml` at the repo root defines three services: `postgres`, `backend`, `frontend`. `docker compose up` starts the full stack (closes Story 4.2). `docker compose up postgres` starts only the database, for native dev (Story 4.3) — no divergent per-environment compose files.
- Each of `backend/` and `frontend/` gets its own `Dockerfile` (Story 4.1). The backend image starts the Spring Boot app and connects to Postgres over the Compose network. The frontend image serves the built SPA.
- The frontend's API base URL is never hardcoded — it's injected via `VITE_API_URL` at build/runtime, in both the containerized and native-dev modes, set to the browser-reachable backend origin. This matters because the browser sits outside the Compose network and cannot resolve a Docker service name like `backend`; the containerized frontend must reach the backend via its host-mapped port, exactly as native dev does. One env var, one convention, used identically by both AD-9 and AD-10.
- Container runtime is Docker + Docker Compose; stack versions otherwise follow Epic 1–3's pins (Java 21/Spring Boot 4.1.1/Maven backend, React 19/TypeScript 6.x/Vite 8 frontend, PostgreSQL 18).
- Secrets (JWT signing key, DB credentials, etc.) are read from environment variables documented in `.env.example` with no real values — never hardcoded or committed, including inside the Docker images or compose file.

## Cross-Story Dependencies

- Story 4.2 depends on Story 4.1's Dockerfiles existing and building correctly.
- Story 4.3 depends on Story 1.1's established native-dev connection pattern (backend/frontend against a containerized-only Postgres) still holding — it documents and verifies that pattern rather than introducing a new one.
- The full stack this epic brings up is Epics 1–3's functionality (auth/roles, quote flow, i18n); this epic adds no new user-facing features, only the deployment path.
