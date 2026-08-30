import { Navigate, Outlet } from 'react-router';
import { getCurrentRole, roleHome, type Role } from './roleHome';

/**
 * Route-wrapper that gates one shell route behind a single {@link Role}
 * (Story 2.4). Reused four times in `router.tsx` with a different `role`
 * prop each time — never per-screen ad hoc checks (AD-10).
 *
 * Role comes only from the decoded JWT via {@link getCurrentRole} — never
 * from props or re-derived logic. This is a UX convenience, not the real
 * security boundary: backend authorization (Story 1.4) independently
 * rejects the wrong role server-side regardless of what this component does
 * (AD-4).
 *
 * - No valid role (logged out / unparseable token) -> redirect to `/login`.
 * - A valid role on the wrong route -> redirect to that role's own home via
 *   `roleHome()`.
 * - The matching role -> render the nested route via `<Outlet />`.
 *
 * `replace` keeps the blocked URL out of browser history so "back" doesn't
 * return the visitor to a route they were just bounced from.
 */
export function RoleGuard({ role }: { role: Role }) {
  const current = getCurrentRole();

  if (current === role) return <Outlet />;

  return <Navigate to={current ? roleHome(current) : '/login'} replace />;
}
