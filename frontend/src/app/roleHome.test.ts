import { afterEach, describe, expect, it } from 'vitest';
import { ROLES, isRole, roleHome, getCurrentRole } from './roleHome';
import { saveToken, clearToken } from '../api/authToken';

/** Builds the same base64url-encoded unsigned-JWT shape `seedToken` does, without pulling in that module's Role-narrowed signature. */
function tokenWithPayload(payload: Record<string, unknown>): string {
  const encoded = btoa(JSON.stringify(payload))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `header.${encoded}.signature`;
}

describe('roleHome', () => {
  it('maps each role to its own route', () => {
    expect(roleHome('CLIENT')).toBe('/');
    expect(roleHome('AGENT')).toBe('/agent');
    expect(roleHome('LIQUIDATOR')).toBe('/liquidator');
    expect(roleHome('ADMINISTRATOR')).toBe('/administrator');
  });

  it('gives every role a distinct destination', () => {
    const homes = ROLES.map((role) => roleHome(role));
    expect(new Set(homes).size).toBe(ROLES.length);
  });
});

describe('isRole', () => {
  it('accepts each known role string', () => {
    for (const role of ROLES) {
      expect(isRole(role)).toBe(true);
    }
  });

  it('rejects unknown or wrongly-cased strings', () => {
    expect(isRole('SUPERUSER')).toBe(false);
    expect(isRole('client')).toBe(false);
    expect(isRole('Agent')).toBe(false);
    expect(isRole('')).toBe(false);
  });

  it('rejects non-string inputs', () => {
    expect(isRole(null)).toBe(false);
    expect(isRole(undefined)).toBe(false);
    expect(isRole(42)).toBe(false);
    expect(isRole(true)).toBe(false);
    expect(isRole({ role: 'CLIENT' })).toBe(false);
    expect(isRole(['CLIENT'])).toBe(false);
  });
});

describe('getCurrentRole (Story 7.1, FR-M3-11)', () => {
  afterEach(() => {
    clearToken();
  });

  it('returns null when no token is stored', () => {
    expect(getCurrentRole()).toBeNull();
  });

  it('returns the role for a token with no exp claim at all (never-expiring, matches every token seeded before this option existed)', () => {
    saveToken(tokenWithPayload({ sub: 'user-1', role: 'CLIENT' }));
    expect(getCurrentRole()).toBe('CLIENT');
  });

  it('returns the role for a token whose exp is still in the future', () => {
    const future = Math.floor(Date.now() / 1000) + 3600;
    saveToken(tokenWithPayload({ sub: 'user-1', role: 'CLIENT', exp: future }));
    expect(getCurrentRole()).toBe('CLIENT');
  });

  it('returns the role for a token valid at exactly this instant (boundary)', () => {
    // exp is UNIX *seconds*; round down so the millisecond comparison
    // inside getCurrentRole cannot land a moment past this second's exp
    // between building the token and asserting on it.
    const now = Math.floor(Date.now() / 1000);
    saveToken(tokenWithPayload({ sub: 'user-1', role: 'CLIENT', exp: now + 1 }));
    expect(getCurrentRole()).toBe('CLIENT');
  });

  it('returns null for a token whose exp has already passed', () => {
    const past = Math.floor(Date.now() / 1000) - 3600;
    saveToken(tokenWithPayload({ sub: 'user-1', role: 'CLIENT', exp: past }));
    expect(getCurrentRole()).toBeNull();
  });

  it('returns null for an undecodable token', () => {
    saveToken('not-a-real-jwt');
    expect(getCurrentRole()).toBeNull();
  });

  it('returns null for a token with an unrecognized role, even if unexpired', () => {
    const future = Math.floor(Date.now() / 1000) + 3600;
    saveToken(tokenWithPayload({ sub: 'user-1', role: 'SUPERADMIN', exp: future }));
    expect(getCurrentRole()).toBeNull();
  });

  it('accepts an explicit token argument instead of reading storage (LoginForm validating a just-received token before saving it)', () => {
    const future = Math.floor(Date.now() / 1000) + 3600;
    const freshToken = tokenWithPayload({ sub: 'user-1', role: 'AGENT', exp: future });
    // Nothing saved to storage - proves the explicit-argument path is used,
    // not a fallback to getToken().
    expect(getCurrentRole(freshToken)).toBe('AGENT');
  });

  it('rejects an explicit token argument that is already expired, the same as a stored one', () => {
    const past = Math.floor(Date.now() / 1000) - 60;
    const expiredToken = tokenWithPayload({ sub: 'user-1', role: 'AGENT', exp: past });
    expect(getCurrentRole(expiredToken)).toBeNull();
  });
});
