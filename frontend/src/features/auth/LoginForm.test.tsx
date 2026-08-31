import { StrictMode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from '../../app/router';
import { getToken, decodeToken } from '../../api/authToken';
import { ApiRequestError, apiFetch } from '../../api/client';
import { ROLES, roleHome } from '../../app/roleHome';
import { seedToken } from '../../test/seedToken';
import bg from '../../i18n/bg.json';
import en from '../../i18n/en.json';
import i18n from '../../i18n';

// `apiFetch` is the only seam mocked — no backend, no network. The rest of
// `api/client.ts` (notably `ApiRequestError`) stays real. `vitest.config.ts`
// sets `mockReset: true`, so the fn is cleared before every test.
vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

const GENERIC_ERROR = bg.errors.generic;
const INVALID_CREDENTIALS = bg.errors.codes.AUTH_INVALID_CREDENTIALS;
const EMAIL = 'someone@example.com';
const PASSWORD = 'DemoPass123!';

/**
 * Builds a well-formed `header.payload.signature` JWT string for `role`.
 * `extra` (Story 7.1) merges additional payload claims, e.g. `{ exp: ... }`.
 */
function makeToken(role: string, extra: Record<string, unknown> = {}): string {
  const encode = (value: unknown) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'user-1', role, ...extra })}.sig`;
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

const emailField = () => screen.getByLabelText(bg.auth.login.email);
const passwordField = () => screen.getByLabelText(bg.auth.login.password);
const loginButton = () => screen.getByRole('button', { name: bg.auth.login.submit });

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

  it('treats an already-expired token as a failed login (Story 7.1) and never persists it', async () => {
    // Degenerate/unlikely (clock skew, a very short-lived test token) but
    // now reachable through the same getCurrentRole path every other check
    // uses - the token is well-formed and its role is real, only `exp` is
    // already in the past.
    mockedApiFetch.mockResolvedValue({
      token: makeToken('CLIENT', { exp: Math.floor(Date.now() / 1000) - 60 }),
    });

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

  it('renders field-level errors from a bean-validation failure next to each offending input', async () => {
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 400', 400, 'SHARED_VALIDATION_ERROR', [
        { field: 'email', message: 'must be a well-formed email address' },
      ]),
    );

    const { user } = renderLogin();
    // Before submitting, neither field carries error-describing aria
    // attributes — no error has been set yet.
    expect(emailField()).not.toHaveAttribute('aria-invalid');
    expect(emailField()).not.toHaveAttribute('aria-describedby');
    expect(passwordField()).not.toHaveAttribute('aria-invalid');
    expect(passwordField()).not.toHaveAttribute('aria-describedby');

    await fillAndSubmit(user);

    expect(await screen.findByText(bg.auth.login.fieldErrors.email)).toBeInTheDocument();
    expect(screen.queryByTestId('login-error')).not.toBeInTheDocument();
    expect(loginButton()).toBeEnabled();

    expect(emailField()).toHaveAttribute('aria-invalid', 'true');
    expect(emailField()).toHaveAttribute('aria-describedby', 'login-email-error');
    expect(screen.getByText(bg.auth.login.fieldErrors.email).id).toBe('login-email-error');
    // password had no field error - neither attribute is present.
    expect(passwordField()).not.toHaveAttribute('aria-invalid');
    expect(passwordField()).not.toHaveAttribute('aria-describedby');
  });

  it('sends only one request when the user submits twice before the response resolves', async () => {
    let resolveFetch: (value: { token: string }) => void;
    mockedApiFetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve;
        }),
    );

    const { user } = renderLogin();
    await user.type(emailField(), EMAIL);
    await user.type(passwordField(), PASSWORD);
    // Two rapid clicks on the same element before the first request
    // settles - only one call should go out (spec: double-submit guard is
    // a synchronous phase check, not reliant on `disabled` having
    // committed to the DOM yet). Re-querying by accessible name for the
    // second click would fail once the label switches to "Logging in…".
    const button = loginButton();
    await user.click(button);
    await user.click(button);

    expect(mockedApiFetch).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveFetch!({ token: makeToken('AGENT') });
    });
  });

  it('allows a second submission once the first request has settled', async () => {
    mockedApiFetch.mockRejectedValueOnce(
      new ApiRequestError('Request failed with status 401', 401, 'AUTH_INVALID_CREDENTIALS'),
    );
    mockedApiFetch.mockResolvedValueOnce({ token: makeToken('AGENT') });

    const { user } = renderLogin();
    await fillAndSubmit(user);
    expect(await screen.findByTestId('login-error')).toHaveTextContent(INVALID_CREDENTIALS);
    expect(loginButton()).toBeEnabled();

    // The guard only blocks a submit while the previous one is still
    // pending - once the first request has settled (here, with a
    // failure), a genuine second submission must go through.
    await user.click(loginButton());

    expect(mockedApiFetch).toHaveBeenCalledTimes(2);
  });

  it('does not save the token after unmounting mid-submit', async () => {
    let resolveFetch: (value: { token: string }) => void;
    mockedApiFetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve;
        }),
    );

    const { user } = renderLogin();
    await user.type(emailField(), EMAIL);
    await user.type(passwordField(), PASSWORD);
    await user.click(loginButton());

    cleanup();
    resolveFetch!({ token: makeToken('AGENT') });
    await new Promise((resolve) => setTimeout(resolve, 0));

    // The response would decode to a valid AGENT token, so if the
    // `cancelledRef` guard were missing, `saveToken` would fire here and
    // `getToken()` would return it. It doesn't - proving the guarded side
    // effect never ran post-unmount (React 18 no longer warns on a
    // setState-after-unmount, so that assertion proved nothing; this one
    // proves the guard actually did something).
    expect(getToken()).toBeNull();
  });

  it('identity swap: logging in as a different role than the expired one already stored lands on the new role\'s home, with no stale role left over (Epic 2 retro item 13)', async () => {
    // A live session's own /login is unreachable under GuestGuard (Story
    // 7.2) - this scenario is only real once the old session has actually
    // died, which is exactly when GuestGuard lets the visitor back in to
    // log in again, possibly as someone else on the same browser.
    seedToken('AGENT', { exp: Math.floor(Date.now() / 1000) - 60 });
    mockedApiFetch.mockResolvedValue({ token: makeToken('LIQUIDATOR') });

    const { router, user } = renderLogin();
    // GuestGuard already let this render through (the stored AGENT token is
    // expired, so getCurrentRole reads it as no session) - the form is on
    // screen to submit against, proving the old token didn't block re-entry.
    await fillAndSubmit(user);

    expect(await screen.findByTestId('liquidator-shell')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(roleHome('LIQUIDATOR'));
    // The stored token now decodes to the new identity, not a merge of the
    // two and not the old one surviving alongside it.
    const stored = getToken();
    expect(stored).not.toBeNull();
    expect(decodeToken(stored!)?.role).toBe('LIQUIDATOR');
  });
});

describe('LoginForm error messages follow a language change', () => {
  // Regression: the form used to store the *resolved* string in state, so an
  // error already on screen stayed in the language it was raised in until the
  // user resubmitted. The failure is now held language-neutral and resolved
  // during render.
  it('re-translates a visible form-level error without resubmitting', async () => {
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 401', 401, 'AUTH_INVALID_CREDENTIALS'),
    );
    const { user } = renderLogin();
    await fillAndSubmit(user);

    expect(await screen.findByTestId('login-error')).toHaveTextContent(
      bg.errors.codes.AUTH_INVALID_CREDENTIALS,
    );

    await act(async () => {
      await i18n.changeLanguage('en');
    });

    expect(screen.getByTestId('login-error')).toHaveTextContent(
      en.errors.codes.AUTH_INVALID_CREDENTIALS,
    );
    // Exactly one request: the switch re-rendered, it did not resubmit.
    expect(mockedApiFetch).toHaveBeenCalledTimes(1);
  });

  it('re-translates a visible field-level error without resubmitting', async () => {
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 400', 400, 'SHARED_VALIDATION_ERROR', [
        { field: 'email', message: 'must be a well-formed email address' },
      ]),
    );
    const { user } = renderLogin();
    await fillAndSubmit(user);

    expect(await screen.findByText(bg.auth.login.fieldErrors.email)).toBeInTheDocument();

    await act(async () => {
      await i18n.changeLanguage('en');
    });

    expect(screen.getByText(en.auth.login.fieldErrors.email)).toBeInTheDocument();
    expect(screen.queryByText(bg.auth.login.fieldErrors.email)).not.toBeInTheDocument();
    // Still wired to the input it describes after the re-render.
    expect(screen.getByText(en.auth.login.fieldErrors.email).id).toBe('login-email-error');
    expect(mockedApiFetch).toHaveBeenCalledTimes(1);
  });
});
