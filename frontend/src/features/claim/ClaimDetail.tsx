import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { apiFetch, ApiRequestError } from '../../api/client';
import { useCancelledRef } from '../../hooks/useCancelledRef';
import type { ClaimResponse } from './claimTypes';
import { claimStatusPresentation } from './claimStatusPresentation';
import { formatDate } from '../../i18n/formatDate';
import { Alert } from '../../components/ui/Alert';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Spinner } from '../../components/ui/Spinner';

type Phase = 'loading' | 'not-found' | 'error' | 'ready';

/** One attachment's authenticated object URL, once fetched (Story 10.4). */
interface AttachmentUrl {
  id: string;
  url: string;
}

/**
 * Claim detail (Story 10.4) - `GET /api/v1/claims/{id}`, owner-scoped: a
 * claim belonging to someone else returns 404, indistinguishable from one
 * that doesn't exist (M4-AD-12/AD-10), so both states render identically
 * here - mirroring `policy/PolicyDetail.tsx`'s four-phase pattern exactly.
 *
 * <p>Photos are fetched separately, one authenticated `GET` per attachment
 * via {@link apiFetch}'s `responseType: 'blob'` (never a plain `<img src>`,
 * which cannot carry the `Authorization` header this app's in-memory-token
 * auth requires - see the spec's own Design Notes). Each blob becomes a
 * `URL.createObjectURL` object URL, used for both the thumbnail `<img>` and
 * its wrapping `<a target="_blank">`.
 *
 * <p><strong>Object URL lifecycle</strong>: every URL created here is
 * revoked - when the claim changes (a re-fetch, e.g. navigating from one
 * claim's detail straight to another's) and unconditionally on unmount -
 * inside the same effect's cleanup, so a URL is never live longer than the
 * claim it belongs to is on screen.
 */
