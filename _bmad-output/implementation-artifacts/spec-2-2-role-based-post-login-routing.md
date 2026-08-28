---
title: 'Story 2.2: Role-Based Post-Login Routing'
type: 'feature'
created: '2026-08-28'
status: 'done'
review_loop_iteration: 0
baseline_commit: '455b5f8636e36f344dff60eced7519b8bb2dacd8'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** After a successful login the user is stranded on the login screen looking at a plain "You are logged in as X" panel (`LoginForm.tsx`). The role carried in the JWT drives nothing — there is no per-role destination and no automatic navigation. FR-5 / UJ-2 require each role to land automatically in its own navigation shell with no manual selection.

**Approach:** On successful login, decode the token, validate the role against a typed set, persist the token only if the role is recognized, then navigate to that role's home route via React Router. Add four shell routes (client + three staff) as bare navigation targets — Story 2.3 builds their real labeled content and chrome. Introduce the frontend's first test tooling (Vitest + React Testing Library) and pin the routing behaviour with it. Backend is untouched: the token already carries the role (Story 1.3).

## Boundaries & Constraints

**Always:**
- The role model is one typed module: a `Role` union, an `isRole` runtime type guard, and `roleHome(role: Role): string` backed by an exhaustive `Record<Role, string>` (a missing role fails compilation). No `string`-typed role→path lookup, no silent default path.
- Token order in `LoginForm`: decode → `isRole` check → **only then** `saveToken`. A login whose token does not decode or whose role is not an `isRole` match is handled exactly like a failed login — generic error, form stays editable, and the token is **not** written to `localStorage`.
- Unknown/undecodable role is a controlled failure at the call site (`LoginForm`), never absorbed by `roleHome`.
- React Router v8 owns navigation (AD-10); the redirect uses `useNavigate`, never `window.location`.
- The frontend role check is a UX convenience, never a security boundary (AD-4). This story adds **no** access enforcement — typing `/agent` still shows the stub; that is Story 2.4.
- Role is read only from the decoded JWT (`decodeToken`), never threaded through props or global state.

**Ask First:**
- Adding any dependency beyond the Vitest + RTL + jsdom test toolchain.

**Never:**
- No backend changes; no new API calls.
- No route guards or access enforcement (Story 2.4).
- No finished shell content: no role labels, "coming soon" copy, layout, or nav menus (Story 2.3). The stubs are route targets only.
- No logout / auth-aware nav (existing deferred item from Story 1.3).
- No i18n (Epic 3).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| CLIENT login | valid CLIENT credentials submitted | token saved; app navigates to the client shell (`/`) | N/A |
| AGENT login | valid AGENT credentials | token saved; app navigates to `/agent` | N/A |
| LIQUIDATOR login | valid LIQUIDATOR credentials | token saved; app navigates to `/liquidator` | N/A |
| ADMINISTRATOR login | valid ADMINISTRATOR credentials | token saved; app navigates to `/administrator` | N/A |
| Undecodable token | login returns 200 but `decodeToken` returns `null` | no navigation; generic error; form editable; **`localStorage` has no token** | reuse existing generic-error path |
| Unrecognized role | token decodes but `role` fails `isRole` | no navigation; generic error; **`localStorage` has no token** | same as above |
| Direct visit to a staff URL | user types `/agent` (any auth state) | the AGENT stub renders — no guard yet | N/A — Story 2.4 |
| Invalid credentials | wrong password / unknown email | existing 401 handling unchanged; no navigation; no token saved | existing `AUTH_INVALID_CREDENTIALS` copy |

</frozen-after-approval>

## Code Map

