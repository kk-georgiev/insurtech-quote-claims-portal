import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { apiFetch } from '../../api/client';
import { saveToken } from '../../api/authToken';
import { getCurrentRole, roleHome } from '../../app/roleHome';
import { useFormSubmission } from '../../hooks/useFormSubmission';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { FormField } from '../../components/ui/FormField';
import { Input } from '../../components/ui/Input';
import { Spinner } from '../../components/ui/Spinner';

interface LoginResponse {
  token: string;
}



/**
 * Login screen (Story 1.3, routing added Story 2.2). On success: validate
 * the returned token through `app/roleHome.ts`'s `getCurrentRole` (Story
 * 7.1), and only then persist it and navigate to that role's home route via
 * React Router (`useNavigate`, never `window.location` — AD-10).
 *
 * A 200 whose token does not decode, whose role is not an `isRole` match,
 * or that is already expired is a controlled failure at this call site
 * (spec Boundaries & Constraints): it is handled exactly like a failed
 * login — generic error, form stays editable, and the token is NOT written
 * to `localStorage`. `roleHome` never sees an unknown role.
 *
 * The frontend role check is a UX convenience, not a security boundary
 * (AD-4): this story adds no access enforcement — that is Story 2.4.
 */
export function LoginForm() {
  const { t } = useTranslation();

  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const { submitting, formError, fieldErrors, submit, reportFailure } = useFormSubmission(
    'auth.login',
    t,
    { formLevelCodes: ['AUTH_INVALID_CREDENTIALS'] },
  );

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    await submit(async (isCancelled) => {
      const response = await apiFetch<LoginResponse>('/api/v1/auth/login', {
        method: 'POST',
        body: { email, password },
      });
      if (isCancelled()) return;

      // Order matters (spec Boundaries & Constraints): validate -> only
      // then saveToken. Story 7.1 routes this through the same
      // `getCurrentRole` every other role check in the app uses (Epic 2
      // retro item 14 / Epic 3 retro item 35 - stop re-implementing
      // decode+isRole inline here) - passed the just-received token
      // directly rather than reading it back from storage, since it is not
      // saved yet. A token that does not decode, whose role is
      // unrecognized, or that is already expired is treated exactly like a
      // failed login.
      const role = getCurrentRole(response.token);
      if (!role) {
        // Not a backend error - the request succeeded and the token came back
        // unusable - so there is no `code` to resolve and nothing was thrown
        // for the shared error routing to catch. Same generic copy the
        // resolver falls back to, taken straight from the catalog.
        reportFailure(null);
        return;
      }

      saveToken(response.token);
      // The shared hook returns the phase to `editing` once this action
      // resolves: today the component just unmounts, but if navigation is
      // ever blocked (Story 2.4 guard / `useBlocker`) an un-reset form would
      // be stuck `disabled` in `'submitting'` with no error. `replace: true`
      // keeps `/login` out of history so Back after a post-auth redirect
      // doesn't land on the login form again.
      navigate(roleHome(role), { replace: true });
    });
  }

  return (
    <Card title={t('auth.login.heading')} titleAs="h2">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormField label={t('auth.login.email')} error={fieldErrors.email} errorId="login-email-error">
          <Input
            id="login-email"
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
          label={t('auth.login.password')}
          error={fieldErrors.password}
          errorId="login-password-error"
        >
          <Input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={submitting}
            invalid={Boolean(fieldErrors.password)}
          />
        </FormField>
        {formError && (
          <Alert variant="danger" data-testid="login-error">
            {formError}
          </Alert>
        )}
        <Button type="submit" disabled={submitting}>
          {submitting ? (
            <>
              <Spinner className="mr-2" />
              {t('auth.login.submitting')}
            </>
          ) : (
            t('auth.login.submit')
          )}
        </Button>
      </form>
    </Card>
  );
}
