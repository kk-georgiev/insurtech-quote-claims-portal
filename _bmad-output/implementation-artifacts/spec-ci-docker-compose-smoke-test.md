---
title: 'CI Docker Compose Smoke Test'
type: 'chore'
created: '2026-08-29'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '00ea696b5ad70963be626a6fb8a027f674bafb6e'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** No CI job builds or runs the Docker images or the `docker-compose.yml` stack Epic 4 added — a wiring regression (bad env var, wrong build context, wrong port) would merge to `dev`/`main` undetected, only surfacing when someone manually runs `docker compose up`. Tracked as a gap since Story 4.1's review, restated after Story 4.2, and named as `epic-4-retro-item-22` in the Epic 4 retrospective.

**Approach:** Add a third parallel job to the existing `.github/workflows/ci.yml` that runs `docker compose up --build -d --wait` (Compose v2's built-in health-gating, no custom polling loop needed) and then confirms the backend and frontend both actually respond, on every PR and push to `main`/`dev` — the same trigger scope the existing jobs already have.

## Boundaries & Constraints

**Always:** The new job lives in the existing `.github/workflows/ci.yml` (no new workflow file) and inherits its current triggers (`pull_request`/`push` to `main`/`dev`) — no per-job trigger override, so it runs on every PR as decided. Use `docker compose up --build -d --wait` — Compose v2's native wait-for-healthy — instead of a hand-rolled polling script. No `.env` file is created for this job: `docker-compose.yml`'s own `${VAR:-default}` fallbacks (Story 4.2) make a bare `docker compose up` work without one. On failure, dump `docker compose logs` before the job ends, so a red run is debuggable from the Actions log alone. Tear down with `docker compose down -v` in a step that always runs, pass or fail.

**Ask First:** None anticipated.

**Never:** Do not modify `docker-compose.yml`, any `Dockerfile`, or `nginx.conf` — this is a CI-only addition. Do not change the existing `backend`/`frontend` jobs' triggers or steps. Do not add Docker Hub authentication, a registry mirror, or build-layer caching in this same change — a distinct concern with its own tradeoffs, left for a separate pass if the rate-limit risk this job accepts ever becomes a real problem in practice.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Healthy stack | Current `docker-compose.yml` + Dockerfiles, unmodified | `docker compose up --build -d --wait` succeeds; `/actuator/health` and the frontend root both respond successfully | N/A |
| Compose wiring regression | A future bad env var name, wrong build context, or wrong internal port | `--wait` fails or times out, or a curl check fails | Job fails red; `docker compose logs` is dumped before exit |
| Docker Hub rate limiting | Shared GitHub-hosted runner IP pool | Job may intermittently fail for reasons unrelated to the code | Accepted, documented risk — same category already accepted for the backend job's Testcontainers `postgres:18` pulls; no mitigation added by this change |

</frozen-after-approval>

## Code Map

- `.github/workflows/ci.yml` — existing `backend`/`frontend` jobs; workflow-level `on: pull_request`/`push` to `[main, dev]`; a `concurrency` group already cancels superseded PR runs. GitHub-hosted `ubuntu-latest` runners ship a working Docker daemon + Compose v2 preinstalled (already relied on by the backend job's Testcontainers tests).
- `docker-compose.yml` — `postgres`/`backend`/`frontend` services; `backend` has a `healthcheck` (what `--wait` gates on); `frontend` does not yet (a separate, already-tracked deferred item); every variable has a `${VAR:-default}` fallback, so no `.env` is required for `docker compose up` to work.
- `_bmad-output/implementation-artifacts/deferred-work.md` — carries this exact gap as three separate entries (Story 4.1's review, Story 4.2's review, and `epic-4-retro-item-22`) — all three close with this change.

## Tasks & Acceptance

**Execution:**
- [x] `.github/workflows/ci.yml` -- add a `docker-compose` job: checkout, `docker compose up --build -d --wait`, `curl --fail` both `http://localhost:8080/actuator/health` and the frontend root, a `docker compose logs` step gated on `if: failure()`, and a `docker compose down -v` step gated on `if: always()` -- closes `epic-4-retro-item-22` and the two prior deferred entries it consolidates
- [x] `_bmad-output/implementation-artifacts/deferred-work.md` -- mark the Story 4.1 and Story 4.2 "no CI job exercises docker compose" entries RESOLVED, referencing this change, matching the project's existing convention for closed deferred items

**Acceptance Criteria:**
- Given a PR or push to `main`/`dev`, when CI runs, then the new job builds and starts `postgres`/`backend`/`frontend`, confirms the backend reports healthy, and confirms both `/actuator/health` and the frontend root respond successfully.
- Given a hypothetical compose-wiring regression, when this job runs against it, then it fails red with `docker compose logs` output visible in the Actions log, not a silent pass.
- Given the existing `backend`/`frontend` jobs, when this change ships, then their triggers and steps are unchanged.

## Spec Change Log

## Verification

**Commands:**
- `docker compose up --build -d --wait && curl -f http://localhost:8080/actuator/health && curl -o /dev/null -s -w "%{http_code}" http://localhost:5173/` -- expected: health JSON printed, `200` for the frontend, no `.env` file present
- `docker compose down -v` -- expected: clean teardown

**Manual checks (if no CLI):**
- After pushing the branch, open the PR's Actions run and confirm the new `docker-compose` job appears and passes alongside `backend`/`frontend`.

## Suggested Review Order

- Entry point: the new job's `up --build --wait --wait-timeout` — caps the health-wait explicitly so a stuck container fails fast instead of riding the job timeout.
  [`ci.yml:103`](../../.github/workflows/ci.yml#L103)

- The two health checks: `--retry-connrefused` covers the frontend's missing healthcheck (a known, separately-tracked gap) racing `--wait`.
  [`ci.yml:110`](../../.github/workflows/ci.yml#L110)

- Failure diagnostics: runs on `cancelled()` too, not just `failure()`, and dumps `docker compose ps` alongside `logs`.
  [`ci.yml:119`](../../.github/workflows/ci.yml#L119)

- Two prior deferred-work.md entries closed by this change.
  [`deferred-work.md:314`](../../_bmad-output/implementation-artifacts/deferred-work.md#L314)
