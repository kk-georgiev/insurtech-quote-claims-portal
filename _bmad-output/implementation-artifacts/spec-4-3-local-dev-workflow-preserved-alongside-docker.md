---
title: 'Story 4.3: Local Dev Workflow Preserved Alongside Docker'
type: 'feature'
created: '2026-08-29'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '5d15588ee2adc1f2447280e72570e4fd6a9b63d1'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Stories 4.1/4.2 added Docker images and full-stack Compose wiring; Story 1.1's native dev path (`docker compose up postgres` + `mvn spring-boot:run` + `npm run dev`) must still work unchanged, and neither `backend/README.md` nor `frontend/README.md` currently tells a reader that the Docker full-stack alternative exists.

**Approach:** Verify the native dual-mode path end-to-end against the now-larger `docker-compose.yml`, and add a one-line cross-reference in each module README pointing to the root README's Docker path — so a reader who opens either file in isolation still discovers both modes.

## Boundaries & Constraints

**Always:** `docker compose up postgres` must keep starting only Postgres, unaffected by the `backend`/`frontend` service definitions Story 4.2 added. `mvn spring-boot:run` and `npm run dev` must connect to that containerized-only Postgres exactly as Story 1.1 established, with no new manual step. README cross-references are additive — existing native-mode instructions are not restructured or removed.

**Ask First:** None anticipated.

**Never:** Do not modify `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile`, or any application source code — this story is verification plus documentation only. Do not duplicate the full native-mode steps across READMEs — cross-reference the root README, don't copy-paste its content.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Partial start still isolated | `docker compose up postgres` (alone), after Story 4.2's compose changes | Only the postgres container starts | N/A |
| Native backend against that Postgres | `mvn spring-boot:run` from `backend/` | Starts, Flyway migrates, `/actuator/health` reports `UP` — identical to Story 1.1 | N/A |
| Native frontend against that backend | `npm run dev` from `frontend/` | Dev server starts, reaches the backend via `VITE_API_URL` — identical to Story 1.1 | N/A |
| Reader opens only one module README | `backend/README.md` or `frontend/README.md` in isolation | Finds a pointer to the Docker full-stack alternative, not just native steps | N/A |

</frozen-after-approval>

## Code Map

- `docker-compose.yml` (Story 4.2) — now defines `postgres`/`backend`/`frontend`; `docker compose up postgres` targets only that named service, unaffected by the other two.
- `backend/README.md` — "Run natively against a containerized Postgres" section (Story 1.1); no mention yet of the Docker full-stack alternative.
- `frontend/README.md` — "Run" section (Story 1.1); same gap.
- `README.md` (root, Story 4.2) — already documents both the native steps and the "One-command alternative"; module READMEs should point back here, not duplicate it.

## Tasks & Acceptance

**Execution:**
- [x] Manual verification -- run `docker compose up postgres`, then `mvn spring-boot:run` (`backend/`) and `npm run dev` (`frontend/`), confirm both connect exactly as Story 1.1 established -- closes this story's core AC and confirms no regression from Stories 4.1/4.2
- [x] `backend/README.md` -- add a one-line cross-reference pointing to the root README's "One-command alternative" -- makes the dual-mode setup discoverable from this file alone
- [x] `frontend/README.md` -- add the same one-line cross-reference -- same reason

**Acceptance Criteria:**
- Given `docker compose up postgres` run alone, when observed, then only postgres starts, unaffected by the `backend`/`frontend` service definitions Story 4.2 added.
- Given that running Postgres, when `mvn spring-boot:run` and `npm run dev` are started, then both succeed and connect exactly as Story 1.1 established, with no new manual step required.
- Given `backend/README.md` and `frontend/README.md` read independently, when either is opened alone, then it documents its own native-mode steps fully and points to where the Docker full-stack alternative is documented.

## Spec Change Log

## Verification

**Commands:**
- `docker compose up postgres -d` -- expected: `docker compose ps` shows only `postgres` running
- `cd backend && mvn spring-boot:run` -- expected: starts, Flyway migrates, `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- `cd frontend && npm install && npm run dev` -- expected: dev server starts, reaches the backend

**Manual checks (if no CLI):**
- Open the frontend dev server URL; confirm `/health` shows "reachable" and log in with a seeded demo account.

## Suggested Review Order

- Cross-reference pointer added to the backend's own onboarding doc.
  [`README.md:14`](../../backend/README.md#L14)

- Identical cross-reference on the frontend side, same placement logic.
  [`README.md:75`](../../frontend/README.md#L75)
