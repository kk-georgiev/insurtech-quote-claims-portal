import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { apiFetch, ApiRequestError } from '../api/client';

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
    <section>
      <h2>{t('app.health.heading')}</h2>
      {health.phase === 'checking' && <p>{t('app.health.checking')}</p>}
      {health.phase === 'reachable' && (
        <p data-testid="health-status">{t('app.health.reachable')}</p>
      )}
      {health.phase === 'unreachable' && (
        <p data-testid="health-status">{t('app.health.unreachable')}</p>
      )}
    </section>
  );
}
