import { describe, expect, it } from 'vitest';
import i18n from '../../i18n';
import { policyStatusPresentation } from './policyStatusPresentation';
import { quoteStatusPresentation } from '../quote/quoteStatusPresentation';
import type { PolicyResponse } from './policyTypes';
import type { Translate } from '../../i18n/errorMessages';
import { formatDate } from '../../i18n/formatDate';
import bg from '../../i18n/bg.json';

const t: Translate = (key, options) => i18n.t(key, options) as string;

function policy(status: PolicyResponse['status']): Pick<
  PolicyResponse,
  'status' | 'coverageStart' | 'coverageEnd'
> {
  return { status, coverageStart: '2026-09-01', coverageEnd: '2027-08-31' };
}

describe('policyStatusPresentation', () => {
  it('reads an active policy as covered, in the success treatment', () => {
    const presentation = policyStatusPresentation(policy('ACTIVE'), t, 'bg');

    expect(presentation.variant).toBe('success');
    expect(presentation.label).toBe(
      bg.policies.status.activeUntil.replace('{{date}}', formatDate('2027-08-31', 'bg')),
    );
  });

  it('reads a scheduled policy as starting later, in the info treatment', () => {
    expect(policyStatusPresentation(policy('SCHEDULED'), t, 'bg').variant).toBe('info');
  });

  it('renders an expired policy neutral, never danger', () => {
    // UX-DR2, and the single deliberate difference from the quote's own
    // mapping: a policy that ran its full term is the successful outcome.
    // Colouring it red would report a failure where none happened.
    const presentation = policyStatusPresentation(policy('EXPIRED'), t, 'bg');

    expect(presentation.variant).toBe('neutral');
    expect(presentation.variant).not.toBe('danger');
  });

  it('differs from the quote mapping exactly where the UX says it must', () => {
    // Pins the contrast itself, so a later refactor that "unifies" the two
    // mappings cannot quietly make an expired policy look like a failure.
    const expiredPolicy = policyStatusPresentation(policy('EXPIRED'), t, 'bg');
    const expiredQuote = quoteStatusPresentation(
      { status: 'EXPIRED', validUntil: '2026-08-01' },
      t,
      'bg',
    );

    expect(expiredQuote.variant).toBe('danger');
    expect(expiredPolicy.variant).toBe('neutral');
  });

  it('has a label and a variant for the reserved cancelled state', () => {
    // No response can carry it this milestone, but a call site must not
    // fall through to undefined the day one can.
    const presentation = policyStatusPresentation(policy('CANCELLED'), t, 'bg');

    expect(presentation.variant).toBe('neutral');
    expect(presentation.label).toBe(bg.policies.status.cancelled);
  });
});
