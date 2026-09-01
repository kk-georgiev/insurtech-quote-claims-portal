import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from '../../app/router';
import { seedToken } from '../../test/seedToken';
import { apiFetch, ApiRequestError } from '../../api/client';
import type { PolicyResponse } from './policyTypes';
import bg from '../../i18n/bg.json';

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

const POLICY_ID = '22222222-2222-2222-2222-222222222222';

function samplePolicy(overrides: Partial<PolicyResponse> = {}): PolicyResponse {
  return {
    id: POLICY_ID,
    policyNumber: 'MI-2026-00000042',
    quoteId: '11111111-1111-1111-1111-111111111111',
    issuedAt: '2026-09-01T09:00:00Z',
    coverageStart: '2026-09-01',
    coverageEnd: '2027-08-31',
    holderName: 'Ivan Petrov',
    vehicleRegistration: 'CA1234BM',
    vehicleVin: null,
    driverAge: 30,
    regionCode: 'KH',
    engineCc: 1500,
    zoneId: 1,
    zoneName: 'Zone 1',
    basePremium: 141.12,
    ageSurcharge: 0,
    bonusMalusClass: 'NEUTRAL',
    bonusMalusFactor: 1,
    oneTimePremium: 141.12,
    installments: 2,
    installmentFee: 2,
    totalPremium: 143.12,
    installmentAmount: 71.56,
    currency: 'EUR',
    status: 'ACTIVE',
    ...overrides,
  };
}

function renderAt(path = `/policies/${POLICY_ID}`) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

describe('PolicyDetail', () => {
  it('shows the three facts a client opens this screen to check', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue(samplePolicy());

    renderAt();

    expect(await screen.findByTestId('policy-detail')).toBeInTheDocument();
    expect(screen.getByTestId('policy-number')).toHaveTextContent('MI-2026-00000042');
    expect(screen.getByTestId('policy-coverage-period')).toHaveTextContent('1 септември 2026 г.');
    expect(screen.getByTestId('policy-coverage-period')).toHaveTextContent('31 август 2027 г.');
    expect(screen.getByTestId('policy-total-premium')).toHaveTextContent('143.12');
    expect(screen.getByTestId('policy-vehicle')).toHaveTextContent('CA1234BM');
  });

  it('renders the number, period and premium heavier than their labels', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue(samplePolicy());

    renderAt();

    // UX-DR12: the values a client came to read outrank their labels.
    for (const testId of ['policy-number', 'policy-coverage-period', 'policy-total-premium']) {
      const value = await screen.findByTestId(testId);
      expect(value.className).toContain('font-semibold');
      expect(value.className).toContain('text-xl');
      expect(value.previousElementSibling?.className).toContain('text-sm');
    }
  });

  it('shows the same breakdown component the quote screens use', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue(samplePolicy());

    renderAt();

    // FR-M3-07/FR-M3-10: not a lookalike - the very same component, so a
    // client comparing policy against quote sees the figures match.
    expect(await screen.findByTestId('quote-result')).toBeInTheDocument();
    expect(screen.getByTestId('quote-basePremium')).toHaveTextContent('141.12');
    expect(screen.getByTestId('quote-bonusMalusFactor')).toBeInTheDocument();
    expect(screen.getByTestId('quote-totalPremium')).toHaveTextContent('143.12');
  });

  it('falls back to the VIN when the vehicle has no registration', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue(
      samplePolicy({ vehicleRegistration: null, vehicleVin: 'WDB1234567N123456' }),
    );

    renderAt();

    expect(await screen.findByTestId('policy-vehicle')).toHaveTextContent('WDB1234567N123456');
  });

  it('renders someone elses policy exactly like one that does not exist', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockRejectedValue(new ApiRequestError('dev prose', 404, 'POLICY_NOT_FOUND'));

    renderAt();

    // The backend answers 404 either way (AD-10) and this screen must not
    // distinguish them, or it would leak that the id is real.
    expect(await screen.findByTestId('policy-detail-not-found')).toHaveTextContent(
      bg.policies.detail.notFound,
    );
  });

  it('shows the error state on a non-404 failure', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockRejectedValue(new Error('network down'));

    renderAt();

    expect(await screen.findByTestId('policy-detail-error')).toBeInTheDocument();
    expect(screen.queryByTestId('policy-detail-not-found')).not.toBeInTheDocument();
  });
});
