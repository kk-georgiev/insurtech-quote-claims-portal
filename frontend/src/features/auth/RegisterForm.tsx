import { useState } from 'react';
import type { FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { apiFetch } from '../../api/client';
import { useFormSubmission } from '../../hooks/useFormSubmission';
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
  // Registration's own success state stays here, not in the shared hook
  // (Story 8.2): each form's success means something different, and one
  // hook carrying three of them would be worse than the duplication it
  // replaced. The mechanics - phase, guards, error routing - are shared.
  const [registered, setRegistered] = useState(false);

  const { submitting, formError, fieldErrors, submit } = useFormSubmission('auth.register', t, {
    formLevelCodes: ['AUTH_EMAIL_TAKEN'],
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    await submit(async (isCancelled) => {
      await apiFetch<RegisterResponse>('/api/v1/auth/register', {
        method: 'POST',
        body: { email, password },
      });
      if (isCancelled()) return;
      setRegistered(true);
    });
  }

  if (registered) {
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
