import { saveToken } from '../api/authToken';
import type { Role } from '../app/roleHome';

/**
 * Seeds a fake unsigned JWT (`header.payload.signature`) carrying `role` in
 * its payload, via the real `saveToken` — the same storage `RoleGuard`
 * reads through `getCurrentRole`/`decodeToken`. `decodeToken` never
 * verifies the signature (Story 1.3), so "header"/"signature" are inert
 * placeholders; only the base64url payload segment matters.
 *
 * Shared by `app/router.test.tsx` and `features/shells/shells.test.tsx`
 * (Story 2.4) — both suites need the identical encoding to seed a token
 * `RoleGuard`/`getCurrentRole` will accept.
 *
 * `exp` (Story 7.1, FR-M3-11) is UNIX seconds, matching the real backend's
 * claim — omit it (the default) for a token that never expires, matching
 * every test written before this option existed. Pass a past value to seed
 * an already-expired token.
 */
export function seedToken(role: Role, options?: { exp?: number }): void {
  const payload: Record<string, unknown> = { sub: 'user-1', role };
  if (options?.exp !== undefined) {
    payload.exp = options.exp;
  }
  const encodedPayload = btoa(JSON.stringify(payload))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  saveToken(`header.${encodedPayload}.signature`);
}
