# Motor Insurance Quote & Claims Portal

[![CI](https://github.com/kk-georgiev/insurtech-quote-claims-portal/actions/workflows/ci.yml/badge.svg)](https://github.com/kk-georgiev/insurtech-quote-claims-portal/actions/workflows/ci.yml)

A portal where a client gets an instant motor insurance quote and can file and
track a claim. Built as a Sirma Academy trainee project.

## Status

**Milestone 1 — Epic 1 complete, Epic 2 in progress.** All six Epic 1
stories are done: project scaffolding, client self-registration, login with
a role-bearing JWT, the shared authentication gate, the quote engine with its
transparent premium breakdown, and quote persistence and retrieval. Epic 2 is
now building role-based access on top of that — staff accounts for the three
non-client roles are seeded (see [Demo accounts](#demo-accounts)), with
post-login routing, per-role screens, and route guards to follow. Claims
handling comes later. See [Planning & progress](#planning--progress) below
for the live per-story status.

## Tech stack

| Layer | Stack |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Maven, Flyway |
| Frontend | React 19, TypeScript 6, Vite 8, React Router 8 |
| Database | PostgreSQL 18 |
| Local dev | Docker Compose (database only, or the full stack — see below) |

## Getting started

**Prerequisites:** Docker Desktop (with WSL2 backend on Windows), JDK 21,
Maven 3.9+, Node.js 20+.

```bash
# 1. Environment variables (placeholders only, no real secrets)
cp .env.example .env

# 2. Database
docker compose up postgres

# 3. Backend (new terminal)
cd backend
mvn spring-boot:run

# 4. Frontend (new terminal)
cd frontend
npm install
npm run dev
```

Open the URL Vite prints (default `http://localhost:5173`). `/` shows the
client shell; the backend health round-trip moved to `/health` (linked in
the header nav), and `/login` / `/register` are the auth screens.

**One-command alternative:** once `.env` exists (step 1 above), `docker
compose up` builds and starts postgres, backend, and frontend together —
no local JDK/Maven/Node toolchain needed. Open
`http://localhost:5173` (or `$FRONTEND_PORT` if you changed it). This is
an alternative to steps 2-4, not a replacement for native dev during active
development. Pulled new code, or changed `.env` (e.g. `VITE_API_URL`,
`BACKEND_PORT`)? Re-run with `docker compose up --build` — a plain `up`
reuses the previously built images, including the frontend's baked-in
`VITE_API_URL`. To stop everything, `docker compose down` (add `-v` to also
drop the Postgres data volume).

### Demo accounts

Self-registration only ever creates **CLIENT** accounts, so the three staff
roles are seeded into the database by migration
`V5__seed_staff_accounts.sql`, which the backend applies on first startup.
They log in through the normal `POST /api/v1/auth/login` endpoint (or the
login screen) like any other account:

| Email | Password | Role |
|---|---|---|
| `agent@motorinsurance.demo` | `DemoPass123!` | AGENT |
| `liquidator@motorinsurance.demo` | `DemoPass123!` | LIQUIDATOR |
| `administrator@motorinsurance.demo` | `DemoPass123!` | ADMINISTRATOR |

Role-based post-login routing landed in Story 2.2: after login each role is
taken to its own route — `/agent`, `/liquidator`, `/administrator`, and the
client at `/`. The landing URL and shell are how you confirm the account
authenticated as the role you expected. Story 2.3 gave each staff route a
static, role-labeled placeholder screen ("Agent workspace" and a coming-soon
line); the client shell at `/` hosts Story 1.7's quote flow. Story 2.4 added
route guards: typing another role's URL (or visiting any of the four while
logged out) redirects instead of rendering that role's screen — a logged-in
user hitting a route that isn't their own is redirected to their *own* shell
(via `roleHome()`), not to `/login`; only an anonymous or invalid-token
visitor lands on `/login`. `/health`, `/register`, and `/login` are
intentionally left unguarded — they are not role-restricted.

To get a **CLIENT** account, register one through the app (or
`POST /api/v1/auth/register`).

### Language

The portal opens in **Bulgarian** by default, with an **English** toggle in
the header that works on every screen — public routes and guarded shells,
logged in or not. Switching is immediate and in place: no reload, no
navigation, and nothing typed into a form is lost. The choice is remembered
in the browser only (`localStorage`); there is no per-account language
preference this milestone.

Story 3.1 set up the i18n infrastructure and translated the app-wide header
chrome, **Story 3.2a** the screen copy, and **Story 3.2b** the error and
validation messaging — backend error-`code` messages, field-level validation
text, and the tariff zone label. Translation coverage for this milestone is
complete: no screen or message falls back to English. See
`frontend/README.md` for the key namespaces and the rules for adding new
copy.

> **These are demo credentials, not production credentials.** They exist so
> the four-role behaviour can be shown on a local checkout, in the same
> category as the placeholder values in `.env.example`. No migration, database
> column, or runtime config holds the plaintext — only BCrypt hashes (NFR-2);
> the plaintext appears here and in `SeededStaffAccountsTest`, which logs in
> with these very credentials so this table cannot go stale unnoticed.
>
> Any real deployment must remove these accounts:
>
> ```sql
> DELETE FROM users WHERE email LIKE '%@motorinsurance.demo';
> ```
>
> Flyway will not re-create them: `V5` is already recorded in
> `flyway_schema_history`, so it never runs again against that database.

Full details, troubleshooting, and the native-vs-Docker split are in
[`backend/README.md`](backend/README.md) and
[`frontend/README.md`](frontend/README.md).

## Project structure

```text
backend/    Spring Boot app — modular monolith, package-by-feature
frontend/   React SPA
docs/       Business analysis, UML diagrams, dev diary
_bmad-output/  Planning docs (PRD, architecture, epics) and per-story specs
docker-compose.yml   Postgres (native dev) or the full stack (see AD-9 in the architecture doc)
```

## Planning & progress

This project is planned and built with the [BMAD method](https://github.com/bmad-code-org/BMAD-METHOD):

- [Product brief / PRD](_bmad-output/planning-artifacts/prds/prd-motor-insurance-quote-claims-portal-2026-08-23/prd.md) — requirements and success criteria
- [Architecture spine](_bmad-output/planning-artifacts/architecture/architecture-motor-insurance-quote-claims-portal-2026-08-23/ARCHITECTURE-SPINE.md) — binding technical decisions (AD-1..AD-11)
- [Epics & stories](_bmad-output/planning-artifacts/epics.md) — feature breakdown
- [Sprint status](_bmad-output/implementation-artifacts/sprint-status.yaml) — live per-story progress

Background on the original assignment and business context is in
[`assignment.md`](assignment.md) and
[`docs/motor_insurance_portal_business_analysis.md`](docs/motor_insurance_portal_business_analysis.md).

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md).
