import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { apiFetch, ApiRequestError } from '../../api/client';
import { saveToken, decodeToken } from '../../api/authToken';
import { isRole, roleHome } from '../../app/roleHome';
import { resolveFieldErrors, resolveFormError } from '../../i18n/errorMessages';
import type { FieldFailure, FormFailure } from '../../i18n/errorMessages';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { FormField } from '../../components/ui/FormField';
import { Input } from '../../components/ui/Input';
import { Spinner } from '../../components/ui/Spinner';

interface LoginResponse {
  token: string;
}

type FormPhase = 'editing' | 'submitting';



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
  const { t } = useTranslation();

  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phase, setPhase] = useState<FormPhase>('editing');
  const [formFailure, setFormFailure] = useState<FormFailure>(null);
  const [fieldFailure, setFieldFailure] = useState<FieldFailure>(null);

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
    setFormFailure(null);
    setFieldFailure(null);

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
        // Not a backend error - the request succeeded and the token came back
        // unusable - so there is no `code` to resolve. Same generic copy the
        // resolver falls back to, taken straight from the catalog.
        setFormFailure({ source: null });
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

  // Resolved during render, never stored resolved: an error already on
  // screen must re-translate the instant the language changes, with no
  // resubmit. `formFailure`/`fieldFailure` hold language-neutral sources.
  const formError = formFailure ? resolveFormError(formFailure.source, t) : null;
  const fieldErrors = fieldFailure
    ? resolveFieldErrors(fieldFailure.fieldErrors, 'auth.login', fieldFailure.code, t)
    : {};

  const submitting = phase === 'submitting';

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