- `frontend/src/app/router.tsx:11-21` — the route table. Add the four shell routes as `RootLayout` children; move `HealthStatus` (currently `index`) to `path: 'health'`; the client shell becomes `index`.
- `frontend/src/features/auth/LoginForm.tsx:61-99` — submit handler + success render. Today `:68-71` saves the token, then decodes. New order: decode → `isRole` guard → on fail `GENERIC_ERROR_MESSAGE` + `phase='editing'` + return with **no `saveToken`** → else `saveToken` → `navigate(roleHome(decoded.role))`. Drop the `phase === 'success'` panel (`:92-99`) and `data-testid="login-success"` (unreferenced).
- `frontend/src/api/authToken.ts:42-59` — `decodeToken` → `{ sub, role, iat?, exp? } | null`; `role` stays a raw `string` (a decode is not a validation). `saveToken`/`getToken` (`:14-21`) reused unchanged.
- `frontend/src/app/RootLayout.tsx:10-25` — shells render in this layout's `<Outlet/>`; its `Register`/`Login` `<nav>` stays as-is (auth-aware nav is a separate deferred item).
- `main.tsx` wraps the app in `StrictMode` (double-invoke); the forms' existing `cancelledRef` pattern tolerates it — tests must too.
- NEW `frontend/src/app/roleHome.ts` — `Role`, `isRole`, `roleHome` (see Design Notes).
- NEW `frontend/src/features/shells/{client,agent,liquidator,administrator}/…Shell.tsx` — bare route targets (a `data-testid` hook + a deliberately provisional placeholder, **not** final copy); path matches the Architecture Structural Seed (`features/shells/`).
- `frontend/package.json:10-16` (scripts: `dev`/`build`/`preview`/`typecheck` only) and `frontend/vite.config.ts` (Vitest can extend it). `frontend/tsconfig.app.json` is `strict` + `noUnusedLocals`/`noUnusedParameters` + `verbatimModuleSyntax` — tests must satisfy these.
- `_bmad-output/implementation-artifacts/deferred-work.md:71-73` — the existing Story 1.5 entry recording that no quote-flow frontend exists. This story appends a follow-up (see Tasks).
- Read-only evidence: **no frontend test exists anywhere in repo history** (`git log --all -- 'frontend/**/*.test.*'` empty); Stories 1.2/1.3 verified by `typecheck` + `build` + manual only.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/package.json` -- add devDeps `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, `jsdom`; add scripts `"test": "vitest run"` and `"test:watch": "vitest"` -- first frontend test toolchain, reused by Stories 2.3, 2.4, Epic 3.
- [x] `frontend/vitest.config.ts` + `frontend/src/test/setup.ts` -- jsdom environment, `@testing-library/jest-dom` matchers, RTL auto-cleanup; wire `setupFiles`. (`tsconfig.node.json` also updated to include the config.)
- [x] `frontend/src/app/roleHome.ts` -- `Role` union, `isRole` type guard, and `roleHome(role: Role): string` over an exhaustive `Record<Role, string>`. No fallback branch.
- [x] `frontend/src/features/shells/{client,agent,liquidator,administrator}/` -- one bare shell component each: a `data-testid` (e.g. `agent-shell`) and provisional placeholder text only. No layout, labels, or nav.
- [x] `frontend/src/app/router.tsx` -- register the four shell routes under `RootLayout`; relocate `HealthStatus` to `path: 'health'`; client shell as `index`. Route table exported as `routes` for tests.
- [x] `frontend/src/features/auth/LoginForm.tsx` -- reorder to decode → `isRole` → conditional `saveToken` → `navigate(roleHome(role))`; remove the `success` phase render and `data-testid="login-success"`; failed validation → generic error, no token saved, no navigation.
- [x] `frontend/src/app/roleHome.test.ts` -- unit-test `roleHome` for all four roles and `isRole` for valid + invalid + non-string inputs.
- [x] `frontend/src/features/auth/LoginForm.test.tsx` -- `apiFetch` mocked, `createMemoryRouter`: each role's successful login navigates to its route (assert `router.state.location.pathname`, not shell prose) and the token is in `localStorage`; undecodable-token and unknown-role each assert **all three** of {generic error shown, pathname unchanged, `getToken()` returns `null`}; invalid-credentials path unchanged.
- [x] `frontend/src/app/router.test.tsx` -- route-table coverage: client shell at `index`, health screen relocated to `/health`, and each staff route renders its stub on a direct unauthenticated visit (matrix row: "no guard yet" — Story 2.4).
- [x] `_bmad-output/implementation-artifacts/deferred-work.md` -- append a Story 2.2 entry: after this story a logged-in CLIENT lands on a bare client-shell stub because Epic 1's quote-flow frontend was never built; cross-reference the existing `:71-73` entry; note that turning it into an `epics.md` story is a PM decision, not this story's.

**Acceptance Criteria:**
- Given a successful login as CLIENT, when the response is handled, then the token is stored and the app navigates to the client shell route (`/`) — which is a bare stub this story, **not** an Epic 1 quote flow (that frontend does not exist; see `deferred-work.md`).
- Given a successful login as AGENT, LIQUIDATOR, or ADMINISTRATOR, when handled, then the token is stored and the app navigates to that role's own distinct route.
- Given a 200 login whose token does not decode or whose role is unrecognized, when handled, then no navigation occurs, a generic error shows, and no token is written to `localStorage`.
- Given `npm run typecheck`, `npm run build`, and `npm test` in `frontend/`, when run, then all three pass — and `npm test` is a script that did not exist before this story.
- Given each of the four roles logging in manually in a browser, when each logs in, then each lands on a distinct URL naming its role, with the client shell at `/`.

