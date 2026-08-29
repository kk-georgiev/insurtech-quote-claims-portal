---
title: 'Story 4.1: Backend and Frontend Dockerfiles'
type: 'feature'
created: '2026-08-29'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '6c5c55921b9ea722ed30229d40851e26d7d38e24'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Neither `backend/` nor `frontend/` has a Dockerfile, so the app can only run natively (JDK/Maven, Node) — nobody can package either side as a container image, which blocks Story 4.2's one-command full-stack startup.

**Approach:** Add one multi-stage `Dockerfile` per app. Backend: Maven+JDK build stage producing the Spring Boot fat jar, copied into a slim JRE runtime stage. Frontend: Node build stage running `npm run build`, copied into an nginx stage that serves the static SPA with client-side-routing fallback.

## Boundaries & Constraints

**Always:** Multi-stage builds — no build toolchain (Maven/JDK/Node) in the final runtime image. Backend runs as a non-root user. `VITE_API_URL` is supplied as a Docker build `ARG` (Vite inlines `import.meta.env.*` at build time, per `frontend/vite.config.ts` and `frontend/src/api/client.ts` — a runtime env var would not work). Each Dockerfile gets a matching `.dockerignore` mirroring the root `.gitignore`'s ignored paths for that app (`target/`, `node_modules/`, `dist/`, `.git/`, `.env*`). Backend image build does not run `mvn test` (Testcontainers tests need a Docker daemon reachable from inside the build, out of scope here — CI's `mvn test` already covers correctness).

**Ask First:** None anticipated.

**Never:** Do not modify `docker-compose.yml` — wiring these images into a `backend`/`frontend` service belongs to Story 4.2. Do not modify `.github/workflows/ci.yml`. Do not add health-check scripts, entrypoint wrappers, or compose-service definitions beyond the two Dockerfiles + their `.dockerignore` + the frontend's nginx config.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Backend image runs against reachable Postgres | Container started with `POSTGRES_HOST/PORT/DB/USER/PASSWORD` pointing at a live Postgres on the same Docker network | Spring Boot starts, Flyway migrations apply, `GET /actuator/health` → 200 `{"status":"UP"}` | N/A |
| Frontend image built with a given `VITE_API_URL` | `docker build --build-arg VITE_API_URL=<url>` | Built JS bundle calls exactly `<url>`, not a hardcoded literal | N/A |
| Client-side route requested directly | `GET /agent` (or any React Router path) against the running frontend container | nginx serves `index.html` (200) via SPA fallback | An actual missing static asset still 404s normally |

</frozen-after-approval>

## Code Map

- `backend/pom.xml` — Maven build, no wrapper present; `spring-boot-maven-plugin` repackages `mvn clean package` output into a runnable fat jar.
- `backend/src/main/resources/application.yml` — reads `POSTGRES_HOST/PORT/DB/USER/PASSWORD` via `${VAR:default}`; `server.port: 8080`; actuator health exposed at `/actuator/health`.
- `frontend/package.json` — `build`: `tsc -b && vite build`; `engines.node >= 20`; Volta-pinned `20.20.2`; matches root `frontend/.nvmrc` (`20`).
- `frontend/vite.config.ts` — `envDir` points at the repo root (one shared `.env`/`.env.example`, not per-package).
- `frontend/src/api/client.ts` — reads `import.meta.env.VITE_API_URL` at module load; throws if unset.
- `.gitignore` — `backend/target/`, `frontend/node_modules/`, `frontend/dist/`, `.env*` — mirror these in the new `.dockerignore` files.
- `docker-compose.yml` — currently `postgres`-only; its header comment already states backend/frontend services land in Epic 4 (this story doesn't touch this file).
- `.env.example` — documents `VITE_API_URL` (browser-reachable backend origin, never a compose service name) and the `POSTGRES_*` variables the backend container needs.

## Tasks & Acceptance

**Execution:**
- [x] `backend/Dockerfile` -- multi-stage (`maven:3.9-eclipse-temurin-21` build → `eclipse-temurin:21-jre-alpine` runtime); `mvn -B -DskipTests package`; non-root user; `EXPOSE 8080`; `ENTRYPOINT ["java","-jar","app.jar"]` -- containerizes the backend per Epic 4's Technical Decisions
- [x] `backend/.dockerignore` -- exclude `target/`, `.git/`, IDE files -- keeps build context small, avoids leaking local build artifacts into the image layer cache
- [x] `frontend/Dockerfile` -- multi-stage (`node:20-alpine` build running `npm ci && npm run build` with `ARG`/`ENV VITE_API_URL` -- `nginx:alpine` runtime serving `dist/`) -- Node 20 matches the project's pinned engine/Volta version
- [x] `frontend/nginx.conf` -- server block with `try_files $uri $uri/ /index.html` -- required because React Router (AD-10) owns client-side routing; without this, a direct hit on any non-root path 404s at nginx instead of reaching the router
- [x] `frontend/.dockerignore` -- exclude `node_modules/`, `dist/`, `.git/`, etc. -- keeps build context small

**Acceptance Criteria:**
- Given the backend Dockerfile, when built and run with valid `POSTGRES_*` env vars against a reachable Postgres, then the container starts, Flyway migrations succeed, and `/actuator/health` reports `UP`.
- Given the frontend Dockerfile, when built with `--build-arg VITE_API_URL=<url>` and run, then the container serves the built SPA on port 80 and the bundled JS calls exactly `<url>`.
- Given a client-side route requested directly against the running frontend container, when received, then nginx returns the SPA shell (200), not a 404.
- Given `docker-compose.yml`, when this story is done, then it is unmodified — wiring these images in is Story 4.2's scope.

## Spec Change Log

## Verification

**Commands:**
- `docker build -t motorinsurance-backend:test ./backend` -- expected: build succeeds
- `docker build -t motorinsurance-frontend:test --build-arg VITE_API_URL=http://localhost:8080 ./frontend` -- expected: build succeeds

**Manual checks (if no CLI):**
- Run the backend image attached to the network of an already-running `docker compose up postgres`; `curl /actuator/health` → `{"status":"UP"}`.
- Run the frontend image; `curl /` and `curl /agent` both → 200; the built JS asset contains the exact injected `VITE_API_URL` value.

## Suggested Review Order

**Backend image**

- Entry point: multi-stage build producing the Spring Boot fat jar, then a slim non-root runtime.
  [`Dockerfile:7`](../../backend/Dockerfile#L7)

- Runtime stage: non-root user setup and how the built jar is launched.
  [`Dockerfile:23`](../../backend/Dockerfile#L23)

**Frontend image**

- Build-time injection of `VITE_API_URL` — must be an `ARG`, not a runtime env var, because Vite inlines it.
  [`Dockerfile:20`](../../frontend/Dockerfile#L20)

- Runtime stage: static SPA served by nginx, paired with `nginx.conf`'s routing rules below.
  [`Dockerfile:26`](../../frontend/Dockerfile#L26)

- SPA fallback vs. real-asset 404: the two `location` blocks that make client-side routes resolve while missing assets still 404.
  [`nginx.conf:15`](../../frontend/nginx.conf#L15)

**Peripherals**

- Backend build-context exclusions mirroring root `.gitignore`.
  [`.dockerignore:1`](../../backend/.dockerignore#L1)

- Frontend build-context exclusions mirroring root `.gitignore`.
  [`.dockerignore:1`](../../frontend/.dockerignore#L1)
