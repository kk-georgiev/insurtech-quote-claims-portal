---
title: 'CI Pipeline'
type: 'chore'
created: '2026-08-28'
status: 'done'
route: 'one-shot'
review_loop_iteration: 0
context: []
---

# CI Pipeline

## Intent

**Problem:** No automated pipeline runs the backend's 76-test Testcontainers suite or the frontend's typecheck/build on any push or PR — regressions are caught only if someone remembers to run `mvn clean test` / `npm run build` locally. Both `CONTRIBUTING.md` §6 and Story 2.1's own review flagged this gap independently.

**Approach:** A single GitHub Actions workflow (`.github/workflows/ci.yml`) with two parallel jobs — backend (`mvn clean test` on JDK 21) and frontend (`npm run typecheck && npm test && npm run build` on Node 20) — triggered on every PR/push to `main`/`dev`, plus manual dispatch. Rebased on `dev` mid-flight to pick up Story 2.2's Vitest/RTL toolchain, so the frontend job ships with real test coverage from day one instead of a follow-up patch.

## Suggested Review Order

**The workflow itself**

- Entry point: triggers, concurrency (scoped to PR-only cancellation), least-privilege permissions.
  [`ci.yml:1`](../../.github/workflows/ci.yml#L1)

- Backend job: JDK 21/Temurin matches local dev tooling (spec-2-1); batch-mode Maven for clean CI logs; no wrapper exists, so this drives the runner's system `mvn`.
  [`ci.yml:20`](../../.github/workflows/ci.yml#L20)

- Frontend job: Node 20 matches `package.json`'s `engines` constraint; typecheck kept as a separate step from build for a clearer failure signal.
  [`ci.yml:59`](../../.github/workflows/ci.yml#L59)

- `npm test` runs Story 2.2's Vitest suite (jsdom, no real backend needed) between typecheck and build.
  [`ci.yml:79`](../../.github/workflows/ci.yml#L79)

**Documentation kept honest**

- Repo-hygiene checklist: CI item checked off; also backfilled three already-verified-done items (default branch, both branch-protection rulesets) that were never checked.
  [`CONTRIBUTING.md:156`](../../CONTRIBUTING.md#L156)

- CI status badge added next to the project title.
  [`README.md:3`](../../README.md#L3)

- Story 2.1's "no CI" finding and Story 2.2's "no CI runner for Vitest" finding both marked resolved, plus six new deferred findings from this change's own review (report artifacts, image caching, lint tooling, PR template, dependency scanning).
  [`deferred-work.md:149`](../../_bmad-output/implementation-artifacts/deferred-work.md#L149)
