import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { apiFetch, ApiRequestError } from '../../api/client';
import type { ApiFieldError } from '../../api/client';
import { saveToken, decodeToken } from '../../api/authToken';
import { isRole, roleHome } from '../../app/roleHome';

interface LoginResponse {
  token: string;
}

type FormPhase = 'editing' | 'submitting';

// AD-7: `code` is the only thing the frontend uses to select user-facing
// text - never the backend's dev/log-facing `message`. This story has no
// i18n catalog yet (out of scope, same as Story 1.2's RegisterForm), so the
// mapped copy lives here as a plain string for now. Wrong password and
// unknown email both map to AUTH_INVALID_CREDENTIALS and share this exact
// message (spec Boundaries & Constraints) - nothing here distinguishes them.
const INVALID_CREDENTIALS_MESSAGE = 'Incorrect email or password.';
const GENERIC_ERROR_MESSAGE = 'Something went wrong. Please try again.';

function toFieldErrorMap(errors: ApiFieldError[]): Record<string, string> {
  const map: Record<string, string> = {};
  for (const error of errors) {
    map[error.field] = error.message;
  }
  return map;
}

/**
 * Login screen (Story 1.3, routing added Story 2.2). On success: decode the
 * JWT, validate its `role` against the typed `Role` set, and only then
 * persist the token and navigate to that role's home route via React Router
 * (`useNavigate`, never `window.location` — AD-10).
 *
 * A 200 whose token does not decode or whose role is not an `isRole` match
 * is a controlled failure at this call site (spec Boundaries & Constraints):
 * it is handled exactly like a failed login — generic error, form stays
 * editable, and the token is NOT written to `localStorage`. `roleHome` never
 * sees an unknown role.
 *
 * The frontend role check is a UX convenience, not a security boundary
 * (AD-4): this story adds no access enforcement — that is Story 2.4.
 */
export function LoginForm() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phase, setPhase] = useState<FormPhase>('editing');
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Unmount guard, same intent/rationale as RegisterForm.tsx's cancelledRef:
  // the request can resolve after the user navigates away mid-submit, and
  // the mount effect must explicitly reset it to `false` so StrictMode's
  // dev double-invoke doesn't leave a stale `true` behind after first mount.
  const cancelledRef = useRef(false);
  useEffect(() => {
    cancelledRef.current = false;
    return () => {
      cancelledRef.current = true;
    };
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (phase === 'submitting') return;
    setPhase('submitting');
    setFormError(null);
    setFieldErrors({});

    try {
      const response = await apiFetch<LoginResponse>('/api/v1/auth/login', {
        method: 'POST',
        body: { email, password },
      });
      if (cancelledRef.current) return;

      // Order matters (spec Boundaries & Constraints): decode -> isRole
      // guard -> only then saveToken. A token that does not decode or whose
      // role is unrecognized is treated exactly like a failed login.
      const decoded = decodeToken(response.token);
      if (!decoded || !isRole(decoded.role)) {
        setPhase('editing');
        setFormError(GENERIC_ERROR_MESSAGE);
        return;
      }

      saveToken(response.token);
      // Reset phase before navigating: today the component just unmounts,
      // but if navigation is ever blocked (Story 2.4 guard / `useBlocker`)
      // an un-reset form would be stuck `disabled` in `'submitting'` with no
      // error. `replace: true` keeps `/login` out of history so Back after a
      // post-auth redirect doesn't land on the login form again.
      setPhase('editing');
      navigate(roleHome(decoded.role), { replace: true });
    } catch (error) {
      if (cancelledRef.current) return;

      // Form stays editable after any error - never locked/cleared.
      setPhase('editing');

      if (error instanceof ApiRequestError) {
        if (error.code === 'AUTH_INVALID_CREDENTIALS') {
          setFormError(INVALID_CREDENTIALS_MESSAGE);
          return;
        }
        if (error.fieldErrors && error.fieldErrors.length > 0) {
          setFieldErrors(toFieldErrorMap(error.fieldErrors));
          return;
        }
      }
      setFormError(GENERIC_ERROR_MESSAGE);
    }
  }

  const submitting = phase === 'submitting';

  return (
    <section>
      <h2>Log in</h2>
      <form onSubmit={handleSubmit} noValidate>
        <div>
          <label htmlFor="login-email">Email</label>
          <input
            id="login-email"
            name="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            disabled={submitting}
            aria-invalid={fieldErrors.email ? true : undefined}
            aria-describedby={fieldErrors.email ? 'login-email-error' : undefined}
          />
          {fieldErrors.email && (
            <p role="alert" id="login-email-error">
              {fieldErrors.email}
            </p>
          )}
        </div>
        <div>
          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={submitting}
            aria-invalid={fieldErrors.password ? true : undefined}
            aria-describedby={fieldErrors.password ? 'login-password-error' : undefined}
          />
          {fieldErrors.password && (
            <p role="alert" id="login-password-error">
              {fieldErrors.password}
            </p>
          )}
        </div>
        {formError && (
          <p role="alert" data-testid="login-error">
            {formError}
          </p>
        )}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>
    </section>
  );
}
