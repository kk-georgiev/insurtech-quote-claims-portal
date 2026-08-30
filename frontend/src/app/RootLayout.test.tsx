import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from './router';
import { ROLES } from './roleHome';
import { getToken } from '../api/authToken';
import { apiFetch } from '../api/client';
import { seedToken } from '../test/seedToken';
import bg from '../i18n/bg.json';

// `/health` mounts `HealthStatus`, whose effect calls `apiFetch` on load.
// Mocked for the same reason `router.test.tsx` mocks it: this suite renders
// the real route table and must never touch the network.
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

describe('RootLayout nav — auth awareness (Story 2.5)', () => {
  it('shows Register/Login/Health and no Logout when logged out', async () => {
    renderAt('/login');

    expect(await screen.findByRole('heading', { name: bg.app.title })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: bg.app.nav.register })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: bg.app.nav.login })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: bg.app.nav.health })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: bg.app.nav.logout })).toBeNull();
  });

  it.each([...ROLES])('shows Logout in place of Register/Login for a logged-in %s, Health unchanged', async (role) => {
    seedToken(role);
    renderAt(role === 'CLIENT' ? '/' : `/${role.toLowerCase()}`);

    expect(await screen.findByTestId(`${role.toLowerCase()}-shell`)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: bg.app.nav.logout })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: bg.app.nav.health })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: bg.app.nav.register })).toBeNull();
    expect(screen.queryByRole('link', { name: bg.app.nav.login })).toBeNull();
  });

  it('clears the token, redirects to /login, and flips the nav immediately on Logout click, with no reload', async () => {
    mockedApiFetch.mockResolvedValue({ status: 'UP' } as { status: string });
    const user = userEvent.setup();
    seedToken('CLIENT');
    const router = renderAt('/');

    expect(await screen.findByTestId('client-shell')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: bg.app.nav.logout }));

    expect(await screen.findByRole('heading', { name: bg.auth.login.heading })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(getToken()).toBeNull();
    expect(screen.getByRole('link', { name: bg.app.nav.register })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: bg.app.nav.login })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: bg.app.nav.logout })).toBeNull();
  });
});
