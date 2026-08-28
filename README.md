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
| Local dev | Docker Compose (database only — see below) |

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

Open the URL Vite prints (default `http://localhost:5173`) — the page calls
the backend's health endpoint and shows whether it's reachable.

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

Role-based routing lands in Stories 2.2–2.4, so for now every role is taken
to the same post-login screen, which prints the role decoded from the token
("You are logged in as AGENT") — that line is how you confirm the account
authenticated as the role you expected.

To get a **CLIENT** account, register one through the app (or
`POST /api/v1/auth/register`).

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
docker-compose.yml   Local Postgres for native dev (see AD-9 in the architecture doc)
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
