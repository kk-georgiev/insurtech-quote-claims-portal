import type { QuoteResponse } from './QuoteForm';
import type { Translate } from '../../i18n/errorMessages';
import { formatDate } from '../../i18n/formatDate';
import type { BadgeProps } from '../../components/ui/Badge';

// Display-only heuristic, not a business rule: the backend's `status`
// field (CALCULATED/ACCEPTED/EXPIRED/CANCELLED) is the only thing that is
// authoritative (Architecture Spine AD-3). This threshold only decides
// which colour/wording a still-valid quote gets - a day of client-clock
// skew at the boundary is a cosmetic nuance, never a correctness issue.
const EXPIRING_SOON_THRESHOLD_DAYS = 3;

export interface QuoteStatusPresentation {
  variant: NonNullable<BadgeProps['variant']>;
  label: string;
}

/**
 * Maps a quote's backend-derived status to the fixed status vocabulary
 * (UX EXPERIENCE.md, Component Patterns): four quote states, one label +
 * one variant each, so no screen invents a fifth. `CANCELLED` is reserved -
 * no backend response can carry it this milestone - but the mapping exists
 * so a future story doesn't have to touch every call site.
 */
export function quoteStatusPresentation(
  quote: Pick<QuoteResponse, 'status' | 'validUntil'>,
  t: Translate,
  language: string,
): QuoteStatusPresentation {
  switch (quote.status) {
    case 'ACCEPTED':
      return { variant: 'success', label: t('quotes.status.accepted') };
    case 'CANCELLED':
      return { variant: 'neutral', label: t('quotes.status.cancelled') };
    case 'EXPIRED':
      return {
        variant: 'danger',
        label: t('quotes.status.expiredOn', { date: formatDate(quote.validUntil, language) }),
      };
    case 'CALCULATED':
    default: {
      const daysUntil = Math.ceil(
        (new Date(quote.validUntil).getTime() - Date.now()) / (1000 * 60 * 60 * 24),
      );
      if (daysUntil <= 0) {
        return { variant: 'warning', label: t('quotes.status.expiresToday') };
      }
      if (daysUntil <= EXPIRING_SOON_THRESHOLD_DAYS) {
        return { variant: 'warning', label: t('quotes.status.expiresInDays', { count: daysUntil }) };
      }
      return {
        variant: 'info',
        label: t('quotes.status.validUntil', { date: formatDate(quote.validUntil, language) }),
      };
    }
  }
}
