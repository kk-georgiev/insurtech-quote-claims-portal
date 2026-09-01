import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from '../../app/router';
import { seedToken } from '../../test/seedToken';
import { apiFetch } from '../../api/client';
import type { PolicyResponse } from './policyTypes';
import bg from '../../i18n/bg.json';

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

function samplePolicy(overrides: Partial<PolicyResponse> = {}): PolicyResponse {
  return {
    id: '22222222-2222-2222-2222-222222222222',
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

function renderAt(path = '/policies') {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

/** The list request resolves; the quote probe only happens when it is empty. */
function respondWith(policies: PolicyResponse[], quotes: unknown[] = []) {
  mockedApiFetch.mockImplementation(((path: string) =>
    Promise.resolve(path.startsWith('/api/v1/policies') ? policies : quotes)) as never);
}

describe('MyPolicies', () => {
  it('renders one row per policy, each row a single link target', async () => {
    seedToken('CLIENT');
    respondWith([samplePolicy(), samplePolicy({ id: '33333333-3333-3333-3333-333333333333' })]);

    renderAt();

    const rows = await screen.findAllByTestId('policy-row');
    expect(rows).toHaveLength(2);
    // The whole row is the link, not a button inside a card (UX-DR4).
    expect(rows[0].tagName).toBe('A');
    expect(rows[0]).toHaveAttribute('href', '/policies/22222222-2222-2222-2222-222222222222');
    expect(rows[0]).toHaveTextContent('MI-2026-00000042');
  });

  it('renders an ended policy without any danger styling', async () => {
    seedToken('CLIENT');
    respondWith([samplePolicy({ status: 'EXPIRED' })]);

    renderAt();

    const row = await screen.findByTestId('policy-row');
    // UX-DR2: a policy that ran its term is the success case. The badge is
    // the muted treatment, and nothing on the row reads as an error.
    expect(row.className).not.toContain('danger');
    expect(row.innerHTML).not.toContain('danger');
    expect(row).toHaveTextContent(
      bg.policies.status.expiredOn.replace('{{date}}', '31 август 2027 г.'),
    );
  });

  it('points a client who has quotes at their quotes', async () => {
    seedToken('CLIENT');
    respondWith([], [{ id: 'q1' }]);

    renderAt();

    expect(await screen.findByTestId('policies-list-empty')).toHaveTextContent(
      bg.policies.list.empty.fromQuotes,
    );
    expect(screen.getByRole('button', { name: bg.policies.list.empty.fromQuotesCta })).toBeInTheDocument();
  });

  it('points a client with no quotes either at the quote form instead', async () => {
    const user = userEvent.setup();
    seedToken('CLIENT');
    respondWith([], []);

    const router = renderAt();

    // Nobody is sent to a second empty screen (UX-DR6).
    expect(await screen.findByTestId('policies-list-empty')).toHaveTextContent(
      bg.policies.list.empty.noQuotes,
    );
    await user.click(screen.getByRole('button', { name: bg.policies.list.empty.noQuotesCta }));
    expect(router.state.location.pathname).toBe('/');
  });

  it('shows a spinner while loading, never a blank screen', async () => {
    seedToken('CLIENT');
    let release: (policies: PolicyResponse[]) => void = () => {};
    mockedApiFetch.mockImplementation((() => new Promise((resolve) => {
      release = resolve as (policies: PolicyResponse[]) => void;
    })) as never);

    renderAt();

    // UX-DR6: every state says something. The heading is stable across all
    // four, so the screen's identity does not flicker between them.
    expect(await screen.findByTestId('policies-list-loading')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: bg.policies.list.heading })).toBeInTheDocument();

    release([samplePolicy()]);
    expect(await screen.findByTestId('policy-row')).toBeInTheDocument();
  });

  it('shows the error state and a working retry on a failed load', async () => {
    const user = userEvent.setup();
    seedToken('CLIENT');
    mockedApiFetch.mockRejectedValueOnce(new Error('network down'));

    renderAt();

    expect(await screen.findByTestId('policies-list-error')).toBeInTheDocument();

    respondWith([samplePolicy()]);
    await user.click(screen.getByRole('button', { name: bg.policies.list.retry }));

    expect(await screen.findByTestId('policy-row')).toBeInTheDocument();
  });

  it('is reachable from the header nav for a client', async () => {
    seedToken('CLIENT');
    respondWith([]);

    renderAt('/');

    expect(await screen.findByRole('link', { name: bg.app.nav.myPolicies })).toHaveAttribute(
      'href',
      '/policies',
    );
  });
});
