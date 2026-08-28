# Motor Insurance Quote & Claims Portal

A portal where a client gets an instant motor insurance quote and can file and
track a claim. Built as a Sirma Academy trainee project.

## Status

**Milestone 1, Epic 1 — in progress.** Story 1.1 (project scaffolding) is
done: a runnable Spring Boot backend and React frontend, wired to local
Postgres, are in place. Auth, the quote engine, and claims handling land in
the stories that follow — see [Planning & progress](#planning--progress)
below for the live status.

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
