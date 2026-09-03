import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { apiFetch, ApiRequestError } from '../../api/client';
import { useCancelledRef } from '../../hooks/useCancelledRef';
import type { PolicyResponse } from './policyTypes';
import { policyStatusPresentation } from './policyStatusPresentation';
import { QuoteResult } from '../quote/QuoteResult';
import { formatDate } from '../../i18n/formatDate';
import { Alert } from '../../components/ui/Alert';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Spinner } from '../../components/ui/Spinner';

type Phase = 'loading' | 'not-found' | 'error' | 'ready';

/**
 * Policy detail (Story 8.3, FR-M3-10) - `GET /api/v1/policies/{id}`,
 * owner-scoped: a policy belonging to someone else returns 404,
 * indistinguishable from one that doesn't exist (Architecture Spine
 * AD-10), so both states render identically here.
 *
 * The breakdown is the *same component* the quote screens use, not a
 * lookalike (FR-M3-07/FR-M3-10): a client comparing their policy against
 * the quote it came from must be able to see the figures match.
 *
 * The three facts a client opens this screen to check - the number, the
 * cover period, the premium - render heavier and larger than their labels
 * (UX-DR12), while the supporting detail stays quiet.
 */
export function PolicyDetail() {
  const { id } = useParams<{ id: string }>();
  const { t, i18n } = useTranslation();
  const [phase, setPhase] = useState<Phase>('loading');
  const [policy, setPolicy] = useState<PolicyResponse | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const cancelledRef = useCancelledRef();

  useEffect(() => {
    if (!id) return;
    setPhase('loading');
    apiFetch<PolicyResponse>(`/api/v1/policies/${id}`, { authenticated: true })
      .then((response) => {
        if (cancelledRef.current) return;
        setPolicy(response);
        setPhase('ready');
      })
      .catch((error: unknown) => {
        if (cancelledRef.current) return;
        if (error instanceof ApiRequestError && error.status === 404) {
          setPhase('not-found');
        } else {
          setPhase('error');
        }
      });
  }, [id, reloadToken]);

  if (phase === 'loading') {
    return (
      <div data-testid="policy-detail-loading" className="flex items-center gap-2 text-text-muted">
        <Spinner />
        <span>{t('policies.list.loading')}</span>
      </div>
    );
  }

  if (phase === 'not-found') {
    return (
      <div className="space-y-3">
        <Alert variant="danger" data-testid="policy-detail-not-found">
          {t('policies.detail.notFound')}
        </Alert>
        <Link to="/policies" className="inline-block text-sm text-accent underline">
          {t('policies.detail.backToList')}
        </Link>
      </div>
    );
  }

  if (phase === 'error') {
    return (
      <div className="space-y-3">
        <Alert variant="danger" data-testid="policy-detail-error">
          {t('policies.list.error')}
        </Alert>
        <Button variant="secondary" onClick={() => setReloadToken((n) => n + 1)}>
          {t('policies.list.retry')}
        </Button>
      </div>
    );
  }

  // phase === 'ready' - policy is always set by this point; the check only
  // narrows the type for the render below.
  if (!policy) return null;

  const status = policyStatusPresentation(policy, t, i18n.language);

  return (
    <div className="space-y-4" data-testid="policy-detail">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-2xl font-semibold tracking-tight text-text">
          {t('policies.detail.heading')}
        </h2>
        <div className="flex items-center gap-3">
          {/* Story 10.3: a claim always starts from the policy it belongs to
              (epic-10-context.md, Routes). No new guard logic - the route
              this leads to sits inside the same CLIENT-only RoleGuard. */}
          <Link
            to={`/policies/${policy.id}/claims/new`}
            className="text-sm text-accent underline"
            data-testid="policy-detail-file-claim"
          >
            {t('policies.detail.fileClaim')}
          </Link>
          <Badge variant={status.variant} data-testid="policy-detail-status">
            {status.label}
          </Badge>
        </div>
      </div>

      <Card>
        <dl className="space-y-4">
          <div>
            <dt className="text-sm font-semibold text-text-muted">
              {t('policies.detail.policyNumber')}
            </dt>
            <dd data-testid="policy-number" className="text-xl font-semibold text-text">
              {policy.policyNumber}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-semibold text-text-muted">
              {t('policies.detail.coveragePeriod')}
            </dt>
            <dd data-testid="policy-coverage-period" className="text-xl font-semibold text-text">
              {t('policies.detail.coverageRange', {
                from: formatDate(policy.coverageStart, i18n.language),
                to: formatDate(policy.coverageEnd, i18n.language),
              })}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-semibold text-text-muted">
              {t('policies.detail.totalPremium')}
            </dt>
            <dd data-testid="policy-total-premium" className="text-xl font-semibold text-text">
              {policy.totalPremium} {policy.currency}
            </dd>
          </div>

          <div className="grid grid-cols-2 gap-x-4 gap-y-2 border-t border-border pt-4 text-sm">
            <dt className="font-semibold text-text-muted">{t('policies.detail.holderName')}</dt>
            <dd data-testid="policy-holder" className="text-right">
              {policy.holderName}
            </dd>

            <dt className="font-semibold text-text-muted">{t('policies.detail.vehicle')}</dt>
            <dd data-testid="policy-vehicle" className="text-right">
              {policy.vehicleRegistration ?? policy.vehicleVin}
            </dd>

            <dt className="font-semibold text-text-muted">{t('policies.detail.issuedAt')}</dt>
            <dd data-testid="policy-issued-at" className="text-right">
              {formatDate(policy.issuedAt, i18n.language)}
            </dd>
          </div>
        </dl>
      </Card>

      {/* The same breakdown component the quote renders - not a second one
          that happens to look similar (FR-M3-07). */}
      <QuoteResult quote={policy} />

      <Link to="/policies" className="inline-block text-sm text-accent underline">
        {t('policies.detail.backToList')}
      </Link>
    </div>
  );
}
