import { describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from '../../app/router';
import { seedToken } from '../../test/seedToken';
import { apiFetch, ApiRequestError } from '../../api/client';
import type { PolicyResponse } from '../policy/policyTypes';
import type { ClaimResponse } from './claimTypes';
import bg from '../../i18n/bg.json';

// `apiFetch` is the only seam mocked, same pattern as `QuoteForm.test.tsx`/
// `QuoteDetail.test.tsx` - no backend, no network. This screen makes two
// different calls (the policy load, then the claim submission), so most
// tests chain `mockResolvedValueOnce`/`mockRejectedValueOnce` in call order
// rather than a single blanket `mockResolvedValue`.
vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

const POLICY_ID = '22222222-2222-2222-2222-222222222222';
const CLAIM_ID = '33333333-3333-3333-3333-333333333333';

function samplePolicy(overrides: Partial<PolicyResponse> = {}): PolicyResponse {
  return {
    id: POLICY_ID,
    policyNumber: 'MI-2026-00000042',
    quoteId: '11111111-1111-1111-1111-111111111111',
    issuedAt: '2026-01-01T09:00:00Z',
    // Deliberately in the past relative to any plausible test run, so the
    // incident date's `max` bound is deterministically `coverageEnd`, not
    // "today" - assertions on the native attribute don't need to know what
    // day the suite runs on.
    coverageStart: '2020-01-01',
    coverageEnd: '2020-12-31',
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
    status: 'EXPIRED',
    ...overrides,
  };
}

function sampleClaim(overrides: Partial<ClaimResponse> = {}): ClaimResponse {
  return {
    id: CLAIM_ID,
    claimNumber: 'CL-2026-00000001',
    policyId: POLICY_ID,
    policyNumber: 'MI-2026-00000042',
    incidentDate: '2020-06-01',
    description: 'Rear bumper damage in a parking-lot collision.',
    location: 'Sofia, near NDK',
    status: 'SUBMITTED',
    submittedAt: '2020-06-02T10:00:00Z',
    attachments: [],
    statusHistory: [{ status: 'SUBMITTED', occurredAt: '2020-06-02T10:00:00Z' }],
    ...overrides,
  };
}

function renderAt(path = `/policies/${POLICY_ID}/claims/new`) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

function imageFile(name: string, sizeBytes = 1024, type = 'image/png'): File {
  return new File([new Uint8Array(sizeBytes)], name, { type });
}

const descriptionField = () => screen.getByLabelText(bg.claims.form.description);
const incidentDateField = () => screen.getByLabelText(bg.claims.form.incidentDate);
const locationField = () => screen.getByLabelText(bg.claims.form.location);
const attachmentsField = () => screen.getByLabelText(bg.claims.form.attachments);
const submitButton = () => screen.getByRole('button', { name: bg.claims.form.submit });

const VALID_INPUT = {
  description: 'Rear bumper damage in a parking-lot collision.',
  incidentDate: '2020-06-01',
  location: 'Sofia, near NDK',
};

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(descriptionField(), VALID_INPUT.description);
  await user.clear(incidentDateField());
  await user.type(incidentDateField(), VALID_INPUT.incidentDate);
  await user.type(locationField(), VALID_INPUT.location);
}

