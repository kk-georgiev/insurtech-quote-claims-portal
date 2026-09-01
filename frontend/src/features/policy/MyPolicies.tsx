import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { apiFetch } from '../../api/client';
import { useCancelledRef } from '../../hooks/useCancelledRef';
import type { PolicyResponse } from './policyTypes';
import { policyStatusPresentation } from './policyStatusPresentation';
import type { QuoteResponse } from '../quote/QuoteForm';
import { Alert } from '../../components/ui/Alert';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Spinner } from '../../components/ui/Spinner';

type Phase = 'loading' | 'error' | 'ready';

/**
 * "My policies" list (Story 8.3, FR-M3-10) - `GET /api/v1/policies`,
 * owner-scoped and newest-first server-side (Architecture Spine AD-10/
 * AD-12), so this screen renders exactly what it receives.
 *
 * Structurally the twin of `quote/MyQuotes.tsx`: `Card` + `Badge` rows, the
 * whole row one `<Link>` target, single column at every width, and four
 * states none of which is a blank screen (UX-DR4, UX-DR6).
 *
 * The empty state is the one place the two screens differ. "Accept one of
 * your quotes" is useless advice to someone with no quotes either, so when
 * the list comes back empty this screen asks once whether any quotes exist
 * and points at whichever screen can actually help (UX-DR6).
 *
 * The wrapping `<section>` drops the legacy `main > section` card chrome
 * (`border-0 bg-transparent p-0`, Story 9.2) so each row's own `Card` is the
 * only card - same pattern `ClientShell`/the role shells already use.
 */
export function MyPolicies() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [phase, setPhase] = useState<Phase>('loading');
  const [policies, setPolicies] = useState<PolicyResponse[]>([]);
  const [hasQuotes, setHasQuotes] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  const cancelledRef = useCancelledRef();

  useEffect(() => {
    setPhase('loading');
    apiFetch<PolicyResponse[]>('/api/v1/policies', { authenticated: true })
      .then(async (response) => {
        if (cancelledRef.current) return;
        setPolicies(response);
        // Only asked when it changes the advice given - a client who holds
        // policies is never sent anywhere.
        if (response.length === 0) {
          const quotes = await apiFetch<QuoteResponse[]>('/api/v1/quotes', { authenticated: true });
          if (cancelledRef.current) return;
          setHasQuotes(quotes.length > 0);
        }
        if (cancelledRef.current) return;
        setPhase('ready');
      })
      .catch(() => {
        if (cancelledRef.current) return;
        setPhase('error');
      });
  }, [reloadToken]);

  return (
    <section
      aria-labelledby="my-policies-heading"
      data-testid="my-policies"
      className="border-0 bg-transparent p-0"
    >
      <h2
        id="my-policies-heading"
        className="mb-4 mt-0 text-2xl font-semibold tracking-tight text-text"
      >
        {t('policies.list.heading')}
      </h2>

      {phase === 'loading' && (
        <div data-testid="policies-list-loading" className="flex items-center gap-2 text-text-muted">
          <Spinner />
          <span>{t('policies.list.loading')}</span>
        </div>
      )}

      {phase === 'error' && (
        <div className="space-y-3">
          <Alert variant="danger" data-testid="policies-list-error">
            {t('policies.list.error')}
          </Alert>
          <Button variant="secondary" onClick={() => setReloadToken((n) => n + 1)}>
            {t('policies.list.retry')}
          </Button>
        </div>
      )}

      {phase === 'ready' && policies.length === 0 && (
        <div data-testid="policies-list-empty" className="space-y-3">
          <Alert variant="info">
            {hasQuotes ? t('policies.list.empty.fromQuotes') : t('policies.list.empty.noQuotes')}
          </Alert>
          <Button onClick={() => navigate(hasQuotes ? '/quotes' : '/')}>
            {hasQuotes ? t('policies.list.empty.fromQuotesCta') : t('policies.list.empty.noQuotesCta')}
          </Button>
        </div>
      )}

      {phase === 'ready' && policies.length > 0 && (
        <ul data-testid="policies-list" className="space-y-3">
          {policies.map((policy) => {
            const status = policyStatusPresentation(policy, t, i18n.language);

            return (
              <li key={policy.id}>
                <Link to={`/policies/${policy.id}`} className="block" data-testid="policy-row">
                  <Card className="transition-shadow hover:shadow-md">
                    <div className="flex items-start justify-between gap-4">
                      <div className="min-w-0">
                        <p className="text-lg font-semibold text-text">{policy.policyNumber}</p>
                        <p className="text-sm text-text-muted">
                          {t('policies.list.vehicleSummary', {
                            vehicle: policy.vehicleRegistration ?? policy.vehicleVin,
                            engineCc: policy.engineCc,
                          })}
                        </p>
                        <p className="text-sm text-text-muted">
                          {policy.totalPremium} {policy.currency}
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
