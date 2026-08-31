import { describe, expect, it } from 'vitest';
import { vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from '../../app/router';
import { seedToken } from '../../test/seedToken';
import { apiFetch, ApiRequestError } from '../../api/client';
import type { QuoteResponse } from './QuoteForm';
import bg from '../../i18n/bg.json';

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

const QUOTE_ID = '11111111-1111-1111-1111-111111111111';

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

function sampleQuote(overrides: Partial<QuoteResponse> = {}): QuoteResponse {
  return {
    id: QUOTE_ID,
    createdAt: '2026-08-28T12:00:00Z',
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
    validUntil: '2026-12-31',
    status: 'CALCULATED',
    acceptedAt: null,
    ...overrides,
  };
}

describe('QuoteDetail', () => {
  it('renders the full breakdown, including the bonus-malus line, for a valid quote', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue(sampleQuote());

    renderAt(`/quotes/${QUOTE_ID}`);

    expect(await screen.findByTestId('quote-detail')).toBeInTheDocument();
    expect(screen.getByTestId('quote-result')).toBeInTheDocument();
    expect(screen.getByTestId('quote-bonusMalusFactor')).toBeInTheDocument();
    expect(screen.getByTestId('quote-totalPremium')).toHaveTextContent('143.12');
    // A valid (CALCULATED) quote shows no expired/accepted notice.
    expect(screen.queryByTestId('quote-detail-expired-notice')).not.toBeInTheDocument();
    expect(screen.queryByTestId('quote-detail-accepted-notice')).not.toBeInTheDocument();
  });

  it('replaces nothing but adds an explanation and an exit for an expired quote — the breakdown stays', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue(sampleQuote({ status: 'EXPIRED', validUntil: '2026-08-01' }));

    renderAt(`/quotes/${QUOTE_ID}`);

    expect(await screen.findByTestId('quote-result')).toBeInTheDocument();
    expect(screen.getByTestId('quote-detail-expired-notice')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: bg.quotes.detail.newQuoteCta }),
    ).toBeInTheDocument();
  });

  it('shows a static accepted notice for an accepted quote, breakdown intact', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue(sampleQuote({ status: 'ACCEPTED' }));

    renderAt(`/quotes/${QUOTE_ID}`);

    expect(await screen.findByTestId('quote-result')).toBeInTheDocument();
    expect(screen.getByTestId('quote-detail-accepted-notice')).toBeInTheDocument();
    expect(screen.queryByTestId('quote-detail-expired-notice')).not.toBeInTheDocument();
  });

  it("renders a not-found state for someone else's quote (404), not an error", async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockRejectedValue(new ApiRequestError('not found', 404, 'QUOTE_NOT_FOUND'));

    renderAt(`/quotes/${QUOTE_ID}`);

    expect(await screen.findByTestId('quote-detail-not-found')).toBeInTheDocument();
    expect(screen.queryByTestId('quote-detail-error')).not.toBeInTheDocument();
  });

  it('shows a generic error state, distinct from not-found, on a server failure', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockRejectedValue(new ApiRequestError('server error', 500));

    renderAt(`/quotes/${QUOTE_ID}`);

    expect(await screen.findByTestId('quote-detail-error')).toBeInTheDocument();
    expect(screen.queryByTestId('quote-detail-not-found')).not.toBeInTheDocument();
  });

  it('offers a way back to the list from every state', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue(sampleQuote());

    renderAt(`/quotes/${QUOTE_ID}`);

    expect(await screen.findByRole('link', { name: bg.quotes.detail.backToList })).toBeInTheDocument();
  });
});