export function ClaimDetail() {
  const { id } = useParams<{ id: string }>();
  const { t, i18n } = useTranslation();
  const [phase, setPhase] = useState<Phase>('loading');
  const [claim, setClaim] = useState<ClaimResponse | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const [attachmentUrls, setAttachmentUrls] = useState<AttachmentUrl[]>([]);

  const cancelledRef = useCancelledRef();

  useEffect(() => {
    if (!id) return;
    setPhase('loading');
    apiFetch<ClaimResponse>(`/api/v1/claims/${id}`, { authenticated: true })
      .then((response) => {
        if (cancelledRef.current) return;
        setClaim(response);
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

  useEffect(() => {
    if (!claim) return;

    let cancelled = false;
    const createdUrls: string[] = [];

    async function loadThumbnails(loadedClaim: ClaimResponse) {
      const results = await Promise.allSettled(
        loadedClaim.attachments.map(async (attachment) => {
          const blob = await apiFetch<Blob>(
            `/api/v1/claims/${loadedClaim.id}/attachments/${attachment.id}`,
            { authenticated: true, responseType: 'blob' },
          );
          return { id: attachment.id, url: URL.createObjectURL(blob) };
        }),
      );

      const fetched: AttachmentUrl[] = [];
      for (const result of results) {
        if (result.status === 'fulfilled') {
          fetched.push(result.value);
          createdUrls.push(result.value.url);
        }
      }

      if (cancelled) {
        // The claim already changed or this screen already unmounted while
        // these fetches were in flight - revoke immediately rather than
        // handing a stale batch to setState.
        fetched.forEach((entry) => URL.revokeObjectURL(entry.url));
        return;
      }
      setAttachmentUrls(fetched);
    }

    loadThumbnails(claim);

    return () => {
      cancelled = true;
      // Every URL this effect run created, revoked before the next claim's
      // batch is created or on unmount - never leaked (spec Boundaries).
      createdUrls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [claim]);

  if (phase === 'loading') {
    return (
      <div data-testid="claim-detail-loading" className="flex items-center gap-2 text-text-muted">
        <Spinner />
        <span>{t('claims.list.loading')}</span>
      </div>
    );
  }

  if (phase === 'not-found') {
    return (
      <div className="space-y-3">
        <Alert variant="danger" data-testid="claim-detail-not-found">
          {t('claims.detail.notFound')}
        </Alert>
        <Link to="/claims" className="inline-block text-sm text-accent underline">
          {t('claims.detail.backToList')}
        </Link>
      </div>
    );
  }

  if (phase === 'error') {
    return (
      <div className="space-y-3">
        <Alert variant="danger" data-testid="claim-detail-error">
          {t('claims.list.error')}
        </Alert>
        <Button variant="secondary" onClick={() => setReloadToken((n) => n + 1)}>
          {t('claims.list.retry')}
        </Button>
      </div>
    );
  }

  // phase === 'ready' - claim is always set by this point; the check only
  // narrows the type for the render below.
  if (!claim) return null;

  const status = claimStatusPresentation(claim, t);

  return (
    <div className="space-y-4" data-testid="claim-detail">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-2xl font-semibold tracking-tight text-text">{t('claims.detail.heading')}</h2>
        <Badge variant={status.variant} data-testid="claim-detail-status">
          {status.label}
        </Badge>
      </div>

      <Card>
        <dl className="space-y-4">
          <div>
            <dt className="text-sm font-semibold text-text-muted">{t('claims.detail.claimNumber')}</dt>
            <dd data-testid="claim-number" className="text-xl font-semibold text-text">
              {claim.claimNumber}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-semibold text-text-muted">{t('claims.detail.description')}</dt>
            <dd data-testid="claim-description" className="text-text">
              {claim.description}
            </dd>
          </div>

          <div className="grid grid-cols-2 gap-x-4 gap-y-2 border-t border-border pt-4 text-sm">
            <dt className="font-semibold text-text-muted">{t('claims.detail.policyNumber')}</dt>
            <dd data-testid="claim-policy-number" className="text-right">
              {claim.policyNumber}
            </dd>

            <dt className="font-semibold text-text-muted">{t('claims.detail.incidentDate')}</dt>
            <dd data-testid="claim-incident-date" className="text-right">
              {formatDate(claim.incidentDate, i18n.language)}
            </dd>

            <dt className="font-semibold text-text-muted">{t('claims.detail.location')}</dt>
            <dd data-testid="claim-location" className="text-right">
              {claim.location}
            </dd>

            <dt className="font-semibold text-text-muted">{t('claims.detail.submittedAt')}</dt>
            <dd data-testid="claim-submitted-at" className="text-right">
              {formatDate(claim.submittedAt, i18n.language)}
            </dd>
          </div>
        </dl>
      </Card>

      <Card title={t('claims.detail.statusHistory')} titleAs="h3">
        <ul data-testid="claim-status-history" className="space-y-2 text-sm">
          {claim.statusHistory.map((entry, index) => {
            const entryStatus = claimStatusPresentation(entry, t);
            return (
              <li key={`${entry.status}-${index}`} className="flex items-center justify-between gap-4">
                <Badge variant={entryStatus.variant}>{entryStatus.label}</Badge>
                <span className="text-text-muted">{formatDate(entry.occurredAt, i18n.language)}</span>
              </li>
            );
          })}
        </ul>
      </Card>

      {claim.attachments.length > 0 && (
        <Card title={t('claims.detail.attachments')} titleAs="h3">
          {/* Plain responsive thumbnail grid, no lightbox/carousel/gallery
              dependency (spec Never) - each thumbnail links to itself in a
              new tab. */}
          <ul
            data-testid="claim-attachments"
            className="grid grid-cols-2 gap-3 sm:grid-cols-3"
          >
            {claim.attachments.map((attachment) => {
              const url = attachmentUrls.find((entry) => entry.id === attachment.id)?.url;
              if (!url) return null;
              return (
                <li key={attachment.id}>
                  <a href={url} target="_blank" rel="noreferrer" data-testid="claim-attachment-link">
                    <img
                      src={url}
                      alt={attachment.displayFilename}
                      className="aspect-square w-full rounded-md border border-border object-cover"
                    />
                  </a>
                </li>
              );
            })}
          </ul>
        </Card>
      )}

      <Link to="/claims" className="inline-block text-sm text-accent underline">
        {t('claims.detail.backToList')}
      </Link>
    </div>
  );
}
