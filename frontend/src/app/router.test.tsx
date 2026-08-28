import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from './router';
import { ROLES, roleHome } from './roleHome';
import { apiFetch } from '../api/client';

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

describe('route table', () => {
  // Closes the loop between `roleHome` and the router: renaming a shell
  // route in `router.tsx` alone would make every login for that role 404
  // while unit tests stayed green.
  it.each([...ROLES])(
    'roleHome(%s) resolves to a route that renders that role\'s shell',
    async (role) => {
      renderAt(roleHome(role));
      expect(await screen.findByTestId(`${role.toLowerCase()}-shell`)).toBeInTheDocument();
    },
  );

  it('serves the client shell at index (/), not the health screen', async () => {
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
