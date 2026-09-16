import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from '../../app/router';
import { seedToken } from '../../test/seedToken';
import { apiFetch } from '../../api/client';
import type { ClaimResponse } from './claimTypes';
import bg from '../../i18n/bg.json';

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

function sampleClaim(overrides: Partial<ClaimResponse> = {}): ClaimResponse {
  return {
    id: '22222222-2222-2222-2222-222222222222',
    claimNumber: 'CL-2026-00000042',
    policyId: '11111111-1111-1111-1111-111111111111',
    policyNumber: 'MI-2026-00000042',
    incidentDate: '2026-08-01',
    description: 'The other driver ran a red light and hit my rear bumper.',
    location: 'Sofia, near Orlov Most',
    status: 'SUBMITTED',
    submittedAt: '2026-08-02T09:00:00Z',
    attachments: [],
    statusHistory: [{ status: 'SUBMITTED', occurredAt: '2026-08-02T09:00:00Z' }],
    ...overrides,
  };
}

function renderAt(path = '/claims') {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

describe('MyClaims', () => {
  it('renders one row per claim, each row a single link target', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue([
      sampleClaim(),
      sampleClaim({ id: '33333333-3333-3333-3333-333333333333', claimNumber: 'CL-2026-00000043' }),
    ]);

    renderAt();

    const rows = await screen.findAllByTestId('claim-row');
    expect(rows).toHaveLength(2);
    // The whole row is the link, not a button inside a card (UX-DR4).
    expect(rows[0].tagName).toBe('A');
    expect(rows[0]).toHaveAttribute('href', '/claims/22222222-2222-2222-2222-222222222222');
    expect(rows[0]).toHaveTextContent('CL-2026-00000042');
  });

  it('shows the claim status via the shared badge/label mapping', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue([sampleClaim({ status: 'APPROVED' })]);

    renderAt();

    const row = await screen.findByTestId('claim-row');
    expect(row).toHaveTextContent(bg.claims.status.approved);
  });

  it('names the empty state cause with one action, never an error tone', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue([]);

    renderAt();

    const empty = await screen.findByTestId('claims-list-empty');
    expect(empty).toHaveTextContent(bg.claims.list.empty.body);
    expect(empty.innerHTML).not.toContain('danger');
    expect(screen.getByRole('button', { name: bg.claims.list.empty.cta })).toBeInTheDocument();
  });

  it('sends a client to their policies from the empty state', async () => {
    const user = userEvent.setup();
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue([]);

    const router = renderAt();

    await screen.findByTestId('claims-list-empty');
    await user.click(screen.getByRole('button', { name: bg.claims.list.empty.cta }));

    expect(router.state.location.pathname).toBe('/policies');
  });

  it('shows a spinner while loading, never a blank screen', async () => {
    seedToken('CLIENT');
    let release: (claims: ClaimResponse[]) => void = () => {};
    mockedApiFetch.mockImplementation((() => new Promise((resolve) => {
      release = resolve as (claims: ClaimResponse[]) => void;
    })) as never);

    renderAt();

    expect(await screen.findByTestId('claims-list-loading')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: bg.claims.list.heading })).toBeInTheDocument();

    release([sampleClaim()]);
    expect(await screen.findByTestId('claim-row')).toBeInTheDocument();
  });

  it('shows the error state and a working retry on a failed load', async () => {
    const user = userEvent.setup();
    seedToken('CLIENT');
    mockedApiFetch.mockRejectedValueOnce(new Error('network down'));

    renderAt();

    expect(await screen.findByTestId('claims-list-error')).toBeInTheDocument();

    mockedApiFetch.mockResolvedValue([sampleClaim()]);
    await user.click(screen.getByRole('button', { name: bg.claims.list.retry }));

    expect(await screen.findByTestId('claim-row')).toBeInTheDocument();
  });

  it('is reachable from the header nav for a client', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValue([]);

    renderAt('/');

    expect(await screen.findByRole('link', { name: bg.app.nav.myClaims })).toHaveAttribute(
      'href',
      '/claims',
    );
  });
});
