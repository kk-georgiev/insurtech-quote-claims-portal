// JWT storage + client-side payload decoding (Story 1.3). `localStorage` is
// the simplest option that persists across reloads/tabs - hardening this
// (httpOnly cookies, XSS mitigation) is explicitly out of scope this
// milestone (see spec Design Notes). `decodeToken` never verifies the
// signature - that's the backend's job (Story 1.4's validation filter) -
// it only reads the payload back out for display purposes (e.g. showing
// the role after login). No automatic `Authorization` header attachment
// lives here yet (that's `client.ts`, Story 1.4 - nothing to test it
// against until the validation filter exists).

const TOKEN_STORAGE_KEY = 'motorinsurance.auth.token';

/** Persists the JWT returned by `POST /api/v1/auth/login`. */
export function saveToken(token: string): void {
  localStorage.setItem(TOKEN_STORAGE_KEY, token);
}

/** Returns the stored JWT, or `null` if none is saved (never logged in / logged out). */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

/**
 * Shape of this backend's JWT payload (`auth/application/JwtService.java`):
 * exactly `sub` (user id) and `role` (Role enum name), plus the standard
 * `iat`/`exp` claims every JJWT-issued token carries.
 */
export interface DecodedToken {
  sub: string;
  role: string;
  iat?: number;
  exp?: number;
}

/**
 * Decodes a JWT's payload segment client-side, WITHOUT verifying its
 * signature - display purposes only. Returns `null` if `token` isn't a
 * well-formed `header.payload.signature` string or its payload isn't valid
 * base64url JSON; callers should treat that as "nothing to show", never as
 * a reason to trust or distrust the token itself.
 */
export function decodeToken(token: string): DecodedToken | null {
  const parts = token.split('.');
  if (parts.length !== 3) return null;

  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const json = decodeURIComponent(
      atob(padded)
        .split('')
        .map((char) => '%' + char.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as DecodedToken;
  } catch {
    return null;
  }
}
