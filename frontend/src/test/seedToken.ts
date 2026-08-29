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
 */
export function seedToken(role: Role): void {
  const payload = btoa(JSON.stringify({ sub: 'user-1', role }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  saveToken(`header.${payload}.signature`);
}
