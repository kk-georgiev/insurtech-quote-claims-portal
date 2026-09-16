import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from '../../app/router';
import { seedToken } from '../../test/seedToken';
import { apiFetch, ApiRequestError } from '../../api/client';
import type { ClaimResponse } from './claimTypes';
import bg from '../../i18n/bg.json';

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

const CLAIM_ID = '22222222-2222-2222-2222-222222222222';
const OTHER_CLAIM_ID = '44444444-4444-4444-4444-444444444444';

function sampleClaim(overrides: Partial<ClaimResponse> = {}): ClaimResponse {
  return {
    id: CLAIM_ID,
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

function renderAt(path = `/claims/${CLAIM_ID}`) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

/**
 * Routes `apiFetch` calls by shape, the way the real backend distinguishes
 * them by path: a `responseType: 'blob'` call is always this screen's
 * per-attachment image fetch, everything else is the claim detail JSON GET.
 * `claimsById` maps a claim id to what its detail fetch resolves with.
 */
function respondWith(claimsById: Record<string, ClaimResponse>) {
  mockedApiFetch.mockImplementation(((path: string, options?: { responseType?: string }) => {
    if (options?.responseType === 'blob') {
      return Promise.resolve(new Blob(['fake-image-bytes'], { type: 'image/jpeg' }));
    }
    const match = /\/api\/v1\/claims\/([^/]+)$/.exec(path);
    const claim = match ? claimsById[match[1]] : undefined;
    if (!claim) {
      return Promise.reject(new ApiRequestError('dev prose', 404, 'CLAIM_NOT_FOUND'));
    }
    return Promise.resolve(claim);
  }) as never);
}

let createObjectURLMock: ReturnType<typeof vi.fn>;
let revokeObjectURLMock: ReturnType<typeof vi.fn>;
let urlCounter: number;

beforeEach(() => {
  urlCounter = 0;
  createObjectURLMock = vi.fn(() => `blob:mock-${urlCounter++}`);
  revokeObjectURLMock = vi.fn();
  URL.createObjectURL = createObjectURLMock;
  URL.revokeObjectURL = revokeObjectURLMock;
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ClaimDetail', () => {
  it('renders the claim facts and its synthetic one-entry status history', async () => {
    seedToken('CLIENT');
    respondWith({ [CLAIM_ID]: sampleClaim() });

    renderAt();

    expect(await screen.findByTestId('claim-detail')).toBeInTheDocument();
    expect(screen.getByTestId('claim-number')).toHaveTextContent('CL-2026-00000042');
    expect(screen.getByTestId('claim-description')).toHaveTextContent(
      'The other driver ran a red light and hit my rear bumper.',
    );
    expect(screen.getByTestId('claim-policy-number')).toHaveTextContent('MI-2026-00000042');
    expect(screen.getByTestId('claim-location')).toHaveTextContent('Sofia, near Orlov Most');
    expect(screen.getByTestId('claim-detail-status')).toHaveTextContent(bg.claims.status.submitted);

    // Story 10.4 Boundaries: exactly the one SUBMITTED entry this story has.
    const historyItems = screen.getByTestId('claim-status-history').querySelectorAll('li');
    expect(historyItems).toHaveLength(1);
    expect(historyItems[0]).toHaveTextContent(bg.claims.status.submitted);
  });

  it('renders someone elses claim exactly like one that does not exist', async () => {
    seedToken('CLIENT');
    respondWith({});

    renderAt();

    // The backend answers 404 either way (M4-AD-12/AD-10) and this screen
    // must not distinguish them, or it would leak that the id is real.
    expect(await screen.findByTestId('claim-detail-not-found')).toHaveTextContent(
      bg.claims.detail.notFound,
    );
  });

  it('shows the error state on a non-404 failure', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockRejectedValue(new Error('network down'));

    renderAt();

    expect(await screen.findByTestId('claim-detail-error')).toBeInTheDocument();
    expect(screen.queryByTestId('claim-detail-not-found')).not.toBeInTheDocument();
  });

  it('fetches each photo as an authenticated blob and renders it linked to itself', async () => {
    seedToken('CLIENT');
    respondWith({
      [CLAIM_ID]: sampleClaim({
        attachments: [
          { id: 'a1', displayFilename: 'crash1.jpg', contentType: 'image/jpeg', sizeBytes: 100, uploadedAt: '2026-08-02T09:00:00Z' },
          { id: 'a2', displayFilename: 'crash2.jpg', contentType: 'image/jpeg', sizeBytes: 100, uploadedAt: '2026-08-02T09:00:00Z' },
        ],
      }),
    });

    renderAt();

    const links = await screen.findAllByTestId('claim-attachment-link');
    expect(links).toHaveLength(2);
    for (const link of links) {
      const img = link.querySelector('img');
      expect(img).not.toBeNull();
      // Same object URL for both the thumbnail and its wrapping link
      // (spec Boundaries).
      expect(link).toHaveAttribute('href', img?.getAttribute('src'));
      expect(link).toHaveAttribute('target', '_blank');
    }

    // Two attachments -> two authenticated per-attachment blob fetches, each
    // requesting bytes rather than JSON (spec I/O matrix: "2 authenticated
    // blob fetches").
    const blobCalls = mockedApiFetch.mock.calls.filter(
      ([, options]) => (options as { responseType?: string } | undefined)?.responseType === 'blob',
    );
    expect(blobCalls).toHaveLength(2);
    expect(blobCalls.map(([path]) => path)).toEqual([
      `/api/v1/claims/${CLAIM_ID}/attachments/a1`,
      `/api/v1/claims/${CLAIM_ID}/attachments/a2`,
    ]);
  });

  it('revokes every object URL it created when the screen unmounts', async () => {
    seedToken('CLIENT');
    respondWith({
      [CLAIM_ID]: sampleClaim({
        attachments: [
          { id: 'a1', displayFilename: 'crash1.jpg', contentType: 'image/jpeg', sizeBytes: 100, uploadedAt: '2026-08-02T09:00:00Z' },
          { id: 'a2', displayFilename: 'crash2.jpg', contentType: 'image/jpeg', sizeBytes: 100, uploadedAt: '2026-08-02T09:00:00Z' },
        ],
      }),
    });

    const router = createMemoryRouter(routes, { initialEntries: [`/claims/${CLAIM_ID}`] });
    const { unmount } = render(<RouterProvider router={router} />);

    expect(await screen.findAllByTestId('claim-attachment-link')).toHaveLength(2);
    expect(createObjectURLMock).toHaveBeenCalledTimes(2);
    expect(revokeObjectURLMock).not.toHaveBeenCalled();

    unmount();

    expect(revokeObjectURLMock).toHaveBeenCalledTimes(2);
    expect(revokeObjectURLMock).toHaveBeenCalledWith('blob:mock-0');
    expect(revokeObjectURLMock).toHaveBeenCalledWith('blob:mock-1');
  });

  it('revokes the previous claims object URLs when navigating straight to another claim', async () => {
    seedToken('CLIENT');
    respondWith({
      [CLAIM_ID]: sampleClaim({
        attachments: [
          { id: 'a1', displayFilename: 'crash1.jpg', contentType: 'image/jpeg', sizeBytes: 100, uploadedAt: '2026-08-02T09:00:00Z' },
        ],
      }),
      [OTHER_CLAIM_ID]: sampleClaim({
        id: OTHER_CLAIM_ID,
        claimNumber: 'CL-2026-00000099',
        attachments: [
          { id: 'b1', displayFilename: 'other1.jpg', contentType: 'image/jpeg', sizeBytes: 100, uploadedAt: '2026-08-03T09:00:00Z' },
        ],
      }),
    });

    const router = renderAt();

    expect(await screen.findAllByTestId('claim-attachment-link')).toHaveLength(1);
    expect(createObjectURLMock).toHaveBeenCalledTimes(1);
    const firstClaimUrl = createObjectURLMock.mock.results[0].value as string;

    await router.navigate(`/claims/${OTHER_CLAIM_ID}`);

    await waitFor(() => {
      expect(screen.getByTestId('claim-number')).toHaveTextContent('CL-2026-00000099');
    });
    // The first claim's URL is revoked before/on the transition to the
    // second (spec Boundaries) - not merely eventually, and not still live.
    expect(revokeObjectURLMock).toHaveBeenCalledWith(firstClaimUrl);

    await waitFor(() => {
      expect(screen.getAllByTestId('claim-attachment-link')).toHaveLength(1);
    });
    expect(createObjectURLMock).toHaveBeenCalledTimes(2);
  });
});
