import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

function sampleQuote(overrides: Partial<QuoteResponse> = {}): QuoteResponse {
  return {
    id: '11111111-1111-1111-1111-111111111111',
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

describe('MyQuotes', () => {
  it('shows a loading state, then the populated list', async () => {
    seedToken('CLIENT');
    let resolveFetch: (value: QuoteResponse[]) => void;
    mockedApiFetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve;
        }),
    );

    renderAt('/quotes');

    expect(await screen.findByTestId('quotes-list-loading')).toBeInTheDocument();

    resolveFetch!([sampleQuote()]);

    expect(await screen.findByTestId('quotes-list')).toBeInTheDocument();
    expect(screen.queryByTestId('quotes-list-loading')).not.toBeInTheDocument();
  });

  it('renders one row per quote, newest first as returned by the backend', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue([
      sampleQuote({ id: '22222222-2222-2222-2222-222222222222', totalPremium: 200 }),
      sampleQuote({ id: '11111111-1111-1111-1111-111111111111', totalPremium: 143.12 }),
    ]);

    renderAt('/quotes');

    const rows = await screen.findAllByTestId('quote-row');
    expect(rows).toHaveLength(2);
    // The whole row is one link target (UX-DR4) - not a card with a button inside.
    expect(rows[0].tagName).toBe('A');
    expect(rows[0]).toHaveAttribute('href', '/quotes/22222222-2222-2222-2222-222222222222');
  });

  it('shows the empty state, not an error, when there are no quotes yet', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue([]);

    renderAt('/quotes');

    expect(await screen.findByTestId('quotes-list-empty')).toBeInTheDocument();
    expect(screen.getByText(bg.quotes.list.empty.title)).toBeInTheDocument();
    expect(screen.queryByTestId('quotes-list-error')).not.toBeInTheDocument();
  });

  it('shows the error state and a working retry on a failed load', async () => {
    const user = userEvent.setup();
    seedToken('CLIENT');
    mockedApiFetch.mockRejectedValueOnce(new ApiRequestError('failed', 500));
    mockedApiFetch.mockResolvedValueOnce([sampleQuote()]);

    renderAt('/quotes');

    expect(await screen.findByTestId('quotes-list-error')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: bg.quotes.list.retry }));

    expect(await screen.findByTestId('quotes-list')).toBeInTheDocument();
  });

  it('the screen title stays visible across every state', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue([]);

    renderAt('/quotes');

    expect(
      await screen.findByRole('heading', { name: bg.quotes.list.heading }),
    ).toBeInTheDocument();
  });

  it("navigating a row's link opens that quote's detail screen", async () => {
    const user = userEvent.setup();
    seedToken('CLIENT');
    mockedApiFetch.mockImplementation((path) => {
      if (path === '/api/v1/quotes') return Promise.resolve([sampleQuote()]);
      return Promise.resolve(sampleQuote());
    });

    renderAt('/quotes');
    const row = await screen.findByTestId('quote-row');
    await user.click(row);

    await waitFor(() => {
      expect(screen.getByTestId('quote-detail')).toBeInTheDocument();
    });
  });
});
