import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from './router';
import { ROLES, roleHome, type Role } from './roleHome';
import { saveToken } from '../api/authToken';
import { apiFetch } from '../api/client';
import { seedToken } from '../test/seedToken';
import bg from '../i18n/bg.json';

// `/health` mounts `HealthStatus`, whose effect calls `apiFetch` on load.
// Mock it (spec: "no backend needed") so the suite never touches the
// network. `vitest.config.ts` sets `mockReset: true`.
vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

// Guaranteed-unparseable: `#` is not a base64 character, so `decodeToken`'s
// `atob` call throws and it returns `null` — "malformed/undecodable token"
// from the I/O matrix, treated the same as no token at all.
const MALFORMED_TOKEN = 'header.###.signature';

// A syntactically valid, decodable token whose `role` claim is not one of
// `ROLES`. `seedToken` is typed to only accept a valid `Role`, so this is
// built by hand — it exercises `getCurrentRole`'s `isRole(decoded.role)`
// failure branch, distinct from `MALFORMED_TOKEN`'s `atob`-throws branch.
const INVALID_ROLE_TOKEN = `header.${btoa(JSON.stringify({ sub: 'user-1', role: 'SUPERADMIN' }))
  .replace(/\+/g, '-')
  .replace(/\//g, '_')
  .replace(/=+$/, '')}.signature`;

describe('route table', () => {
  // Closes the loop between `roleHome` and the router: renaming a shell
  // route in `router.tsx` alone would make every login for that role 404
  // while unit tests stayed green.
  it.each([...ROLES])(
    "roleHome(%s) resolves to a route that renders that role's shell",
    async (role) => {
      seedToken(role);
      renderAt(roleHome(role));
      expect(await screen.findByTestId(`${role.toLowerCase()}-shell`)).toBeInTheDocument();
    },
  );

  it('serves the client shell at index (/), not the health screen', async () => {
    seedToken('CLIENT');
    renderAt('/');
    expect(await screen.findByTestId('client-shell')).toBeInTheDocument();
    expect(screen.queryByTestId('health-status')).toBeNull();
    // Guards against `<QuoteForm />` being dropped from `ClientShell` -
    // asserting the wrapper testid alone wouldn't catch that regression.
    expect(screen.getByRole('heading', { name: bg.quote.form.heading })).toBeInTheDocument();
  });

  it('serves the backend health round-trip at /health', async () => {
    mockedApiFetch.mockResolvedValue({ status: 'UP' } as { status: string });
    renderAt('/health');
    expect(await screen.findByTestId('health-status')).toHaveTextContent(bg.app.health.reachable);
  });
});

// Story 2.4: every one of the four shell routes (`/`, `/agent`,
// `/liquidator`, `/administrator`) is nested under a `RoleGuard` instance.
// These cases exercise the full I/O matrix from the spec: own route, every
// ordered wrong-role pair (symmetric), anonymous, and malformed token.
describe('RoleGuard', () => {
  it.each([...ROLES])('%s: own route renders that role\'s shell, no redirect', async (role) => {
    seedToken(role);
    const router = renderAt(roleHome(role));
    expect(await screen.findByTestId(`${role.toLowerCase()}-shell`)).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(roleHome(role));
  });

  const wrongRolePairs: Array<[Role, Role]> = ROLES.flatMap((visitor) =>
    ROLES.filter((owner) => owner !== visitor).map((owner): [Role, Role] => [visitor, owner]),
  );

  it.each(wrongRolePairs)(
    "logged in as %s, visiting %s's route redirects to the visitor's own home",
    async (visitor, owner) => {
      seedToken(visitor);
      const router = renderAt(roleHome(owner));
      expect(await screen.findByTestId(`${visitor.toLowerCase()}-shell`)).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(roleHome(visitor));
    },
  );

  it.each([...ROLES])('anonymous visit to %s\'s route redirects to /login', async (role) => {
    const router = renderAt(roleHome(role));
    expect(await screen.findByRole('heading', { name: bg.auth.login.heading })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it.each([...ROLES])('malformed token on %s\'s route redirects to /login', async (role) => {
    saveToken(MALFORMED_TOKEN);
    const router = renderAt(roleHome(role));
    expect(await screen.findByRole('heading', { name: bg.auth.login.heading })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it.each([...ROLES])(
    "decodable token with an unrecognized role on %s's route redirects to /login",
    async (role) => {
      // Distinct from the malformed-token case above: this token decodes
      // fine, but its `role` claim fails `isRole` — the other branch of
      // `getCurrentRole`'s "no valid role" contract.
      saveToken(INVALID_ROLE_TOKEN);
      const router = renderAt(roleHome(role));
      expect(await screen.findByRole('heading', { name: bg.auth.login.heading })).toBeInTheDocument();
      expect(router.state.location.pathname).toBe('/login');
    },
  );

  it.each([...ROLES])(
    "an expired token (Story 7.1, FR-M3-11) on %s's route redirects to /login, same as no token at all",
    async (role) => {
      // A well-formed, correctly-roled token whose exp has passed - the
      // third branch of getCurrentRole's "no valid role" contract, distinct
      // from malformed and unrecognized-role above.
      seedToken(role, { exp: Math.floor(Date.now() / 1000) - 60 });
      const router = renderAt(roleHome(role));
      expect(await screen.findByRole('heading', { name: bg.auth.login.heading })).toBeInTheDocument();
      expect(router.state.location.pathname).toBe('/login');
    },
  );
});

// Story 7.2, FR-M3-13: the inverse of RoleGuard - an already-authenticated
// visitor is sent away from /login and /register instead of seeing them.
describe('GuestGuard', () => {
  const authScreens: Array<['login' | 'register', string]> = [
    ['login', bg.auth.login.heading],
    ['register', bg.auth.register.heading],
  ];

  it.each(ROLES.flatMap((role) => authScreens.map((screenEntry): [Role, 'login' | 'register', string] => [role, screenEntry[0], screenEntry[1]])))(
    'a logged-in %s visiting /%s is redirected to their own role home, never sees the form',
    async (role, path) => {
      seedToken(role);
      const router = renderAt(`/${path}`);
      expect(await screen.findByTestId(`${role.toLowerCase()}-shell`)).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(roleHome(role));
    },
  );

  it.each(authScreens)('an anonymous visitor still reaches /%s normally', async (path, heading) => {
    const router = renderAt(`/${path}`);
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/${path}`);
  });

  it.each(authScreens)(
    'a visitor whose stored token is already expired (Story 7.1) still reaches /%s normally',
    async (path, heading) => {
      // The case Story 7.1's expiry check and this guard compose to handle:
      // a dead-but-still-stored session must not be mistaken for a live one
      // here, the same as everywhere else getCurrentRole is read.
      seedToken('CLIENT', { exp: Math.floor(Date.now() / 1000) - 60 });
      const router = renderAt(`/${path}`);
      expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(`/${path}`);
    },
  );

  it('/health stays reachable and unaffected for a logged-in visitor', async () => {
    mockedApiFetch.mockResolvedValue({ status: 'UP' } as { status: string });
    seedToken('CLIENT');
    const router = renderAt('/health');
    expect(await screen.findByTestId('health-status')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/health');
  });
});
