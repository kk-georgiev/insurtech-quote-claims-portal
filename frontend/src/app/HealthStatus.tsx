import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { apiFetch, ApiRequestError } from '../api/client';
import { Alert } from '../components/ui/Alert';
import { Card } from '../components/ui/Card';
import { Spinner } from '../components/ui/Spinner';

interface ActuatorHealthResponse {
  status: string;
}

type HealthState = { phase: 'checking' } | { phase: 'reachable' } | { phase: 'unreachable' };

/**
 * Trivial health round-trip (Story 1.1 AC): calls the backend's Actuator
 * health endpoint on load via the typed API client and renders whether the
 * backend is reachable. Never crashes on failure - renders "unreachable".
 *
 * Story 3.2a: the failure reason used to be interpolated into the sentence
 * the user reads, but it is `ApiRequestError.message` - developer-facing
 * prose that AD-7 says must never be rendered, and untranslatable besides.
 * It now goes to the console, which is where AD-7 puts it, and the user gets
 * a plain translated sentence.
 */
export function HealthStatus() {
  const { t } = useTranslation();

  const [health, setHealth] = useState<HealthState>({ phase: 'checking' });

  useEffect(() => {
    let cancelled = false;

    apiFetch<ActuatorHealthResponse>('/actuator/health')
      .then((response) => {
        if (cancelled) return;
        if (response.status === 'UP') {
          setHealth({ phase: 'reachable' });
          return;
        }
        console.error(`Backend health check reported status "${response.status}"`);
        setHealth({ phase: 'unreachable' });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        console.error(
          'Backend health check failed:',
          error instanceof ApiRequestError ? error.message : error,
        );
        setHealth({ phase: 'unreachable' });
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <Card title={t('app.health.heading')} titleAs="h2">
      {health.phase === 'checking' && (
        <p className="flex items-center gap-2 text-sm text-text-muted">
          <Spinner />
          {t('app.health.checking')}
        </p>
      )}
      {health.phase === 'reachable' && (
        <p data-testid="health-status" className="text-sm text-text">
          {t('app.health.reachable')}
        </p>
      )}
      {/* Only the failure is a banner. "Reachable" renders on load and is not
          an error, so wrapping it in `role="alert"` would make assistive tech
          announce a non-event assertively every time the screen opens. */}
      {health.phase === 'unreachable' && (
        <Alert variant="danger" data-testid="health-status">
          {t('app.health.unreachable')}
        </Alert>
      )}
    </Card>
  );
}
