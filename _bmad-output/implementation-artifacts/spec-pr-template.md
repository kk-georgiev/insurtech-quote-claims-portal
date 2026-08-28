---
title: 'PR Template'
type: 'chore'
created: '2026-08-28'
status: 'done'
route: 'one-shot'
review_loop_iteration: 0
context: []
---

# PR Template

## Intent

**Problem:** `.github/PULL_REQUEST_TEMPLATE.md` was the last unchecked item in `CONTRIBUTING.md` §6's repo-hygiene checklist — every PR body so far has been hand-written from memory of the process, with no structural reminder of the conventions §1/§2/§3/§3a/§4 actually require.

**Approach:** A single template file whose checklist maps directly to those sections — base branch, branch naming, merge strategy (squash vs. merge-commit for a release), self-review criteria, and post-merge cleanup — reviewed once, then `CONTRIBUTING.md`'s own checklist line updated to describe what actually shipped.

## Suggested Review Order

- Entry point: the checklist, each item traceable to a specific `CONTRIBUTING.md` section cited inline.
  [`PULL_REQUEST_TEMPLATE.md:20`](../../.github/PULL_REQUEST_TEMPLATE.md#L20)

- `CONTRIBUTING.md` §6's own line, reworded post-review to describe the template's actual (broader) scope.
  [`CONTRIBUTING.md:166`](../../CONTRIBUTING.md#L166)

- One deferred finding from the review (release-tag reminder — post-merge, doesn't fit a pre-submission checklist).
  [`deferred-work.md:195`](../../_bmad-output/implementation-artifacts/deferred-work.md#L195)
