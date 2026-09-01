import { describe, expect, it } from 'vitest';
import { vi } from 'vitest';
import { render, screen } from '@testing-library/react';
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

// --- Story 8.2: the acceptance section ---

describe('QuoteDetail acceptance section (Story 8.2)', () => {
  it('offers acceptance below the breakdown for a still-valid quote', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue(sampleQuote());

    renderAt(`/quotes/${QUOTE_ID}`);

    const form = await screen.findByTestId('accept-form');
    const breakdown = screen.getByTestId('quote-result');
    // Reading order (UX-DR5): what you are buying, then the commitment -
    // asserted on real document order, not on styling.
    expect(breakdown.compareDocumentPosition(form)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('is the only primary button on the screen', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue(sampleQuote());

    renderAt(`/quotes/${QUOTE_ID}`);
    await screen.findByTestId('accept-form');

    // `primary` is the navy-filled call to action (Button's cva variant);
    // a second one would leave the client with two competing commitments.
    const primaries = screen
      .getAllByRole('button')
      .filter((button) => button.className.includes('bg-primary'));
    expect(primaries).toHaveLength(1);
    expect(primaries[0]).toHaveTextContent(bg.quotes.accept.submit);
  });

  it.each(['EXPIRED', 'ACCEPTED'] as const)(
    'offers no acceptance affordance at all for a %s quote',
    async (status) => {
      seedToken('CLIENT');
      mockedApiFetch.mockResolvedValue(sampleQuote({ status, validUntil: '2026-08-01' }));

      renderAt(`/quotes/${QUOTE_ID}`);

      await screen.findByTestId('quote-detail');
      // Replaced, never merely disabled (UX EXPERIENCE.md, State Patterns).
      expect(screen.queryByTestId('accept-form')).not.toBeInTheDocument();
    },
  );

  it('re-reads the quote and re-renders as expired when the offer dies mid-screen', async () => {
    const user = userEvent.setup();
    seedToken('CLIENT');
    // First load: still acceptable. The accept call is refused because the
    // offer expired in the meantime; the re-read then returns the expired
    // quote (UX-DR8).
    mockedApiFetch
      .mockResolvedValueOnce(sampleQuote())
      .mockRejectedValueOnce(new ApiRequestError('dev prose', 409, 'QUOTE_EXPIRED'))
      .mockResolvedValueOnce(sampleQuote({ status: 'EXPIRED', validUntil: '2026-08-01' }));

    renderAt(`/quotes/${QUOTE_ID}`);
    await screen.findByTestId('accept-form');
    await user.type(screen.getByLabelText(bg.quotes.accept.holderName), 'Ivan Petrov');
    await user.type(screen.getByLabelText(bg.quotes.accept.vehicleRegistration), 'CA1234BM');
    await user.click(screen.getByRole('button', { name: bg.quotes.accept.submit }));

    // The screen itself is now wrong, so it corrects itself rather than
    // leaving a form that asserts acceptability from a stale fetch.
    expect(await screen.findByTestId('quote-detail-expired-notice')).toBeInTheDocument();
    expect(screen.queryByTestId('accept-form')).not.toBeInTheDocument();
  });
});
