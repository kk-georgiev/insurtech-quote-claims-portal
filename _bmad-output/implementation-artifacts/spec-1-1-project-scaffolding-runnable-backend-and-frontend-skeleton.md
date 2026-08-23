---
title: 'Story 1.1: Project Scaffolding — Runnable Backend and Frontend Skeleton'
type: 'feature'
created: '2026-08-23'
status: 'done'
review_loop_iteration: 0
baseline_commit: '24fe2bd22c0d9ecf614d6a4b27145630d454b9fe'
context: ['{project-root}/_bmad-output/planning-artifacts/architecture/architecture-motor-insurance-quote-claims-portal-2026-08-23/ARCHITECTURE-SPINE.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The repo has only documentation — no backend or frontend code exists yet, so no later story (registration, login, quoting) has any foundation to build on.

**Approach:** Scaffold a minimal, runnable Spring Boot backend and React frontend wired to local Postgres, matching the Architecture Spine's Structural Seed exactly, with only the `shared` module populated — no business logic yet.

## Boundaries & Constraints

**Always:**
- Repo layout matches the Structural Seed exactly: `backend/src/main/java/com/motorinsurance/shared/...`, `backend/src/main/resources/db/migration/`, `frontend/src/{app,features,i18n,api}/`, root `docker-compose.yml` and `.env.example`.
- Only the `shared` backend package exists (AD-1/AD-6): `ApiError` DTO + centralized `@RestControllerAdvice` exception handler skeleton (AD-7 shape), base Spring config. No `auth`/`quote`/`pricing` packages, entities, or endpoints yet.
- Flyway's initial baseline migration runs successfully against a clean DB (no tables yet — nothing owns a table until its module exists).
- A trivial health round-trip proves the wiring: frontend dev server calls a backend health endpoint (Spring Boot Actuator default is sufficient) via `VITE_API_URL` and shows reachable/unreachable state.
- `.env.example` documents every required variable (Postgres creds, JWT secret placeholder per AD-11, `VITE_API_URL`) with no real values (NFR-2).
- Stack pins exactly: Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 18, React 19, TypeScript 6.x, Vite 8, React Router 8 (router installed and owns routing per AD-10, even though routes are near-empty this story).
- Frontend API base URL is always read from `VITE_API_URL`, never hardcoded (AD-9/AD-10).

**Ask First:** Any deviation from the Structural Seed's exact directory layout or the pinned stack versions above.

**Never:**
- No `auth`/`quote`/`pricing` module code, entities, or business logic (AD-6 — created only in the story that needs them).
- No JWT/security filter or protected endpoints (Story 1.3/1.4's job).
- No `backend/Dockerfile`, `frontend/Dockerfile`, or backend/frontend services in `docker-compose.yml` — full containerized stack is Epic 4 (Stories 4.1/4.2); this story's compose file defines the `postgres` service only.
- No data-fetching library beyond a thin typed `fetch` wrapper (AD-10 explicitly excludes React Query this milestone).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Clean start | `docker compose up postgres`, then `mvn spring-boot:run` | Backend connects to Postgres; Flyway baseline migration succeeds | Backend logs a clear error and fails fast if DB unreachable |
| Health round-trip | `npm run dev`, open app | Frontend calls backend health endpoint via `VITE_API_URL`, renders "reachable" | Renders "unreachable" state if the call fails, no crash |

</frozen-after-approval>

## Code Map

- `docker-compose.yml` -- root compose; `postgres:18` service only this story (AD-9, scoped — backend/frontend services deferred to Epic 4)
- `.env.example` -- documents `POSTGRES_DB/USER/PASSWORD`, `JWT_SECRET` placeholder (AD-11), `VITE_API_URL` (NFR-2)
- `backend/pom.xml` -- new Maven project, Spring Boot 4.1.1 parent, Java 21; deps: web, data-jpa, postgresql, flyway-core, actuator, validation
- `backend/src/main/resources/application.yml` -- DB connection + Flyway from env vars, Actuator health exposed
- `backend/src/main/resources/db/migration/V1__baseline.sql` -- empty baseline migration (no tables yet)
- `backend/src/main/java/com/motorinsurance/shared/api/ApiError.java` + `shared/api/GlobalExceptionHandler.java` -- AD-7 envelope skeleton
- `backend/README.md` -- native run instructions (`mvn spring-boot:run` against `docker compose up postgres`)
- `frontend/` -- new Vite 8 + React 19 + TypeScript 6.x project, React Router 8 installed
- `frontend/src/api/client.ts` -- typed `fetch` wrapper reading `import.meta.env.VITE_API_URL` (AD-10)
- `frontend/src/app/` -- root layout + router setup; calls backend health endpoint on load, renders reachable/unreachable
- `frontend/README.md` -- native run instructions (`npm run dev` against `docker compose up postgres`)

## Tasks & Acceptance

**Execution:**
- [x] `docker-compose.yml` -- add `postgres:18` service with env-driven creds + named volume -- enables `docker compose up postgres`
- [x] `.env.example` -- document all required variables, no real values -- NFR-2/AD-11
- [x] `backend/pom.xml` + project skeleton -- init Maven project pinned to Java 21 / Spring Boot 4.1.1 -- foundation for backend
- [x] `backend/src/main/resources/application.yml` -- wire DB connection + Flyway, expose Actuator health -- health round-trip
- [x] `backend/src/main/resources/db/migration/V1__baseline.sql` -- add empty baseline migration -- proves Flyway succeeds on clean DB
- [x] `backend/src/main/java/com/motorinsurance/shared/api/*` -- add `ApiError` + `GlobalExceptionHandler` skeleton -- seeds AD-7 structurally
- [x] `backend/README.md` -- document native run steps -- pairs with frontend README
- [x] `frontend/` -- scaffold Vite 8 + React 19 + TS 6.x + React Router 8 -- matches Structural Seed
- [x] `frontend/src/api/client.ts` -- add typed fetch wrapper using `VITE_API_URL` -- AD-9/AD-10
- [x] `frontend/src/app/` -- add root layout calling health endpoint on load -- makes AC1 visibly verifiable
- [x] `frontend/README.md` -- document native run steps -- pairs with backend README

**Acceptance Criteria:**
- Given a clean checkout, when I run `docker compose up postgres` and start backend/frontend natively, then the backend connects to Postgres and the frontend dev server reaches the backend via a trivial health round-trip.
- Given a clean database, when the backend starts, then Flyway's initial baseline migration succeeds.
- Given the Structural Seed, when the repo is scaffolded, then only the `shared` backend module package exists.
- Given `.env.example`, when inspected, then every required variable is documented with no real values.

## Spec Change Log

## Design Notes

The Structural Seed shows `Dockerfile` under `backend/` and `frontend/` and a 3-service `docker-compose.yml` as the milestone's *end state* — but epics.md splits that out explicitly: Story 4.1 adds the Dockerfiles, Story 4.2 wires the full `docker compose up`. This story only needs `docker compose up postgres` to work (per its own AC), so building the Dockerfiles now would be scope creep duplicated later. Keep `docker-compose.yml` to a single `postgres` service.

## Verification

**Commands:**
- `docker compose up postgres` -- expected: Postgres container starts and becomes healthy
- `cd backend && mvn spring-boot:run` -- expected: starts cleanly, logs successful Flyway migration, connects to Postgres
- `cd frontend && npm run dev` -- expected: Vite dev server starts; app shows backend "reachable"
- `curl http://localhost:8080/actuator/health` -- expected: `{"status":"UP"}`

## Suggested Review Order

**Backend bootstrap & module skeleton**

- Entry point for the single deployable process; not a module itself (AD-1/AD-6).
  [`MotorInsuranceApplication.java:13`](../../backend/src/main/java/com/motorinsurance/MotorInsuranceApplication.java#L13)

- Fixed AD-7 envelope shape (`timestamp, status, code, message, fieldErrors`) every error response uses.
  [`ApiError.java:19`](../../backend/src/main/java/com/motorinsurance/shared/api/ApiError.java#L19)

- Centralized exception handler seeding AD-7; catch-all now logs server-side instead of leaking `ex.getMessage()` to callers (review-loop patch).
  [`GlobalExceptionHandler.java:67`](../../backend/src/main/java/com/motorinsurance/shared/api/GlobalExceptionHandler.java#L67)

**CORS: single source of truth (review-loop patch)**

- Dev origin allow-list defined once, shared by both the regular-endpoint config and Actuator's separate CORS handling.
  [`application.yml:7`](../../backend/src/main/resources/application.yml#L7)

- Regular `@RestController` endpoints read the same property via `@Value` instead of a hardcoded duplicate list.
  [`CorsConfig.java:28`](../../backend/src/main/java/com/motorinsurance/shared/config/CorsConfig.java#L28)

- Actuator's separately-dispatched CORS config now points at the same property key, eliminating drift risk.
  [`application.yml:42`](../../backend/src/main/resources/application.yml#L42)

**Frontend health round-trip**

- Root route wiring; router owns all routing per AD-10, near-empty this story.
  [`router.tsx:9`](../../frontend/src/app/router.tsx#L9)

- Renders reachable/unreachable state from the backend health check; never crashes on failure.
  [`HealthStatus.tsx:18`](../../frontend/src/app/HealthStatus.tsx#L18)

- Shared typed fetch client every future screen will call through; JSON-parse failures now throw a clean `ApiRequestError` (review-loop patch).
  [`client.ts:63`](../../frontend/src/api/client.ts#L63)

**Local dev environment**

- `postgres:18` service only this story; backend/frontend containers deferred to Epic 4.
  [`docker-compose.yml:14`](../../docker-compose.yml#L14)

- Compose healthcheck backing the `docker compose up postgres` acceptance criterion.
  [`docker-compose.yml:24`](../../docker-compose.yml#L24)

- Broadened to ignore every `.env*` variant except the template (review-loop patch).
  [`.gitignore:37`](../../.gitignore#L37)

**Peripherals**

- Empty baseline migration proving Flyway runs cleanly against a fresh DB (AD-6: no module owns a table yet).
  [`V1__baseline.sql`](../../backend/src/main/resources/db/migration/V1__baseline.sql#L1)
- Maven project pinned to Java 21 / Spring Boot 4.1.1.
  [`pom.xml`](../../backend/pom.xml#L1)
- `@types/node` pinned to match the documented Node 20+ minimum; `engines` field added (review-loop patch).
  [`package.json`](../../frontend/package.json#L21)
- Documents every required variable with no real values (NFR-2).
  [`.env.example`](../../.env.example#L1)
