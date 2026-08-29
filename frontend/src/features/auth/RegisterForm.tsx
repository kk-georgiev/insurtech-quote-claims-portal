import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { apiFetch, ApiRequestError } from '../../api/client';
import { resolveFieldErrors, resolveFormError } from '../../i18n/errorMessages';

interface RegisterResponse {
  id: string;
  email: string;
  role: string;
}

type FormPhase = 'editing' | 'submitting' | 'success';



/**
 * Client self-registration screen (Story 1.2). Always registers as CLIENT -
 * there is no role selector, matching the backend's privilege-escalation
 * invariant. No redirect to a login screen on success (it doesn't exist yet,
 * Story 1.3) - shows a success state in place instead.
 */
export function RegisterForm() {
  const { t } = useTranslation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phase, setPhase] = useState<FormPhase>('editing');
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Unmount guard, same intent as HealthStatus.tsx's `cancelled` flag: the
  // request can still resolve after the user navigates away mid-submit, and
  // without this the `then`/`catch` handlers below would call state setters
  // on an unmounted component. A ref (not a plain effect-local variable) is
  // needed here because the async work is kicked off from the submit event
  // handler, not from an effect - but that means the mount effect must
  // explicitly reset it to `false`, not just register the `true`-setting
  // cleanup: React's StrictMode dev double-invoke (mount -> cleanup -> mount)
  // would otherwise leave a stale `true` behind after the very first mount,
  // permanently blocking every future submit's state updates.
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
      await apiFetch<RegisterResponse>('/api/v1/auth/register', {
        method: 'POST',
        body: { email, password },
      });
      if (cancelledRef.current) return;
      setPhase('success');
    } catch (error) {
      if (cancelledRef.current) return;

      // Form stays editable after any error - never locked/cleared.
      setPhase('editing');

      if (error instanceof ApiRequestError) {
        if (error.code === 'AUTH_EMAIL_TAKEN') {
          setFormError(resolveFormError(error, t));
          return;
        }
        if (error.fieldErrors && error.fieldErrors.length > 0) {
          setFieldErrors(resolveFieldErrors(error.fieldErrors, 'auth.register', error.code, t));
          return;
        }
      }
      setFormError(resolveFormError(error, t));
    }
  }

  if (phase === 'success') {
    return (
      <section>
        <h2>{t('auth.register.success')}</h2>
        <p data-testid="register-success">
          {t('auth.register.successBody')}
        </p>
      </section>
    );
  }

  const submitting = phase === 'submitting';

  return (
    <section>
      <h2>{t('auth.register.heading')}</h2>
      <form onSubmit={handleSubmit} noValidate>
        <div>
          <label htmlFor="register-email">{t('auth.register.email')}</label>
          <input
            id="register-email"
            name="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            disabled={submitting}
            aria-invalid={fieldErrors.email ? true : undefined}
            aria-describedby={fieldErrors.email ? 'register-email-error' : undefined}
          />
          {fieldErrors.email && (
            <p role="alert" id="register-email-error">
              {fieldErrors.email}
            </p>
          )}
        </div>
        <div>
          <label htmlFor="register-password">{t('auth.register.password')}</label>
          <input
            id="register-password"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={100}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={submitting}
            aria-invalid={fieldErrors.password ? true : undefined}
            aria-describedby={fieldErrors.password ? 'register-password-error' : undefined}
          />
          {fieldErrors.password && (
            <p role="alert" id="register-password-error">
              {fieldErrors.password}
            </p>
          )}
        </div>
        {formError && (
          <p role="alert" data-testid="register-error">
            {formError}
          </p>
        )}
        <button type="submit" disabled={submitting}>
          {submitting ? t('auth.register.submitting') : t('auth.register.submit')}
        </button>
      </form>
    </section>
  );
}
