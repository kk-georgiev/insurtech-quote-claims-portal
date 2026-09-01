import { Navigate, Outlet } from 'react-router';
import { getCurrentRole, roleHome } from './roleHome';

/**
 * The inverse of {@link RoleGuard} (Story 7.2, FR-M3-13): gates `/login` and
 * `/register` so a visitor who already has a live session doesn't land back
 * on a sign-in form for the identity they are already using.
 *
 * Role comes from the same {@link getCurrentRole} every other guard reads —
 * no new expiry/validity logic here. That means Story 7.1's "no valid role"
 * contract already covers the case this story needs distinguished from a
 * genuinely logged-in visitor: an anonymous visitor, and one whose token
 * `getCurrentRole` treats as dead (expired, malformed, unrecognized role),
 * both pass straight through to the real login/register screen — only a
 * *live* session gets redirected away from it.
 *
 * - A valid, current role -> redirect to that role's own home via
 *   `roleHome()`, replacing history so "back" doesn't return here.
 * - No valid role -> render the nested route (`LoginForm`/`RegisterForm`)
 *   via `<Outlet />`, unchanged from before this story.
 */
export function GuestGuard() {
  const current = getCurrentRole();

  if (current) return <Navigate to={roleHome(current)} replace />;

  return <Outlet />;
}
