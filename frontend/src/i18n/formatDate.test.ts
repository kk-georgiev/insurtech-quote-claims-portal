import { afterEach, describe, expect, it } from 'vitest';
import { formatDate } from './formatDate';

describe('formatDate', () => {
  const originalTz = process.env.TZ;

  afterEach(() => {
    process.env.TZ = originalTz;
  });

  it('renders a bare LocalDate string the same day regardless of the viewer local zone', () => {
    // A bare `LocalDate` (no time component) parses as UTC midnight. A
    // viewer at a negative UTC offset (e.g. US Pacific, UTC-8) would see it
    // roll back to the previous day if this formatter read the platform's
    // local zone instead of pinning to UTC (Epic 6 retro item 46).
    process.env.TZ = 'America/Los_Angeles';

    expect(formatDate('2026-01-01', 'en')).toBe('January 1, 2026');
  });

  it('renders an Instant near UTC midnight on its UTC calendar day, not the viewer local day', () => {
    process.env.TZ = 'America/Los_Angeles';

    // 2026-01-01T02:00:00Z is 2025-12-31 18:00 in America/Los_Angeles - the
    // exact case a local-zone render would get wrong.
    expect(formatDate('2026-01-01T02:00:00Z', 'en')).toBe('January 1, 2026');
  });

  it('is stable across UTC-positive and UTC-negative viewer zones for the same input', () => {
    const input = '2026-06-15T10:00:00Z';

    process.env.TZ = 'Pacific/Kiritimati'; // UTC+14
    const fromPositiveOffset = formatDate(input, 'en');

    process.env.TZ = 'Etc/GMT+12'; // UTC-12
    const fromNegativeOffset = formatDate(input, 'en');

    expect(fromPositiveOffset).toBe(fromNegativeOffset);
    expect(fromPositiveOffset).toBe('June 15, 2026');
  });

  it('renders in the requested language convention', () => {
    process.env.TZ = 'Europe/Sofia';

    expect(formatDate('2026-09-14', 'bg')).toBe('14 септември 2026 г.');
  });
});