describe('FnolForm', () => {
  it('loads the policy and renders the fields in reading order: which policy, what happened, when, where, photos, submit', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValueOnce(samplePolicy());

    renderAt();

    expect(await screen.findByTestId('fnol-form')).toBeInTheDocument();
    expect(screen.getByTestId('fnol-policy-label')).toHaveTextContent('MI-2026-00000042');

    const policyLabel = screen.getByTestId('fnol-policy-label');
    const description = descriptionField();
    const incidentDate = incidentDateField();
    const location = locationField();
    const attachments = attachmentsField();
    const submit = submitButton();

    // Real document order, not styling (mirrors QuoteDetail's own reading-
    // order assertion).
    expect(policyLabel.compareDocumentPosition(description)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(description.compareDocumentPosition(incidentDate)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(incidentDate.compareDocumentPosition(location)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(location.compareDocumentPosition(attachments)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(attachments.compareDocumentPosition(submit)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('mirrors the policy coverage window on the incident date input min/max', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValueOnce(samplePolicy());

    renderAt();

    const input = await screen.findByLabelText(bg.claims.form.incidentDate);
    expect(input).toHaveAttribute('min', '2020-01-01');
    // coverageEnd (2020-12-31) is earlier than "today" whenever this suite
    // runs, so max is deterministically the coverage end, not today.
    expect(input).toHaveAttribute('max', '2020-12-31');
  });

  it("renders the same not-found screen as PolicyDetail for a policy that isn't the client's own", async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockRejectedValueOnce(new ApiRequestError('dev prose', 404, 'POLICY_NOT_FOUND'));

    renderAt();

    expect(await screen.findByTestId('fnol-not-found')).toHaveTextContent(bg.policies.detail.notFound);
    expect(screen.queryByTestId('fnol-form')).not.toBeInTheDocument();
  });

  it('shows an error state, distinct from not-found, on a non-404 failure loading the policy', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockRejectedValueOnce(new Error('network down'));

    renderAt();

    expect(await screen.findByTestId('fnol-error')).toBeInTheDocument();
    expect(screen.queryByTestId('fnol-not-found')).not.toBeInTheDocument();
  });

  it('lists a valid photo with its name and size before submit', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValueOnce(samplePolicy());
    const user = userEvent.setup();

    renderAt();
    await screen.findByTestId('fnol-form');

    await user.upload(attachmentsField(), imageFile('damage.jpg', 2048, 'image/jpeg'));

    const list = screen.getByTestId('fnol-accepted-files');
    expect(list).toHaveTextContent('damage.jpg');
    expect(screen.queryByTestId('fnol-rejected-files')).not.toBeInTheDocument();
  });

  it('excludes an oversized file client-side, lists it with a reason, and sends no request', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValueOnce(samplePolicy());
    const user = userEvent.setup();

    renderAt();
    await screen.findByTestId('fnol-form');

    const oversized = imageFile('huge.jpg', 6 * 1024 * 1024, 'image/jpeg'); // 6 MB > 5 MiB cap
    await user.upload(attachmentsField(), oversized);

    expect(screen.getByTestId('fnol-rejected-files')).toHaveTextContent('huge.jpg');
    expect(screen.getByTestId('fnol-rejected-files')).toHaveTextContent(bg.errors.codes.ATTACHMENT_TOO_LARGE);
    expect(screen.queryByTestId('fnol-accepted-files')).not.toBeInTheDocument();
    // Only the initial policy GET happened - no submit occurred.
    expect(mockedApiFetch).toHaveBeenCalledTimes(1);
  });

  it('excludes a non-image file client-side and lists it with a reason', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValueOnce(samplePolicy());

    renderAt();
    await screen.findByTestId('fnol-form');

    const pdf = new File(['%PDF-1.4'], 'report.pdf', { type: 'application/pdf' });
    // `userEvent.upload` itself filters candidates against the input's
    // `accept` attribute before they ever reach the input's `files` list -
    // exactly the browser's own file-picker behaviour, which is precisely
    // what a client bypassing it (e.g. "All Files" in the OS dialog) does
    // not get. `fireEvent` sets the input's files directly, bypassing that
    // filtering, so this exercises this component's *own* type screening
    // rather than the browser's.
    fireEvent.change(attachmentsField(), { target: { files: [pdf] } });

    expect(screen.getByTestId('fnol-rejected-files')).toHaveTextContent('report.pdf');
    expect(screen.getByTestId('fnol-rejected-files')).toHaveTextContent(
      bg.errors.codes.ATTACHMENT_UNSUPPORTED_TYPE,
    );
    expect(screen.queryByTestId('fnol-accepted-files')).not.toBeInTheDocument();
  });

  it('excludes files beyond the 10-file cap and lists the excess with a reason', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValueOnce(samplePolicy());
    const user = userEvent.setup();

    renderAt();
    await screen.findByTestId('fnol-form');

    const files = Array.from({ length: 11 }, (_, i) => imageFile(`photo-${i + 1}.jpg`, 1024, 'image/jpeg'));
    await user.upload(attachmentsField(), files);

    const accepted = screen.getByTestId('fnol-accepted-files');
    const rejected = screen.getByTestId('fnol-rejected-files');
    expect(accepted.querySelectorAll('li')).toHaveLength(10);
    expect(rejected).toHaveTextContent('photo-11.jpg');
    expect(rejected).toHaveTextContent(bg.errors.codes.ATTACHMENT_TOO_MANY);
  });

  it('submits the multipart FormData with the exact contract field names, including photos', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValueOnce(samplePolicy()).mockResolvedValueOnce(sampleClaim());
    const user = userEvent.setup();

    renderAt();
    await screen.findByTestId('fnol-form');

    await fillForm(user);
    await user.upload(attachmentsField(), [
      imageFile('one.jpg', 1024, 'image/jpeg'),
      imageFile('two.png', 2048, 'image/png'),
    ]);
    await user.click(submitButton());

    await waitFor(() => expect(mockedApiFetch).toHaveBeenCalledTimes(2));
    const [path, options] = mockedApiFetch.mock.calls[1];
    expect(path).toBe('/api/v1/claims');
    expect(options).toMatchObject({ method: 'POST', authenticated: true });

    const body = options?.body as FormData;
    expect(body).toBeInstanceOf(FormData);
    expect(body.get('policyId')).toBe(POLICY_ID);
    expect(body.get('incidentDate')).toBe(VALID_INPUT.incidentDate);
    expect(body.get('description')).toBe(VALID_INPUT.description);
    expect(body.get('location')).toBe(VALID_INPUT.location);
    const attachments = body.getAll('attachments') as File[];
    expect(attachments).toHaveLength(2);
    expect(attachments.map((f) => f.name)).toEqual(['one.jpg', 'two.png']);
  });

  it('submits with zero attachment entries when no photos are chosen', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValueOnce(samplePolicy()).mockResolvedValueOnce(sampleClaim());
    const user = userEvent.setup();

    renderAt();
    await screen.findByTestId('fnol-form');
    await fillForm(user);
    await user.click(submitButton());

    await waitFor(() => expect(mockedApiFetch).toHaveBeenCalledTimes(2));
    const [, options] = mockedApiFetch.mock.calls[1];
    const body = options?.body as FormData;
    expect(body.getAll('attachments')).toHaveLength(0);
  });

  it('shows the inline success confirmation with the claim number and a link back to the policy', async () => {
    seedToken('CLIENT');
    mockedApiFetch
      .mockResolvedValueOnce(samplePolicy())
      .mockResolvedValueOnce(sampleClaim({ claimNumber: 'CL-2026-00000007' }));
    const user = userEvent.setup();

    renderAt();
    await screen.findByTestId('fnol-form');
    await fillForm(user);
    await user.click(submitButton());

    expect(await screen.findByTestId('fnol-success')).toBeInTheDocument();
    expect(screen.getByTestId('fnol-success-claim-number')).toHaveTextContent('CL-2026-00000007');
    const link = screen.getByRole('link', { name: bg.claims.form.success.backToPolicy });
    expect(link).toHaveAttribute('href', `/policies/${POLICY_ID}`);
    // No navigate to /claims/:id (doesn't exist until Story 10.4) - the form
    // itself is gone, replaced in place.
    expect(screen.queryByTestId('fnol-form')).not.toBeInTheDocument();
  });

  it('renders a field-level error under incident date for a future-incident-date rejection', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValueOnce(samplePolicy()).mockRejectedValueOnce(
      new ApiRequestError('dev prose', 400, 'CLAIM_INCIDENT_DATE_IN_FUTURE', [
        { field: 'incidentDate', message: 'The incident date cannot be in the future' },
      ]),
    );
    const user = userEvent.setup();

    renderAt();
    await screen.findByTestId('fnol-form');
    await fillForm(user);
    await user.click(submitButton());

    expect(await screen.findByText(bg.errors.codes.CLAIM_INCIDENT_DATE_IN_FUTURE)).toBeInTheDocument();
    expect(screen.queryByTestId('fnol-form-error')).not.toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  // Verified against ClaimIncidentOutsideCoverageException.java: this
  // rejection carries no `fieldErrors` (409, conflict-with-state - not a
  // per-field validation problem), unlike the future-date case above. It
  // therefore surfaces through the shared hook's form-level fallback, not
  // as a field error - the FIELD_SPECIFIC_CODES entry is registered for
  // forward compatibility, but current backend behaviour resolves it here.
  it('renders a form-level error for an outside-coverage rejection (no fieldErrors on this exception)', async () => {
    seedToken('CLIENT');
    mockedApiFetch
      .mockResolvedValueOnce(samplePolicy())
      .mockRejectedValueOnce(new ApiRequestError('dev prose', 409, 'CLAIM_INCIDENT_OUTSIDE_COVERAGE'));
    const user = userEvent.setup();

    renderAt();
    await screen.findByTestId('fnol-form');
    await fillForm(user);
    await user.click(submitButton());

    expect(await screen.findByTestId('fnol-form-error')).toHaveTextContent(
      bg.errors.codes.CLAIM_INCIDENT_OUTSIDE_COVERAGE,
    );
    expect(submitButton()).toBeEnabled();
  });

  it('renders a field-level error under the photo list for a backend attachment rejection the client missed', async () => {
    seedToken('CLIENT');
    mockedApiFetch.mockResolvedValueOnce(samplePolicy()).mockRejectedValueOnce(
      new ApiRequestError('dev prose', 400, 'ATTACHMENT_UNSUPPORTED_TYPE', [
        { field: 'attachments', message: 'Only JPEG, PNG and WebP images are accepted: sneaky.jpg' },
      ]),
    );
    const user = userEvent.setup();

    renderAt();
    await screen.findByTestId('fnol-form');
    await fillForm(user);
    await user.click(submitButton());

    expect(await screen.findByText(bg.errors.codes.ATTACHMENT_UNSUPPORTED_TYPE)).toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  it('preserves the submitted field values after a failed submission', async () => {
    seedToken('CLIENT');
    mockedApiFetch
      .mockResolvedValueOnce(samplePolicy())
      .mockRejectedValueOnce(new ApiRequestError('dev prose', 500));
    const user = userEvent.setup();

    renderAt();
    await screen.findByTestId('fnol-form');
    await fillForm(user);
    await user.click(submitButton());

    expect(await screen.findByTestId('fnol-form-error')).toBeInTheDocument();
    expect(descriptionField()).toHaveValue(VALID_INPUT.description);
    expect(incidentDateField()).toHaveValue(VALID_INPUT.incidentDate);
    expect(locationField()).toHaveValue(VALID_INPUT.location);
  });

  it('sends only one request when the user submits twice before the response resolves', async () => {
    seedToken('CLIENT');
    let resolveSubmit: (value: ClaimResponse) => void;
    mockedApiFetch.mockResolvedValueOnce(samplePolicy()).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveSubmit = resolve;
        }),
    );
    const user = userEvent.setup();

    renderAt();
    await screen.findByTestId('fnol-form');
    await fillForm(user);

    const button = submitButton();
    await user.click(button);
    await user.click(button);

    expect(mockedApiFetch).toHaveBeenCalledTimes(2); // one GET + one POST, not two POSTs

    await act(async () => {
      resolveSubmit!(sampleClaim());
    });
  });
});
