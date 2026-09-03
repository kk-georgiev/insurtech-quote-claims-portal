import { describe, expect, it } from 'vitest';
import i18n from '../../i18n';
import { claimStatusPresentation } from './claimStatusPresentation';
import type { ClaimResponse } from './claimTypes';
import type { Translate } from '../../i18n/errorMessages';
import bg from '../../i18n/bg.json';

const t: Translate = (key, options) => i18n.t(key, options) as string;

function claim(status: ClaimResponse['status']): Pick<ClaimResponse, 'status'> {
  return { status };
}

describe('claimStatusPresentation', () => {
  it('reads a freshly filed claim as submitted, in the neutral treatment', () => {
    const presentation = claimStatusPresentation(claim('SUBMITTED'), t);

    expect(presentation.variant).toBe('neutral');
    expect(presentation.label).toBe(bg.claims.status.submitted);
  });

  it('reads a claim under review in the info treatment', () => {
    const presentation = claimStatusPresentation(claim('UNDER_REVIEW'), t);

    expect(presentation.variant).toBe('info');
    expect(presentation.label).toBe(bg.claims.status.underReview);
  });

  it('reads an approved claim in the success treatment', () => {
    const presentation = claimStatusPresentation(claim('APPROVED'), t);

    expect(presentation.variant).toBe('success');
    expect(presentation.label).toBe(bg.claims.status.approved);
  });

  it('reads a paid claim in the success treatment too', () => {
    // Both APPROVED and PAID are favourable outcomes for the client
    // (epic-10-context.md, UX & Interaction Patterns) - neither is the
    // one danger state.
    const presentation = claimStatusPresentation(claim('PAID'), t);

    expect(presentation.variant).toBe('success');
    expect(presentation.label).toBe(bg.claims.status.paid);
  });

  it('reads a rejected claim in the one danger treatment', () => {
    const presentation = claimStatusPresentation(claim('REJECTED'), t);

    expect(presentation.variant).toBe('danger');
    expect(presentation.label).toBe(bg.claims.status.rejected);
  });

  it('has a label and a variant for every one of the five fixed states', () => {
    const statuses: ClaimResponse['status'][] = [
      'SUBMITTED',
      'UNDER_REVIEW',
      'APPROVED',
      'REJECTED',
      'PAID',
    ];

    for (const status of statuses) {
      const presentation = claimStatusPresentation(claim(status), t);
      expect(presentation.label.length).toBeGreaterThan(0);
      expect(presentation.variant).toBeDefined();
    }
  });
});
