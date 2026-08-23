import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { apiFetch, ApiRequestError } from '../../api/client';
import type { ApiFieldError } from '../../api/client';
import { saveToken, decodeToken } from '../../api/authToken';

interface LoginResponse {
  token: string;
}

type FormPhase = 'editing' | 'submitting' | 'success';

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
 * Login screen (Story 1.3). On success, stores the JWT (`api/authToken.ts`,
 * localStorage) and shows its decoded role purely for confirmation - no
 * redirect or role-based routing yet (Epic 2, Story 2.2).
 */
export function LoginForm() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phase, setPhase] = useState<FormPhase>('editing');
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [role, setRole] = useState<string | null>(null);

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
    setPhase('submitting');
    setFormError(null);
    setFieldErrors({});

    try {
      const response = await apiFetch<LoginResponse>('/api/v1/auth/login', {
        method: 'POST',
        body: { email, password },
      });
      if (cancelledRef.current) return;

      saveToken(response.token);
      const decoded = decodeToken(response.token);
      setRole(decoded?.role ?? null);
      setPhase('success');
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

  if (phase === 'success') {
    return (
      <section>
        <h2>Logged in</h2>
        <p data-testid="login-success">You are logged in{role ? ` as ${role}` : ''}.</p>
      </section>
    );
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
          />
          {fieldErrors.email && <p role="alert">{fieldErrors.email}</p>}
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
          />
          {fieldErrors.password && <p role="alert">{fieldErrors.password}</p>}
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
