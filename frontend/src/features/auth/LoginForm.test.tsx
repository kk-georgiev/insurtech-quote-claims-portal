import { StrictMode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from '../../app/router';
import { getToken } from '../../api/authToken';
import { ApiRequestError, apiFetch } from '../../api/client';
import { ROLES, roleHome } from '../../app/roleHome';

// `apiFetch` is the only seam mocked — no backend, no network. The rest of
// `api/client.ts` (notably `ApiRequestError`) stays real. `vitest.config.ts`
// sets `mockReset: true`, so the fn is cleared before every test.
vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

const GENERIC_ERROR = 'Something went wrong. Please try again.';
const INVALID_CREDENTIALS = 'Incorrect email or password.';
const EMAIL = 'someone@example.com';
const PASSWORD = 'DemoPass123!';

/** Builds a well-formed `header.payload.signature` JWT string for `role`. */
function makeToken(role: string): string {
  const encode = (value: unknown) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'user-1', role })}.sig`;
}

function renderLogin() {
  const router = createMemoryRouter(routes, { initialEntries: ['/login'] });
  render(
    <StrictMode>
      <RouterProvider router={router} />
    </StrictMode>,
  );
  return { router, user: userEvent.setup() };
}

const emailField = () => screen.getByLabelText('Email');
const passwordField = () => screen.getByLabelText('Password');
const loginButton = () => screen.getByRole('button', { name: 'Log in' });

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
  await user.type(emailField(), EMAIL);
  await user.type(passwordField(), PASSWORD);
  await user.click(loginButton());
}

describe('LoginForm role-based post-login routing', () => {
  it.each([...ROLES])(
    'navigates a successful %s login to its own route and stores the token',
    async (role) => {
      const token = makeToken(role);
      mockedApiFetch.mockResolvedValue({ token });

      const { router, user } = renderLogin();
      await fillAndSubmit(user);

      await waitFor(() => {
        expect(router.state.location.pathname).toBe(roleHome(role));
      });
      expect(getToken()).toBe(token);
      // Post-auth redirect replaces `/login` rather than stacking on it,
      // so Back doesn't return to the login form.
      expect(router.state.historyAction).toBe('REPLACE');
    },
  );

  it('treats an undecodable token as a failed login and leaves the form editable', async () => {
    mockedApiFetch.mockResolvedValue({ token: 'not-a-jwt' });

    const { router, user } = renderLogin();
    await fillAndSubmit(user);

    expect(await screen.findByTestId('login-error')).toHaveTextContent(GENERIC_ERROR);
    expect(router.state.location.pathname).toBe('/login');
    expect(getToken()).toBeNull();
    expect(loginButton()).toBeEnabled();
  });

  it('treats a token with an unrecognized role as a failed login and leaves the form editable', async () => {
    mockedApiFetch.mockResolvedValue({ token: makeToken('SUPERUSER') });

    const { router, user } = renderLogin();
    await fillAndSubmit(user);

    expect(await screen.findByTestId('login-error')).toHaveTextContent(GENERIC_ERROR);
    expect(router.state.location.pathname).toBe('/login');
    expect(getToken()).toBeNull();
    expect(loginButton()).toBeEnabled();
  });

  it('treats a 200 with no token field as a failed login', async () => {
    mockedApiFetch.mockResolvedValue({} as { token: string });

    const { router, user } = renderLogin();
    await fillAndSubmit(user);

    expect(await screen.findByTestId('login-error')).toHaveTextContent(GENERIC_ERROR);
    expect(router.state.location.pathname).toBe('/login');
    expect(getToken()).toBeNull();
  });

  it('recovers from a controlled failure: retrying with a valid token navigates, credentials preserved', async () => {
    mockedApiFetch.mockResolvedValueOnce({ token: 'not-a-jwt' });

    const { router, user } = renderLogin();
    await fillAndSubmit(user);

    expect(await screen.findByTestId('login-error')).toHaveTextContent(GENERIC_ERROR);
    // The typed credentials survived the failure — no re-entry needed.
    expect(emailField()).toHaveValue(EMAIL);
    expect(passwordField()).toHaveValue(PASSWORD);
    expect(loginButton()).toBeEnabled();

    mockedApiFetch.mockResolvedValueOnce({ token: makeToken('AGENT') });
    await user.click(loginButton());

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/agent');
    });
    expect(getToken()).toBe(makeToken('AGENT'));
  });

  it('leaves the invalid-credentials path unchanged', async () => {
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 401', 401, 'AUTH_INVALID_CREDENTIALS'),
    );

    const { router, user } = renderLogin();
    await fillAndSubmit(user);

    expect(await screen.findByTestId('login-error')).toHaveTextContent(INVALID_CREDENTIALS);
    expect(router.state.location.pathname).toBe('/login');
    expect(getToken()).toBeNull();
    expect(loginButton()).toBeEnabled();
  });
});
