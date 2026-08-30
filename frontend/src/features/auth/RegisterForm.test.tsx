import { StrictMode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RegisterForm } from './RegisterForm';
import { ApiRequestError, apiFetch } from '../../api/client';
import bg from '../../i18n/bg.json';

// `apiFetch` is the only seam mocked — no backend, no network. The rest of
// `api/client.ts` (notably `ApiRequestError`) stays real. `vitest.config.ts`
// sets `mockReset: true`, so the fn is cleared before every test. Mirrors
// `LoginForm.test.tsx`'s mocking pattern (spec Tasks & Acceptance).
vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

const GENERIC_ERROR = bg.errors.generic;
const EMAIL_TAKEN = bg.errors.codes.AUTH_EMAIL_TAKEN;
const EMAIL = 'someone@example.com';
const PASSWORD = 'DemoPass123!';

function renderForm() {
  render(
    <StrictMode>
      <RegisterForm />
    </StrictMode>,
  );
  return { user: userEvent.setup() };
}

const emailField = () => screen.getByLabelText(bg.auth.register.email);
const passwordField = () => screen.getByLabelText(bg.auth.register.password);
const registerButton = () => screen.getByRole('button', { name: bg.auth.register.submit });

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
  await user.type(emailField(), EMAIL);
  await user.type(passwordField(), PASSWORD);
  await user.click(registerButton());
}

describe('RegisterForm', () => {
  it('registers successfully and shows the success state', async () => {
    mockedApiFetch.mockResolvedValue({ id: 'user-1', email: EMAIL, role: 'CLIENT' });

    const { user } = renderForm();
    await fillAndSubmit(user);

    expect(await screen.findByTestId('register-success')).toBeInTheDocument();
    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/auth/register', {
      method: 'POST',
      body: { email: EMAIL, password: PASSWORD },
    });
  });

  it('shows the email-taken message and leaves the form editable', async () => {
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 409', 409, 'AUTH_EMAIL_TAKEN'),
    );

    const { user } = renderForm();
    await fillAndSubmit(user);

    expect(await screen.findByTestId('register-error')).toHaveTextContent(EMAIL_TAKEN);
    expect(registerButton()).toBeEnabled();
    // The typed credentials survived the failure — no re-entry needed.
    expect(emailField()).toHaveValue(EMAIL);
    expect(passwordField()).toHaveValue(PASSWORD);
  });

  it('renders field-level errors from a bean-validation failure next to each offending input', async () => {
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 400', 400, 'SHARED_VALIDATION_ERROR', [
        { field: 'email', message: 'must be a well-formed email address' },
        { field: 'password', message: 'size must be between 8 and 100' },
      ]),
    );

    const { user } = renderForm();
    // Before submitting, neither field carries error-describing aria
    // attributes — no error has been set yet.
    expect(emailField()).not.toHaveAttribute('aria-invalid');
    expect(emailField()).not.toHaveAttribute('aria-describedby');
    expect(passwordField()).not.toHaveAttribute('aria-invalid');
    expect(passwordField()).not.toHaveAttribute('aria-describedby');

    await fillAndSubmit(user);

    expect(await screen.findByText(bg.auth.register.fieldErrors.email)).toBeInTheDocument();
    expect(screen.getByText(bg.auth.register.fieldErrors.password)).toBeInTheDocument();
    expect(screen.queryByTestId('register-error')).not.toBeInTheDocument();
    expect(registerButton()).toBeEnabled();

    expect(emailField()).toHaveAttribute('aria-invalid', 'true');
    expect(emailField()).toHaveAttribute('aria-describedby', 'register-email-error');
    expect(screen.getByText(bg.auth.register.fieldErrors.email).id).toBe('register-email-error');
    expect(passwordField()).toHaveAttribute('aria-invalid', 'true');
    expect(passwordField()).toHaveAttribute('aria-describedby', 'register-password-error');
    expect(screen.getByText(bg.auth.register.fieldErrors.password).id).toBe('register-password-error');
  });

  it('falls back to a generic form-level error on a plain network error (not an ApiRequestError)', async () => {
    mockedApiFetch.mockRejectedValue(new Error('Failed to fetch'));

    const { user } = renderForm();
    await fillAndSubmit(user);

    expect(await screen.findByTestId('register-error')).toHaveTextContent(GENERIC_ERROR);
    expect(registerButton()).toBeEnabled();
  });

  it('sends only one request when the user submits twice before the response resolves', async () => {
    let resolveFetch: (value: { id: string; email: string; role: string }) => void;
    mockedApiFetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve;
        }),
    );

    const { user } = renderForm();
    await user.type(emailField(), EMAIL);
    await user.type(passwordField(), PASSWORD);
    // Two rapid clicks on the same element before the first request
    // settles - only one call should go out (spec: double-submit guard is
    // a synchronous phase check, not reliant on `disabled` having
    // committed to the DOM yet). Re-querying by accessible name for the
    // second click would fail once the label switches to "Creating account…".
    const button = registerButton();
    await user.click(button);
    await user.click(button);

    expect(mockedApiFetch).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveFetch!({ id: 'user-1', email: EMAIL, role: 'CLIENT' });
    });
  });

  it('allows a second submission once the first request has settled', async () => {
    mockedApiFetch.mockRejectedValueOnce(
      new ApiRequestError('Request failed with status 409', 409, 'AUTH_EMAIL_TAKEN'),
    );
    mockedApiFetch.mockResolvedValueOnce({ id: 'user-1', email: EMAIL, role: 'CLIENT' });

    const { user } = renderForm();
    await fillAndSubmit(user);
    expect(await screen.findByTestId('register-error')).toHaveTextContent(EMAIL_TAKEN);
    expect(registerButton()).toBeEnabled();

    // The guard only blocks a submit while the previous one is still
    // pending - once the first request has settled (here, with a
    // failure), a genuine second submission must go through.
    await user.click(registerButton());

    expect(mockedApiFetch).toHaveBeenCalledTimes(2);
  });

  it('does not throw when the request resolves after unmounting mid-submit', async () => {
    let resolveFetch: (value: { id: string; email: string; role: string }) => void;
    mockedApiFetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve;
        }),
    );

    const { user } = renderForm();
    await fillAndSubmit(user);

    cleanup();

    // Unlike LoginForm, this form has no external side effect (like
    // `saveToken`) to spy on post-unmount - its only post-response side
    // effects are internal React state updates, which React 18 already
    // no-ops silently after unmount (no console warning exists to assert
    // on). This is the more modest, honestly-scoped claim available: that
    // resolving the pending request after unmount does not throw or
    // produce an uncaught rejection, i.e. the `cancelledRef` guard's early
    // return is reached cleanly instead of a setter blowing up.
    await expect(
      (async () => {
        resolveFetch!({ id: 'user-1', email: EMAIL, role: 'CLIENT' });
        await new Promise((resolve) => setTimeout(resolve, 0));
      })(),
    ).resolves.not.toThrow();
  });
});
