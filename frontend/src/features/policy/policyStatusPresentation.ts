import type { PolicyResponse } from './policyTypes';
import type { Translate } from '../../i18n/errorMessages';
import { formatDate } from '../../i18n/formatDate';
import type { BadgeProps } from '../../components/ui/Badge';

export interface PolicyStatusPresentation {
  variant: NonNullable<BadgeProps['variant']>;
  label: string;
}

/**
 * Maps a policy's backend-derived status to the fixed status vocabulary
 * (UX EXPERIENCE.md, Component Patterns) - one label and one variant per
 * state, so no screen invents its own.
 *
 * <p><strong>An expired policy is `neutral`, never `danger`</strong>
 * (UX-DR2). This is the one place that rule lives, and it is the single
 * deliberate difference from `quote/quoteStatusPresentation.ts`, where
 * `EXPIRED` *is* `danger`: an expired quote is an opportunity the client
 * lost, while a policy that reached the end of its term did exactly what it
 * was bought to do. Colouring it red would tell the client something went
 * wrong when nothing did.
 *
 * `CANCELLED` is reserved - no response can carry it this milestone - but
 * the mapping exists so a future story does not have to touch every call
 * site.
 */
export function policyStatusPresentation(
  policy: Pick<PolicyResponse, 'status' | 'coverageStart' | 'coverageEnd'>,
  t: Translate,
  language: string,
): PolicyStatusPresentation {
  switch (policy.status) {
    case 'SCHEDULED':
      return {
        variant: 'info',
        label: t('policies.status.scheduled', { date: formatDate(policy.coverageStart, language) }),
      };
    case 'EXPIRED':
      return {
        variant: 'neutral',
        label: t('policies.status.expiredOn', { date: formatDate(policy.coverageEnd, language) }),
      };
    case 'CANCELLED':
      return { variant: 'neutral', label: t('policies.status.cancelled') };
    case 'ACTIVE':
    default:
      return {
        variant: 'success',
        label: t('policies.status.activeUntil', { date: formatDate(policy.coverageEnd, language) }),
      };
  }
}
