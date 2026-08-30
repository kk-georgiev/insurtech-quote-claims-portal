<!--
Fill this in — see CONTRIBUTING.md §3 (PR process) and, for a dev -> main
release PR, §3a. Delete this comment block before submitting.
-->

## What & why

<!-- 1–3 sentences: what changed and why. Link the issue/story if any
     (e.g. "Closes #123"). -->

## Tested

<!-- Commands run (e.g. `mvn clean test`, `npm run typecheck && npm test && npm run build`)
     and/or manual verification steps. -->

## Screenshots / GIF

<!-- UI changes only — delete this section otherwise. -->

## Checklist

- [ ] PR title follows Conventional Commits (§2) — for a normal PR this becomes the
      permanent squash-merge commit message on `dev`
- [ ] Base branch is `dev` — or, for a `dev → main` release promotion (§3a), base is
      `main` and this PR's source is `dev` itself
- [ ] Branch name follows `feature|fix|chore|docs/<description>` (§1) — n/a for a
      `dev → main` release PR
- [ ] Branch was kept up to date from `dev` via `merge`, not `rebase` (§1)
- [ ] Self-reviewed against §4: business-logic correctness, test coverage, security
      (IDOR, input validation), modular structure (thin controllers, business logic
      in `application`/`domain`, not in React components)
- [ ] CI is green, when available (§3/§6)
- [ ] Reviewer aware of merge strategy: **Squash and merge** into `dev` (default) vs.
      **Merge commit** for a `dev → main` release PR (§3a)
- [ ] I did not push directly to `dev`/`main` — this PR is the only way these changes land
- [ ] After merge: delete the branch (local + remote, §3)
