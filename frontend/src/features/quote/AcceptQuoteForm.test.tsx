import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { apiFetch, ApiRequestError } from '../../api/client';
import type { PolicyResponse } from '../policy/policyTypes';
import { AcceptQuoteForm } from './AcceptQuoteForm';
import bg from '../../i18n/bg.json';

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

const QUOTE_ID = '11111111-1111-1111-1111-111111111111';

function samplePolicy(overrides: Partial<PolicyResponse> = {}): PolicyResponse {
  return {
    id: '22222222-2222-2222-2222-222222222222',
    policyNumber: 'MI-2026-00000042',
    quoteId: QUOTE_ID,
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
    ...overrides,
  };
}

function renderForm(onQuoteExpired = vi.fn()) {
  render(<AcceptQuoteForm quoteId={QUOTE_ID} onQuoteExpired={onQuoteExpired} />);
  return { onQuoteExpired };
}

async function fillIdentity(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(bg.quotes.accept.holderName), 'Ivan Petrov');
  await user.type(screen.getByLabelText(bg.quotes.accept.vehicleRegistration), 'CA1234BM');
}

describe('AcceptQuoteForm', () => {
  it('submits what the client entered and replaces itself with the issued policy', async () => {
    const user = userEvent.setup();
    mockedApiFetch.mockResolvedValue(samplePolicy());

    renderForm();
    await fillIdentity(user);
    await user.click(screen.getByRole('button', { name: bg.quotes.accept.submit }));

    expect(await screen.findByTestId('accept-success')).toBeInTheDocument();
    // The policy number rendered is the one the server returned, never a
    // predicted value (UX-DR14).
    expect(screen.getByTestId('accept-policy-number')).toHaveTextContent('MI-2026-00000042');
    expect(screen.getByTestId('accept-total-premium')).toHaveTextContent('143.12');
    // The form is gone - the section is replaced, not merely annotated.
    expect(screen.queryByTestId('accept-form')).not.toBeInTheDocument();

    const [path, options] = mockedApiFetch.mock.calls[0];
    expect(path).toBe(`/api/v1/quotes/${QUOTE_ID}/accept`);
    expect(options).toMatchObject({ method: 'POST', authenticated: true });
    expect(options?.body).toMatchObject({ holderName: 'Ivan Petrov', vehicleRegistration: 'CA1234BM' });
  });

  it('omits the vehicle identifier it was not given rather than sending it blank', async () => {
    const user = userEvent.setup();
    mockedApiFetch.mockResolvedValue(samplePolicy({ vehicleRegistration: null, vehicleVin: 'WDB1234567N123456' }));

    renderForm();
    await user.type(screen.getByLabelText(bg.quotes.accept.holderName), 'Ivan Petrov');
    await user.type(screen.getByLabelText(bg.quotes.accept.vehicleVin), 'WDB1234567N123456');
    await user.click(screen.getByRole('button', { name: bg.quotes.accept.submit }));

    await screen.findByTestId('accept-success');
    const body = mockedApiFetch.mock.calls[0][1]?.body as Record<string, unknown>;
    expect(body).not.toHaveProperty('vehicleRegistration');
    expect(body.vehicleVin).toBe('WDB1234567N123456');
  });

  it('renders a replay the same way as a first acceptance', async () => {
    const user = userEvent.setup();
    // The backend answers a repeat acceptance with 200 and the policy it
    // already issued (AD-5). That is a success, not an error, and it must
    // read identically here - the client cannot and need not tell the two
    // apart, since apiFetch resolves on any 2xx.
    mockedApiFetch.mockResolvedValue(samplePolicy({ policyNumber: 'MI-2026-00000007' }));

    renderForm();
    await fillIdentity(user);
    await user.click(screen.getByRole('button', { name: bg.quotes.accept.submit }));

    expect(await screen.findByTestId('accept-success')).toBeInTheDocument();
    expect(screen.getByTestId('accept-policy-number')).toHaveTextContent('MI-2026-00000007');
    expect(screen.queryByTestId('accept-error')).not.toBeInTheDocument();
  });

  it('does not special-case an expired session - no success, nothing half-shown', async () => {
    const user = userEvent.setup();
    // Story 7.1 owns clearing the token and returning to login, in the
    // shared api client. This form's only duty is to not claim success and
    // not swallow the failure.
    mockedApiFetch.mockRejectedValue(new ApiRequestError('dev prose', 401, 'AUTH_UNAUTHENTICATED'));

    renderForm();
    await fillIdentity(user);
    await user.click(screen.getByRole('button', { name: bg.quotes.accept.submit }));

    expect(await screen.findByTestId('accept-error')).toHaveTextContent(
      bg.errors.codes.AUTH_UNAUTHENTICATED,
    );
    expect(screen.queryByTestId('accept-success')).not.toBeInTheDocument();
  });

  it('shows nothing as done while the request is in flight', async () => {
    const user = userEvent.setup();
    let release: (policy: PolicyResponse) => void = () => {};
    mockedApiFetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          release = resolve as (policy: PolicyResponse) => void;
        }),
    );

    renderForm();
    await fillIdentity(user);
    await user.click(screen.getByRole('button', { name: bg.quotes.accept.submit }));

    // No optimistic UI (UX-DR14): the success block appears only after the
    // backend confirms. The button keeps its label and disables.
    expect(screen.queryByTestId('accept-success')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: bg.quotes.accept.submit })).toBeDisabled();

    release(samplePolicy());
    expect(await screen.findByTestId('accept-success')).toBeInTheDocument();
  });

  it('sends one request when the button is pressed twice before the response resolves', async () => {
    const user = userEvent.setup();
    let release: (policy: PolicyResponse) => void = () => {};
    mockedApiFetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          release = resolve as (policy: PolicyResponse) => void;
        }),
    );

    renderForm();
    await fillIdentity(user);
    const button = screen.getByRole('button', { name: bg.quotes.accept.submit });
    await user.click(button);
    await user.click(button);

    expect(mockedApiFetch).toHaveBeenCalledTimes(1);
    release(samplePolicy());
    await screen.findByTestId('accept-success');
  });

  it.each([
    ['QUOTE_COVERAGE_START_IN_PAST', 'coverageStart'],
    ['QUOTE_COVERAGE_START_TOO_FAR_AHEAD', 'coverageStart'],
    ['QUOTE_VEHICLE_IDENTIFIER_REQUIRED', 'vehicleRegistration'],
  ])('renders %s beside its own field and keeps every entered value', async (code, field) => {
    const user = userEvent.setup();
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('dev prose', 400, code, [{ field, message: 'dev prose' }]),
    );

    renderForm();
    await fillIdentity(user);
    await user.click(screen.getByRole('button', { name: bg.quotes.accept.submit }));

    const message = await screen.findByText(bg.errors.codes[code as keyof typeof bg.errors.codes]);
    expect(message).toBeInTheDocument();
    // Never the backend's own prose (AD-7/AD-8).
    expect(screen.queryByText('dev prose')).not.toBeInTheDocument();
    // Every value the client entered stays in place (UX-DR6).
    expect(screen.getByLabelText(bg.quotes.accept.holderName)).toHaveValue('Ivan Petrov');
    expect(screen.getByLabelText(bg.quotes.accept.vehicleRegistration)).toHaveValue('CA1234BM');
    expect(screen.getByTestId('accept-form')).toBeInTheDocument();
  });

  it('asks the screen to re-read the quote when the offer expired while it was open', async () => {
    const user = userEvent.setup();
    const onQuoteExpired = vi.fn();
    mockedApiFetch.mockRejectedValue(new ApiRequestError('dev prose', 409, 'QUOTE_EXPIRED'));

    renderForm(onQuoteExpired);
    await fillIdentity(user);
    await user.click(screen.getByRole('button', { name: bg.quotes.accept.submit }));

    // The screen is now wrong, not just this submission (UX-DR8) - the
    // parent re-reads so it re-renders as expired in the same beat.
    await waitFor(() => expect(onQuoteExpired).toHaveBeenCalledTimes(1));
    expect(await screen.findByTestId('accept-error')).toHaveTextContent(bg.errors.codes.QUOTE_EXPIRED);
  });

  it('falls back to the generic message for a failure carrying no code', async () => {
    const user = userEvent.setup();
    mockedApiFetch.mockRejectedValue(new Error('network down'));

    renderForm();
    await fillIdentity(user);
    await user.click(screen.getByRole('button', { name: bg.quotes.accept.submit }));

    expect(await screen.findByTestId('accept-error')).toHaveTextContent(bg.errors.generic);
    expect(screen.getByTestId('accept-form')).toBeInTheDocument();
  });

  it('bounds the date input to the same window the backend enforces', () => {
    renderForm();

    const input = screen.getByLabelText(bg.quotes.accept.coverageStart);
    const today = new Date();
    const min = input.getAttribute('min');
    const max = input.getAttribute('max');
    expect(min).toBeTruthy();
    expect(max).toBeTruthy();

    // 90 days apart, matching quote.max-coverage-start-days-ahead. The bound
    // is a courtesy - the backend re-checks it either way (M1 AD-4).
    const days = (Date.parse(max as string) - Date.parse(min as string)) / 86_400_000;
    expect(days).toBe(90);
    expect(min).toBe(
      new Date(today.getTime() - today.getTimezoneOffset() * 60_000).toISOString().slice(0, 10),
    );
  });

  it('says the horizon is the portal own rule, not an official requirement', () => {
    renderForm();

    // NFR-8: provenance is stated wherever the rule is surfaced.
    expect(screen.getByText(/собствено правило на портала/)).toBeInTheDocument();
  });
});
