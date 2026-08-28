// The frontend's single source of truth for "which roles exist" and "where
// does each role live" (Story 2.2). Role is read only from the decoded JWT
// (`api/authToken.ts`) — never threaded through props or global state.
//
// The frontend role check is a UX convenience, never a security boundary
// (AD-4): this module decides where to *send* a user, not what they may
// access. Backend endpoints enforce access independently (Story 1.4 / 2.4).

export const ROLES = ['CLIENT', 'AGENT', 'LIQUIDATOR', 'ADMINISTRATOR'] as const;
export type Role = (typeof ROLES)[number];

/**
 * Runtime type guard for the `role` claim carried by a decoded JWT, whose
 * type is a raw `string` (a decode is not a validation). Callers own the
 * failure path for anything that fails this check — see `LoginForm`.
 */
export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value);
}

// `Record<Role, string>` makes the compiler reject this file if a role is
// left unmapped — there is no `string`-typed lookup and no default path.
const ROLE_HOME: Record<Role, string> = {
  CLIENT: '/',
  AGENT: '/agent',
  LIQUIDATOR: '/liquidator',
  ADMINISTRATOR: '/administrator',
};

/**
 * Total function over a typed {@link Role}: every role has a home route.
 * There is deliberately no `null`/unknown branch — that case never reaches
 * here because every call site runs {@link isRole} first and owns the
 * controlled failure. Story 2.4's route guard reuses this to compute
 * "where does this user belong" with no duplicated table.
 */
export function roleHome(role: Role): string {
  return ROLE_HOME[role];
}
