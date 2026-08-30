---
title: 'Logout Action and Authenticated Navigation'
type: 'feature'
created: '2026-08-30'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '0d815f69c54e80ea3aef91399d94644190dcf858'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Every authenticated user is permanently signed in with no way to end their session — `RootLayout`'s nav is not auth-aware and has no Logout control (its own comment already flags this as a deferred gap from Story 3.1).

**Approach:** Give `RootLayout` an auth-aware nav, derived from `roleHome.ts`'s existing `getCurrentRole()` on every render, that swaps Register/Login for a Logout control when authenticated; clicking it clears the stored token and navigates to `/login`.

## Boundaries & Constraints

**Always:** Auth state for the nav is derived fresh from `getCurrentRole()` on each render — no separate tracked auth state. Logout is client-side only (clear the stored token, `navigate('/login')`) — no backend call, no server-side session invalidation (AD-3, no revocation this milestone). The Health link stays visible regardless of auth state. No page reload. New i18n key(s) ship in both `bg.json` and `en.json` in this same change (AD-8).

**Ask First:** None anticipated.

**Never:** No backend logout endpoint. No "remember me" or cross-tab logout broadcast. Do not fold in guarding `/login`/`/register` against an already-authenticated visitor — that is a separate, already-tracked action item, deliberately out of scope here.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Authenticated, any role | Valid token in storage | Nav shows Logout, not Register/Login; Health still shown | N/A |
| Unauthenticated | No token, or malformed/invalid-role token | Nav shows Register/Login/Health, no Logout | N/A |
| Click Logout | Authenticated, click Logout | Token cleared, redirected to `/login`, nav flips to unauthenticated state immediately, no reload | N/A |

</frozen-after-approval>

## Code Map

- `frontend/src/api/authToken.ts` -- add `export function clearToken(): void` (`localStorage.removeItem(TOKEN_STORAGE_KEY)`) -- symmetric with the existing `saveToken`/`getToken`.
- `frontend/src/app/RootLayout.tsx` -- import `useNavigate` from `react-router`, `clearToken` from `../api/authToken`, `getCurrentRole` from `./roleHome`; branch the nav on `getCurrentRole()`; add a `handleLogout` that calls `clearToken()` then `navigate('/login', { replace: true })` -- this is the exact file the component's own comment already named as needing this change.
- `frontend/src/i18n/bg.json`, `frontend/src/i18n/en.json` -- add `app.nav.logout` ("Изход" / "Logout") beside the three existing `app.nav.*` keys -- required in both catalogs together, enforced by `LanguageToggle.test.tsx`'s existing key-parity test.
- `frontend/src/app/RootLayout.test.tsx` -- new. Follow `router.test.tsx`'s pattern (`createMemoryRouter(routes, ...)` + `RouterProvider`, `seedToken()` from `../test/seedToken`) to cover the I/O matrix.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/api/authToken.ts` -- add `clearToken()` -- gives `RootLayout` a way to end the session.
- [x] `frontend/src/app/RootLayout.tsx` -- auth-aware nav + logout handler -- closes the gap the epic flagged.
- [x] `frontend/src/i18n/bg.json`, `en.json` -- add `app.nav.logout` -- AD-8 contract.
- [x] `frontend/src/app/RootLayout.test.tsx` -- new suite covering the I/O matrix -- proves the AC.

**Acceptance Criteria:**
- Given I am logged in as any role, when I view any screen, then I see Logout in the nav in place of Register/Login (Health unchanged).
- Given I click Logout, when it completes, then my token is cleared, I land on `/login`, and the nav reflects the logged-out state immediately, with no reload.
- Given I am not logged in, when I view the nav, then Register/Login/Health are exactly as before and no Logout control appears.

## Design Notes

Mirrors `RoleGuard.tsx`'s existing pattern of calling `getCurrentRole()` directly during render, no memoization:

```tsx
const currentRole = getCurrentRole();

function handleLogout() {
  clearToken();
  navigate('/login', { replace: true });
}

// in the nav:
{currentRole ? (
  <button type="button" onClick={handleLogout}>{t('app.nav.logout')}</button>
) : (
  <>
    <Link to="/register">{t('app.nav.register')}</Link>
    <Link to="/login">{t('app.nav.login')}</Link>
  </>
)}
<Link to="/health">{t('app.nav.health')}</Link>
```

## Verification

**Commands:**
- `cd frontend; npm run typecheck; npm run build; npm test` -- all clean, including the new `RootLayout.test.tsx` suite.

**Manual checks:**
- `npm run dev`, log in as CLIENT, confirm Logout appears in place of Register/Login; click it, confirm redirect to `/login` with Register/Login visible again and no console errors.

## Suggested Review Order

**Auth-aware nav logic**

- Entry point: derives auth state fresh from `getCurrentRole()` on every render, no tracked state.
  [`RootLayout.tsx:33`](../../frontend/src/app/RootLayout.tsx#L33)

- Logout handler: clears the token, then client-side navigates — no backend call (AD-3).
  [`RootLayout.tsx:35`](../../frontend/src/app/RootLayout.tsx#L35)

- Conditional render swaps Register/Login for Logout; Health stays unconditional either way.
  [`RootLayout.tsx:45`](../../frontend/src/app/RootLayout.tsx#L45)

**Token storage**

- `clearToken()`: symmetric counterpart to `saveToken`/`getToken`, a plain `localStorage.removeItem`.
  [`authToken.ts:24`](../../frontend/src/api/authToken.ts#L24)

**i18n (AD-8: both catalogs ship together)**

- New `app.nav.logout` key, Bulgarian.
  [`bg.json:7`](../../frontend/src/i18n/bg.json#L7)

- New `app.nav.logout` key, English.
  [`en.json:7`](../../frontend/src/i18n/en.json#L7)

**Tests**

- New suite covering the full I/O matrix: logged-out nav, logged-in nav per role, and the logout click flow.
  [`RootLayout.test.tsx:28`](../../frontend/src/app/RootLayout.test.tsx#L28)
