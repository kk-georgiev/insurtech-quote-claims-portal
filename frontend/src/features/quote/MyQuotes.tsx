import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { apiFetch } from '../../api/client';
import { useCancelledRef } from '../../hooks/useCancelledRef';
import type { QuoteResponse } from './QuoteForm';
import { quoteStatusPresentation } from './quoteStatusPresentation';
import { formatDate } from '../../i18n/formatDate';
import { Alert } from '../../components/ui/Alert';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Spinner } from '../../components/ui/Spinner';

type Phase = 'loading' | 'error' | 'ready';

/**
 * "My quotes" list (Story 6.3, FR-M3-01) - `GET /api/v1/quotes`, owner-
 * scoped and newest-first server-side (Architecture Spine AD-10/AD-12), so
 * this screen renders exactly what it receives with no client-side
 * filtering or re-sorting.
 *
 * Every row is `Card` + `Badge`, the whole row one `<Link>` target, single
 * column at every width (UX EXPERIENCE.md, Component Patterns) - no table.
 * The four states (loading/empty/error/populated) sit below one stable
 * `<h2>` so the screen's identity doesn't flicker between them; none
 * renders as a blank screen (UX-DR6).
 *
 * The wrapping `<section>` drops the legacy `main > section` card chrome
 * (`border-0 bg-transparent p-0`, Story 9.2) so each row's own `Card` is the
 * only card - same pattern `ClientShell`/the role shells already use.
 */
export function MyQuotes() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [phase, setPhase] = useState<Phase>('loading');
  const [quotes, setQuotes] = useState<QuoteResponse[]>([]);
  const [reloadToken, setReloadToken] = useState(0);

  // A slow response resolving after the user has navigated away must not
  // call a state setter on an unmounted component. Shared with every other
  // async screen since Story 8.2 (Epic 6 retro item 44) - this is a load,
  // not a submit, so it takes the guard alone and no phase machine.
  const cancelledRef = useCancelledRef();

  useEffect(() => {
    setPhase('loading');
    apiFetch<QuoteResponse[]>('/api/v1/quotes', { authenticated: true })
      .then((response) => {
        if (cancelledRef.current) return;
        setQuotes(response);
        setPhase('ready');
      })
      .catch(() => {
        if (cancelledRef.current) return;
        setPhase('error');
      });
  }, [reloadToken]);

  return (
    <section
      aria-labelledby="my-quotes-heading"
      data-testid="my-quotes"
      className="border-0 bg-transparent p-0"
    >
      <h2 id="my-quotes-heading" className="mb-4 mt-0 text-2xl font-semibold tracking-tight text-text">
        {t('quotes.list.heading')}
      </h2>

      {phase === 'loading' && (
        <div data-testid="quotes-list-loading" className="flex items-center gap-2 text-text-muted">
          <Spinner />
          <span>{t('quotes.list.loading')}</span>
        </div>
      )}

      {phase === 'error' && (
        <div className="space-y-3">
          <Alert variant="danger" data-testid="quotes-list-error">
            {t('quotes.list.error')}
          </Alert>
          <Button variant="secondary" onClick={() => setReloadToken((n) => n + 1)}>
            {t('quotes.list.retry')}
          </Button>
        </div>
      )}

      {phase === 'ready' && quotes.length === 0 && (
        <div data-testid="quotes-list-empty" className="space-y-3">
          <Alert variant="info">{t('quotes.list.empty.title')}</Alert>
          <Button onClick={() => navigate('/')}>{t('quotes.list.empty.cta')}</Button>
        </div>
      )}

      {phase === 'ready' && quotes.length > 0 && (
        <ul data-testid="quotes-list" className="space-y-3">
          {quotes.map((quote) => {
            const status = quoteStatusPresentation(quote, t, i18n.language);
            const zoneLabel = t(`quote.result.zones.${quote.zoneId}`, {
              defaultValue: t('quote.result.zoneFallback', { zoneId: quote.zoneId }),
            });

            return (
              <li key={quote.id}>
                <Link to={`/quotes/${quote.id}`} className="block" data-testid="quote-row">
                  <Card className="transition-shadow hover:shadow-md">
                    <div className="flex items-start justify-between gap-4">
                      <div className="min-w-0">
                        <p className="text-lg font-semibold text-text">
                          {quote.totalPremium} {quote.currency}
                        </p>
                        <p className="text-sm text-text-muted">
                          {t('quotes.list.vehicleSummary', { engineCc: quote.engineCc, zone: zoneLabel })}
                        </p>
                        <p className="text-sm text-text-muted">
                          {formatDate(quote.createdAt, i18n.language)}
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
