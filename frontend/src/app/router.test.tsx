import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from './router';
import { ROLES, roleHome, type Role } from './roleHome';
import { saveToken } from '../api/authToken';
import { apiFetch } from '../api/client';
import { seedToken } from '../test/seedToken';

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
    expect(screen.getByRole('heading', { name: 'Get a quote' })).toBeInTheDocument();
  });

  it('serves the backend health round-trip at /health', async () => {
    mockedApiFetch.mockResolvedValue({ status: 'UP' } as { status: string });
    renderAt('/health');
    expect(await screen.findByTestId('health-status')).toHaveTextContent('Backend is reachable.');
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
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it.each([...ROLES])('malformed token on %s\'s route redirects to /login', async (role) => {
    saveToken(MALFORMED_TOKEN);
    const router = renderAt(roleHome(role));
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
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
      expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
      expect(router.state.location.pathname).toBe('/login');
    },
  );
});
