import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { apiFetch } from '../../api/client';
import { useCancelledRef } from '../../hooks/useCancelledRef';
import type { ClaimResponse } from './claimTypes';
import { claimStatusPresentation } from './claimStatusPresentation';
import { formatDate } from '../../i18n/formatDate';
import { Alert } from '../../components/ui/Alert';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Spinner } from '../../components/ui/Spinner';

type Phase = 'loading' | 'error' | 'ready';

/**
 * "My claims" list (Story 10.4) - `GET /api/v1/claims`, owner-scoped and
 * newest-first server-side (M4-AD-12/AD-10), so this screen renders exactly
 * what it receives. Structurally the twin of `policy/MyPolicies.tsx`: `Card`
 * + `Badge` rows, the whole row one `<Link>` target, single column at every
 * width, and three states none of which is a blank screen.
 *
 * <p>Unlike `MyPolicies`, the empty state has one cause and one action -
 * a claim is only ever filed from a policy's own detail screen (spec
 * Boundaries: no standalone "file a claim" entry point on this screen), so
 * there is no second empty-state branch to choose between.
 */
export function MyClaims() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [phase, setPhase] = useState<Phase>('loading');
  const [claims, setClaims] = useState<ClaimResponse[]>([]);
  const [reloadToken, setReloadToken] = useState(0);

  const cancelledRef = useCancelledRef();

  useEffect(() => {
    setPhase('loading');
    apiFetch<ClaimResponse[]>('/api/v1/claims', { authenticated: true })
      .then((response) => {
        if (cancelledRef.current) return;
        setClaims(response);
        setPhase('ready');
      })
      .catch(() => {
        if (cancelledRef.current) return;
        setPhase('error');
      });
  }, [reloadToken]);

  return (
    <section
      aria-labelledby="my-claims-heading"
      data-testid="my-claims"
      className="border-0 bg-transparent p-0"
    >
      <h2
        id="my-claims-heading"
        className="mb-4 mt-0 text-2xl font-semibold tracking-tight text-text"
      >
        {t('claims.list.heading')}
      </h2>

      {phase === 'loading' && (
        <div data-testid="claims-list-loading" className="flex items-center gap-2 text-text-muted">
          <Spinner />
          <span>{t('claims.list.loading')}</span>
        </div>
      )}

      {phase === 'error' && (
        <div className="space-y-3">
          <Alert variant="danger" data-testid="claims-list-error">
            {t('claims.list.error')}
          </Alert>
          <Button variant="secondary" onClick={() => setReloadToken((n) => n + 1)}>
            {t('claims.list.retry')}
          </Button>
        </div>
      )}

      {phase === 'ready' && claims.length === 0 && (
        <div data-testid="claims-list-empty" className="space-y-3">
          <Alert variant="info">{t('claims.list.empty.body')}</Alert>
          <Button onClick={() => navigate('/policies')}>{t('claims.list.empty.cta')}</Button>
        </div>
      )}

      {phase === 'ready' && claims.length > 0 && (
        <ul data-testid="claims-list" className="space-y-3">
          {claims.map((claim) => {
            const status = claimStatusPresentation(claim, t);

            return (
              <li key={claim.id}>
                <Link to={`/claims/${claim.id}`} className="block" data-testid="claim-row">
                  <Card className="transition-shadow hover:shadow-md">
                    <div className="flex items-start justify-between gap-4">
                      <div className="min-w-0">
                        <p className="text-lg font-semibold text-text">{claim.claimNumber}</p>
                        <p className="text-sm text-text-muted">
                          {t('claims.list.policyNumber', { policyNumber: claim.policyNumber })}
                        </p>
                        <p className="text-sm text-text-muted">
                          {formatDate(claim.incidentDate, i18n.language)}
                        </p>
                      </div>
                      <Badge variant={status.variant} className="shrink-0">
                        {status.label}
                      </Badge>
                    </div>
                  </Card>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
