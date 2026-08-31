import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { apiFetch, ApiRequestError } from '../../api/client';
import { resolveFieldErrors, resolveFormError } from '../../i18n/errorMessages';
import type { FieldFailure, FormFailure } from '../../i18n/errorMessages';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { FormField } from '../../components/ui/FormField';
import { Input } from '../../components/ui/Input';
import { Spinner } from '../../components/ui/Spinner';

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
  const [formFailure, setFormFailure] = useState<FormFailure>(null);
  const [fieldFailure, setFieldFailure] = useState<FieldFailure>(null);

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
    setFormFailure(null);
    setFieldFailure(null);

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
          setFormFailure({ source: error });
          return;
        }
        if (error.fieldErrors && error.fieldErrors.length > 0) {
          setFieldFailure({ fieldErrors: error.fieldErrors, code: error.code });
          return;
        }
      }
      setFormFailure({ source: error });
    }
  }

  if (phase === 'success') {
    return (
      <Card title={t('auth.register.success')} titleAs="h2">
        {/* Same shared banner as the failure path, in its success variant —
            the legacy `[data-testid='register-success']` colour rule in
            index.css is what this replaces. */}
        <Alert variant="success" data-testid="register-success">
          {t('auth.register.successBody')}
        </Alert>
      </Card>
    );
  }

  // Resolved during render, never stored resolved: an error already on
  // screen must re-translate the instant the language changes, with no
  // resubmit. `formFailure`/`fieldFailure` hold language-neutral sources.
  const formError = formFailure ? resolveFormError(formFailure.source, t) : null;
  const fieldErrors = fieldFailure
    ? resolveFieldErrors(fieldFailure.fieldErrors, 'auth.register', fieldFailure.code, t)
    : {};

  const submitting = phase === 'submitting';

  return (
    <Card title={t('auth.register.heading')} titleAs="h2">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormField
          label={t('auth.register.email')}
          error={fieldErrors.email}
          errorId="register-email-error"
        >
          <Input
            id="register-email"
            name="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            disabled={submitting}
            invalid={Boolean(fieldErrors.email)}
          />
        </FormField>
        <FormField
          label={t('auth.register.password')}
          error={fieldErrors.password}
          errorId="register-password-error"
        >
          <Input
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
            invalid={Boolean(fieldErrors.password)}
          />
        </FormField>
        {formError && (
          <Alert variant="danger" data-testid="register-error">
            {formError}
          </Alert>
        )}
        <Button type="submit" disabled={submitting}>
          {submitting ? (
            <>
              <Spinner className="mr-2" />
              {t('auth.register.submitting')}
            </>
          ) : (
            t('auth.register.submit')
          )}
        </Button>
      </form>
    </Card>
  );
}
