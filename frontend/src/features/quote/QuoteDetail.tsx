import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { apiFetch, ApiRequestError } from '../../api/client';
import type { QuoteResponse } from './QuoteForm';
import { QuoteResult } from './QuoteResult';
import { quoteStatusPresentation } from './quoteStatusPresentation';
import { formatDate } from '../../i18n/formatDate';
import { Alert } from '../../components/ui/Alert';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Spinner } from '../../components/ui/Spinner';

type Phase = 'loading' | 'not-found' | 'error' | 'ready';

/**
 * Quote detail (Story 6.3, FR-M3-01) - `GET /api/v1/quotes/{id}`, owner-
 * scoped: a quote belonging to someone else returns 404, indistinguishable
 * from one that doesn't exist (Architecture Spine AD-10), so both states
 * render identically here.
 *
 * An expired quote still renders its full breakdown, with the acceptance
 * affordance *replaced* by an explanation and a way out - never merely
 * disabled (UX EXPERIENCE.md, State Patterns). An accepted quote renders
 * the same way with a static notice; the link to its policy is Story 8.3's
 * job, once policies exist. A still-valid (`CALCULATED`) quote shows only
 * the breakdown and its status - the acceptance form itself is Story 8.2's
 * scope, not this one's.
 */
export function QuoteDetail() {
  const { id } = useParams<{ id: string }>();
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [phase, setPhase] = useState<Phase>('loading');
  const [quote, setQuote] = useState<QuoteResponse | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const cancelledRef = useRef(false);
  useEffect(() => {
    cancelledRef.current = false;
    return () => {
      cancelledRef.current = true;
    };
  }, []);

  useEffect(() => {
    if (!id) return;
    setPhase('loading');
    apiFetch<QuoteResponse>(`/api/v1/quotes/${id}`, { authenticated: true })
      .then((response) => {
        if (cancelledRef.current) return;
        setQuote(response);
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
      <div data-testid="quote-detail-loading" className="flex items-center gap-2 text-text-muted">
        <Spinner />
        <span>{t('quotes.list.loading')}</span>
      </div>
    );
  }

  if (phase === 'not-found') {
    return (
      <div className="space-y-3">
        <Alert variant="danger" data-testid="quote-detail-not-found">
          {t('quotes.detail.notFound')}
        </Alert>
        <Link to="/quotes" className="inline-block text-sm text-accent underline">
          {t('quotes.detail.backToList')}
        </Link>
      </div>
    );
  }

  if (phase === 'error') {
    return (
      <div className="space-y-3">
        <Alert variant="danger" data-testid="quote-detail-error">
          {t('quotes.list.error')}
        </Alert>
        <Button variant="secondary" onClick={() => setReloadToken((n) => n + 1)}>
          {t('quotes.list.retry')}
        </Button>
      </div>
    );
  }

  // phase === 'ready' - quote is always set by this point; the check only
  // narrows the type for the render below.
  if (!quote) return null;

  const status = quoteStatusPresentation(quote, t, i18n.language);

  return (
    <div className="space-y-4" data-testid="quote-detail">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-2xl font-semibold tracking-tight text-text">{t('quotes.detail.heading')}</h2>
        <Badge variant={status.variant} data-testid="quote-detail-status">
          {status.label}
        </Badge>
      </div>

      <QuoteResult quote={quote} />

      {quote.status === 'EXPIRED' && (
        <Card data-testid="quote-detail-expired-notice">
          <p className="text-sm text-text-muted">
            {t('quotes.detail.expiredNotice', { date: formatDate(quote.validUntil, i18n.language) })}
          </p>
          <Button className="mt-3" onClick={() => navigate('/')}>
            {t('quotes.detail.newQuoteCta')}
          </Button>
        </Card>
      )}

      {quote.status === 'ACCEPTED' && (
        <Alert variant="success" data-testid="quote-detail-accepted-notice">
          {t('quotes.detail.acceptedNotice')}
        </Alert>
      )}

      <Link to="/quotes" className="inline-block text-sm text-accent underline">
        {t('quotes.detail.backToList')}
      </Link>
    </div>
  );
}
