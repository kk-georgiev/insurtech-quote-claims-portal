import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { quoteStatusPresentation } from './quoteStatusPresentation';
import type { Translate } from '../../i18n/errorMessages';

// A minimal but real-shaped `t`: returns the key with its interpolated
// values appended, so assertions can check both which key resolved and
// what was passed to it, without pulling in i18next.
const t: Translate = (key, options) =>
  options ? `${key}:${JSON.stringify(options)}` : key;

const NOW = new Date('2026-09-01T00:00:00Z');

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
});

describe('quoteStatusPresentation', () => {
  it('ACCEPTED is always success, regardless of validUntil', () => {
    const result = quoteStatusPresentation(
      { status: 'ACCEPTED', validUntil: '2020-01-01' },
      t,
      'en',
    );
    expect(result.variant).toBe('success');
    expect(result.label).toBe('quotes.status.accepted');
  });

  it('EXPIRED is danger, labelled with the expiry date', () => {
    const result = quoteStatusPresentation(
      { status: 'EXPIRED', validUntil: '2026-08-20' },
      t,
      'en',
    );
    expect(result.variant).toBe('danger');
    expect(result.label).toContain('quotes.status.expiredOn');
  });

  it('CANCELLED (reserved, unreachable this milestone) maps to neutral', () => {
    const result = quoteStatusPresentation(
      { status: 'CANCELLED', validUntil: '2026-09-01' },
      t,
      'en',
    );
    expect(result.variant).toBe('neutral');
  });

  it('CALCULATED, far from validUntil, is info with the valid-until date', () => {
    const result = quoteStatusPresentation(
      { status: 'CALCULATED', validUntil: '2026-09-14' },
      t,
      'en',
    );
    expect(result.variant).toBe('info');
    expect(result.label).toContain('quotes.status.validUntil');
  });

  it('CALCULATED, within the expiring-soon window, is warning with a day count', () => {
    const result = quoteStatusPresentation(
      { status: 'CALCULATED', validUntil: '2026-09-03' },
      t,
      'en',
    );
    expect(result.variant).toBe('warning');
    expect(result.label).toContain('quotes.status.expiresInDays');
    expect(result.label).toContain('"count":2');
  });

  it('CALCULATED, valid until today, reads as expiring today, not a day count', () => {
    const result = quoteStatusPresentation(
      { status: 'CALCULATED', validUntil: '2026-09-01' },
      t,
      'en',
    );
    expect(result.variant).toBe('warning');
    expect(result.label).toBe('quotes.status.expiresToday');
  });
});