## Design Notes

`roleHome` is a total function over a typed `Role`, so Story 2.4's guard can reuse it to compute "where does this user belong" with no duplicated table and no untyped lookup:

```ts
export const ROLES = ['CLIENT', 'AGENT', 'LIQUIDATOR', 'ADMINISTRATOR'] as const;
export type Role = (typeof ROLES)[number];

export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value);
}

const ROLE_HOME: Record<Role, string> = {
  CLIENT: '/', AGENT: '/agent', LIQUIDATOR: '/liquidator', ADMINISTRATOR: '/administrator',
};

export function roleHome(role: Role): string {
  return ROLE_HOME[role];
}
```

`Record<Role, string>` makes the compiler reject the file if a role is unmapped. `roleHome` has no `null`/unknown branch — that case never reaches it: `LoginForm` (and later the guard) call `isRole` first and own the failure path. The controlled error lives at the call site, not inside `roleHome`.

2.2's `LoginForm` tests assert on the resolved route (`router.state.location.pathname`), never on shell copy, so Story 2.3 can write that copy freely.

## Verification

**Commands:**
- `cd frontend; npm install` -- pulls the new test toolchain.
- `cd frontend; npm run typecheck; npm run build; npm test` -- all clean. `npm test` runs the new Vitest suite (`roleHome`/`isRole` + `LoginForm` routing). Node 20+ (`package.json` engines); no Docker or backend needed (`apiFetch` is mocked).

**Manual checks:**
- `docker compose up postgres`, start the backend, `cd frontend; npm run dev`. Log in as `agent@motorinsurance.demo` / `DemoPass123!` → lands on `/agent`. Repeat for `liquidator@…`, `administrator@…` (Story 2.1 seed), and a freshly self-registered CLIENT (→ `/`).
- After a failed-validation path (only reproducible by pointing at a stub backend that returns an odd token), confirm `localStorage` holds no `motorinsurance.auth.token`.
- Visit `/liquidator` directly in a fresh tab without logging in → the stub still renders (no guard — confirms Story 2.4 still has work).

## Suggested Review Order

**The routing decision**

- Entry point: the role model — typed `Role`, `isRole` guard, `roleHome` as a total function over an exhaustive `Record<Role, string>`.
  [`roleHome.ts:37`](../../frontend/src/app/roleHome.ts#L37)

- Post-login: decode → `isRole` → **only then** `saveToken` → `navigate(..., { replace: true })`. Unknown/undecodable role is a controlled failure here, never in `roleHome`.
  [`LoginForm.tsx:84`](../../frontend/src/features/auth/LoginForm.tsx#L84)

**The route table**

- Four shell routes; client at `index`, `HealthStatus` relocated to `/health`.
  [`router.tsx:25`](../../frontend/src/app/router.tsx#L25)

- `createBrowserRouter` moved here so `router.tsx` stays a side-effect-free table the tests can mount.
  [`App.tsx:7`](../../frontend/src/app/App.tsx#L7)

- Bare stubs — `<section><h2>Role</h2>` + `data-testid`, no copy Story 2.3 would have to unpick.
  [`AgentShell.tsx`](../../frontend/src/features/shells/agent/AgentShell.tsx)

**Tests (first frontend suite)**

- `roleHome` ↔ route-table loop closed: `roleHome(role)` must resolve to a route that renders that role's shell.
  [`router.test.tsx:28`](../../frontend/src/app/router.test.tsx#L28)

- Each role's login lands on its route with the token stored; the two controlled-failure rows assert error + pathname + `getToken()` null + form re-enabled; plus a no-`token`-field case and a recovery-after-failure case.
  [`LoginForm.test.tsx:54`](../../frontend/src/features/auth/LoginForm.test.tsx#L54)

- Toolchain: Vitest extends the app's Vite config; `mockReset: true` set once so test files don't hand-roll it.
  [`vitest.config.ts`](../../frontend/vitest.config.ts)

**Docs & deferred**

- Both READMEs re-synced (landing page, `npm test`, layout) and `RootLayout` gets a `/health` link.
  [`README.md:48`](../../README.md#L48)

- Four deferred entries: quote-flow frontend gap, no CI for the frontend suite, no `errorElement`/404 route, no coverage story.
  [`deferred-work.md`](deferred-work.md)
