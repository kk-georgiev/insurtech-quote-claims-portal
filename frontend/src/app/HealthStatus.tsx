import { useEffect, useState } from 'react';
import { apiFetch, ApiRequestError } from '../api/client';

interface ActuatorHealthResponse {
  status: string;
}

type HealthState =
  | { phase: 'checking' }
  | { phase: 'reachable' }
  | { phase: 'unreachable'; reason: string };

/**
 * Trivial health round-trip (Story 1.1 AC): calls the backend's Actuator
 * health endpoint on load via the typed API client and renders whether the
 * backend is reachable. Never crashes on failure - renders "unreachable".
 */
export function HealthStatus() {
  const [health, setHealth] = useState<HealthState>({ phase: 'checking' });

  useEffect(() => {
    let cancelled = false;

    apiFetch<ActuatorHealthResponse>('/actuator/health')
      .then((response) => {
        if (cancelled) return;
        setHealth(
          response.status === 'UP'
            ? { phase: 'reachable' }
            : { phase: 'unreachable', reason: `backend reported status "${response.status}"` },
        );
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        const reason = error instanceof ApiRequestError ? error.message : 'unknown error';
        setHealth({ phase: 'unreachable', reason });
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <section>
      <h2>Backend status</h2>
      {health.phase === 'checking' && <p>Checking backend...</p>}
      {health.phase === 'reachable' && <p data-testid="health-status">Backend is reachable.</p>}
      {health.phase === 'unreachable' && (
        <p data-testid="health-status">Backend is unreachable ({health.reason}).</p>
      )}
    </section>
  );
}
