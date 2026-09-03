import type { ClaimResponse } from './claimTypes';
import type { Translate } from '../../i18n/errorMessages';
import type { BadgeProps } from '../../components/ui/Badge';

export interface ClaimStatusPresentation {
  variant: NonNullable<BadgeProps['variant']>;
  label: string;
}

/**
 * Maps a claim's stored status to the fixed five-state vocabulary
 * (epic-10-context.md, UX & Interaction Patterns) - one label and one
 * variant per state, defined once here so no screen invents a sixth.
 * Mirrors `policy/policyStatusPresentation.ts`'s shape exactly, but takes
 * only `Pick<ClaimResponse, 'status'>` - unlike a policy's derived status, a
 * claim's status carries no dates to interpolate into its label.
 *
 * `SUBMITTED` is `neutral` (the claim exists, nothing has happened yet),
 * `UNDER_REVIEW` is `info` (in progress), `APPROVED`/`PAID` are both
 * `success` (a favourable outcome for the client either way), and
 * `REJECTED` is the one `danger` state.
 */
export function claimStatusPresentation(
  claim: Pick<ClaimResponse, 'status'>,
  t: Translate,
): ClaimStatusPresentation {
  switch (claim.status) {
    case 'UNDER_REVIEW':
      return { variant: 'info', label: t('claims.status.underReview') };
    case 'APPROVED':
      return { variant: 'success', label: t('claims.status.approved') };
    case 'REJECTED':
      return { variant: 'danger', label: t('claims.status.rejected') };
    case 'PAID':
      return { variant: 'success', label: t('claims.status.paid') };
    case 'SUBMITTED':
    default:
      return { variant: 'neutral', label: t('claims.status.submitted') };
  }
}
