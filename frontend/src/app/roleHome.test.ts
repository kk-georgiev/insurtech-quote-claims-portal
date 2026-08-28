import { describe, expect, it } from 'vitest';
import { ROLES, isRole, roleHome } from './roleHome';

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
