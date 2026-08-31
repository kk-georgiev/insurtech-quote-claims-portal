// The frontend's single source of truth for "which roles exist" and "where
// does each role live" (Story 2.2). Role is read only from the decoded JWT
// (`api/authToken.ts`) — never threaded through props or global state.
//
// The frontend role check is a UX convenience, never a security boundary
// (AD-4): this module decides where to *send* a user, not what they may
// access. Backend endpoints enforce access independently (Story 1.4 / 2.4).

import { decodeToken, getToken } from '../api/authToken';

export const ROLES = ['CLIENT', 'AGENT', 'LIQUIDATOR', 'ADMINISTRATOR'] as const;
export type Role = (typeof ROLES)[number];

/** The three non-CLIENT roles — the staff placeholder shells (Story 2.3). */
export type StaffRole = Exclude<Role, 'CLIENT'>;

export const STAFF_ROLES: readonly StaffRole[] = ROLES.filter(
  (role): role is StaffRole => role !== 'CLIENT',
);

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

/**
 * The current visitor's role, derived only from the stored JWT
 * (`getToken` + `decodeToken`, `api/authToken.ts`) — never from props or
 * global state (Story 2.4, AD-10). Returns `null` for "no valid role":
 * no token saved, an unparseable/malformed token, a token whose `role`
 * claim isn't one of {@link ROLES}, or — Story 7.1, FR-M3-11 — a token
 * whose `exp` claim has already passed. Callers (`RoleGuard`, `RootLayout`)
 * treat `null` as "not logged in" uniformly, so an expired-but-still-stored
 * token reads as a dead session everywhere this is called, not just on the
 * next navigation. This reads `exp` only — it never verifies the token's
 * signature, which stays the backend's job (`auth.config.JwtAuthenticationFilter`);
 * a request against an expired-but-signature-valid token is independently
 * rejected server-side regardless of what this function returns (AD-4).
 *
 * Takes an explicit `token` (defaulting to the stored one) so `LoginForm`
 * can validate a just-received token *before* deciding whether to persist
 * it, through this same one code path, instead of re-implementing
 * decode+`isRole`+expiry inline (Epic 2 retro item 14, Epic 3 retro item 35).
 */
export function getCurrentRole(token: string | null = getToken()): Role | null {
  if (!token) return null;

  const decoded = decodeToken(token);
  if (!decoded || !isRole(decoded.role)) return null;

  // `exp` is UNIX seconds (JWT spec); Date.now() is milliseconds. A token
  // with no `exp` claim at all is treated as never-expiring here — this
  // backend's tokens always carry one (JwtService), so an absent `exp`
  // only ever occurs in a forged/malformed token, which the signature
  // check server-side would reject anyway.
  if (typeof decoded.exp === 'number' && decoded.exp * 1000 <= Date.now()) {
    return null;
  }

  return decoded.role;
}
