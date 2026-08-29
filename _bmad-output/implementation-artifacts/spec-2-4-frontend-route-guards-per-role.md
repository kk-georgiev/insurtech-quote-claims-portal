---
title: 'Story 2.4: Frontend Route Guards Per Role'
type: 'feature'
created: '2026-08-29'
status: 'done'
review_loop_iteration: 0
baseline_commit: '3b758a52f84a3855f9a2763a6a2f68a352515e51'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** None of the four shell routes (`/`, `/agent`, `/liquidator`, `/administrator`) check who is looking. A logged-in CLIENT can type `/agent` and see Agent content; an anonymous visitor can reach any of the four (pinned today by `shells.test.tsx`'s "renders for an unauthenticated direct visit, with no redirect" cases, explicitly written to be flipped by this story). Backend authorization (Story 1.4) already rejects the wrong role server-side, but the UI never even attempts to hide what it shouldn't show.

**Approach:** Add one shared `RoleGuard` route-wrapper component, parameterized by the single `Role` it protects, and nest each of the four shell routes under an instance of it in `router.tsx`. No valid role (not logged in / unparseable token) redirects to `/login`; a valid role on the wrong route redirects to that role's own home via the existing `roleHome()`; a matching role renders normally via `<Outlet />`.

## Boundaries & Constraints

**Always:**
- Exactly one `RoleGuard` component implementation, reused four times with a different `role` prop — never per-screen ad hoc checks (AD-10, epic-2-context.md).
- Role comes only from the decoded JWT (`getToken` + `decodeToken` + `isRole`, `api/authToken.ts` / `app/roleHome.ts`) — never from props, global state, or re-derived logic.
- `/login`, `/register`, and `/health` stay unguarded — they are not role-restricted.
- Redirects use `<Navigate replace>` so the blocked URL never lands in browser history.
- Keep all four shells' existing `data-testid`s untouched.

**Ask First:**
- Anything beyond redirecting (e.g. a "forbidden" flash message) — out of scope unless requested.

**Never:**
- No change to backend authorization — it remains the real security boundary (AD-4), independently correct regardless of this guard.
- No change to `LoginForm`'s post-login `navigate()` call or to `roleHome.ts`'s existing `ROLE_HOME` table/`roleHome()` function.
- No new routing library or state-management dependency.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Own route | logged in as X, visit X's route | X's shell renders | N/A |
| Wrong role | logged in as X, visit Y's route (X≠Y) | redirected to `roleHome(X)` | N/A |
| Symmetric | every ordered (X,Y) role pair, X≠Y | same redirect-to-own-home behavior | N/A |
| Anonymous | no token, visit any of the 4 shell routes | redirected to `/login` | N/A |
| Malformed token | unparseable/undecodable token present, visit any shell route | treated as no role — redirected to `/login` | N/A |

</frozen-after-approval>

## Code Map

- `frontend/src/app/roleHome.ts` -- add `export type StaffRole = Exclude<Role, 'CLIENT'>`, `export const STAFF_ROLES`, and `export function getCurrentRole(): Role | null` (wraps `getToken()` + `decodeToken()` + `isRole()`); closes the deferred-work item asking Story 2.4 to export `StaffRole`/`STAFF_ROLES` here instead of `shells.test.tsx` re-deriving them.
- `frontend/src/app/RoleGuard.tsx` -- new. `function RoleGuard({ role }: { role: Role })`; uses `getCurrentRole()`, `roleHome()`; renders `<Outlet />` or `<Navigate to=... replace />`.
- `frontend/src/app/router.tsx:19-27` -- wrap each of the 4 shell `children` entries in its own `{ element: <RoleGuard role="X" />, children: [...] }`; `health`/`register`/`login` entries unchanged.
- `frontend/src/app/router.test.tsx:36-43` -- the anonymous `/` test currently asserts `client-shell` renders with no login; must change to assert redirect to `/login`. Add guard cases per the I/O Matrix, reusing `renderAt()`; needs a small token-seeding helper (fake unsigned JWT via `saveToken` + a hand-built `header.payload.sig` string, since `decodeToken` never checks the signature).
- `frontend/src/features/shells/shells.test.tsx:19-21,117-126` -- drop the locally-defined `StaffRole`/`STAFF_ROLES` in favor of importing them from `roleHome.ts`; flip the "unauthenticated direct visit, no redirect" cases to assert redirect to `/login`.
- `README.md:74-76`, `frontend/README.md:8-9` -- remove the "no route guards yet" caveat now that this story adds them.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/app/roleHome.ts` -- add `StaffRole`, `STAFF_ROLES`, `getCurrentRole()` -- single source for role-derived helpers the guard and tests both need.
- [x] `frontend/src/app/RoleGuard.tsx` -- new shared wrapper component -- the one guard implementation the AC requires.
- [x] `frontend/src/app/router.tsx` -- nest all 4 shell routes under `RoleGuard` instances -- wires the guard into the real route table.
- [x] `frontend/src/app/router.test.tsx` -- update the anonymous-`/` case; add own-role/wrong-role/anonymous/malformed-token cases across all 4 roles -- proves the symmetric AC.
- [x] `frontend/src/features/shells/shells.test.tsx` -- import `StaffRole`/`STAFF_ROLES` from `roleHome.ts`; flip the anonymous-visit cases to expect redirect -- keeps this suite honest about the new behavior.
- [x] `README.md`, `frontend/README.md` -- drop the stale "no route guards yet" line.

**Acceptance Criteria:**
- Given I am logged in as CLIENT, when I navigate to `/agent` (or `/liquidator`, `/administrator`), then I land back on `/`, never seeing that role's content.
- Given this holds symmetrically for every role pair, when tested, then the same redirect-to-own-home applies in every case.
- Given no valid role (logged out or a malformed token), when I visit any of the 4 shell routes, then I am redirected to `/login`.
- Given `npm run typecheck`, `npm run build`, and `npm test` in `frontend/`, when run, then all pass.

## Design Notes

`RoleGuard` shape:
```tsx
export function RoleGuard({ role }: { role: Role }) {
  const current = getCurrentRole();
  if (current === role) return <Outlet />;
  return <Navigate to={current ? roleHome(current) : '/login'} replace />;
}
```
`router.tsx` nesting for one route (repeated per role):
```tsx
{ element: <RoleGuard role="AGENT" />, children: [{ path: 'agent', element: <AgentShell /> }] }
```

## Verification

**Commands:**
- `cd frontend; npm run typecheck; npm run build; npm test` -- all clean; existing 6 router-test cases plus the 5 shells-test cases still pass (2 rewritten for the new redirect, not deleted), plus new guard cases.

**Manual checks:**
- `npm run dev`, log in as CLIENT, manually type `/agent` in the address bar -- redirected back to `/`. Log out (clear `localStorage`), visit `/administrator` directly -- redirected to `/login`.

**Verification note (added post-implementation, 2026-08-29):** `npm test` on this machine (Node v24.18.0) cannot run any test that triggers a real `navigate()`/`<Navigate>` — a pre-existing jsdom/undici `AbortSignal` incompatibility, reproducing identically on the untouched `LoginForm.test.tsx`, unrelated to this story (see `deferred-work.md`, "Story 2.4 verification" entry). `typecheck`/`build` are clean; the non-redirecting RoleGuard cases pass in `npm test`; every I/O-matrix redirect scenario was instead verified manually end-to-end via a live `npm run dev` session (fake tokens seeded into `localStorage`), with no console errors. CI (Node 20) is unaffected and is expected to run the full suite green.

## Suggested Review Order

**The guard itself**

- Entry point: the three-branch redirect decision (own role / other valid role / no role) that everything else wires into.
  [`RoleGuard.tsx:23`](../../frontend/src/app/RoleGuard.tsx#L23)

- Where "current role" comes from — `null` covers no-token, undecodable-token, and unrecognized-role-claim alike, all treated the same by the guard above.
  [`roleHome.ts:58`](../../frontend/src/app/roleHome.ts#L58)

**Wiring into the route table**

- Each of the four shell routes nested under its own `RoleGuard` instance; `health`/`register`/`login` deliberately left outside any guard.
  [`router.tsx:23`](../../frontend/src/app/router.tsx#L23)

**Test coverage of the I/O matrix**

- The full symmetric matrix: own route (4), every ordered wrong-role pair (12), anonymous (4), malformed token (4), decodable-but-invalid-role token (4) — 28 cases in one guard suite.
  [`router.test.tsx:187`](../../frontend/src/app/router.test.tsx#L187)

- `shells.test.tsx`'s pinned "no redirect while logged out" case flipped to its opposite now that the guard exists — the behavioral change this story was for.
  [`shells.test.tsx:129`](../../frontend/src/features/shells/shells.test.tsx#L129)

**Peripherals**

- Shared fake-JWT builder factored out once both test files needed the identical encoding.
  [`seedToken.ts:15`](../../frontend/src/test/seedToken.ts#L15)

- `StaffRole`/`STAFF_ROLES` moved here from `shells.test.tsx`'s local re-derivation — the deferred-work item this story was asked to close.
  [`roleHome.ts:14`](../../frontend/src/app/roleHome.ts#L14)

- Doc refresh: the "no route guards yet" caveat replaced with what actually redirects where.
  [`README.md:74`](../../README.md#L74)
