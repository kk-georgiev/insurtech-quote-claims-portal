import { StrictMode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QuoteForm } from './QuoteForm';
import type { QuoteResponse } from './QuoteForm';
import { ApiRequestError, apiFetch } from '../../api/client';

// `apiFetch` is the only seam mocked — no backend, no network. The rest of
// `api/client.ts` (notably `ApiRequestError`) stays real. `vitest.config.ts`
// sets `mockReset: true`, so the fn is cleared before every test. Mirrors
// `LoginForm.test.tsx`'s mocking pattern (spec Tasks & Acceptance).
vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

const GENERIC_ERROR = 'Something went wrong. Please try again.';

const SAMPLE_QUOTE: QuoteResponse = {
  id: '11111111-1111-1111-1111-111111111111',
  createdAt: '2026-08-28T12:00:00Z',
  driverAge: 30,
  regionCode: 'SOF',
  engineCc: 1600,
  zoneId: 1,
  zoneName: 'Sofia',
  basePremium: 300,
  ageSurcharge: 0,
  oneTimePremium: 300,
  installments: 2,
  installmentFee: 15,
  totalPremium: 315,
  installmentAmount: 157.5,
  currency: 'BGN',
};

function renderForm() {
  render(
    <StrictMode>
      <QuoteForm />
    </StrictMode>,
  );
  return { user: userEvent.setup() };
}

const driverAgeField = () => screen.getByLabelText('Driver age');
const regionCodeField = () => screen.getByLabelText('Region code');
const engineCcField = () => screen.getByLabelText('Engine size (cc)');
const installmentsField = () => screen.getByLabelText('Number of installments');
const submitButton = () => screen.getByRole('button', { name: 'Get quote' });

async function fillAndSubmit(
  user: ReturnType<typeof userEvent.setup>,
  values: { driverAge: string; regionCode: string; engineCc: string; installments: string },
) {
  await user.type(driverAgeField(), values.driverAge);
  await user.type(regionCodeField(), values.regionCode);
  await user.type(engineCcField(), values.engineCc);
  await user.type(installmentsField(), values.installments);
  await user.click(submitButton());
}

const VALID_INPUT = { driverAge: '30', regionCode: 'SOF', engineCc: '1600', installments: '2' };

describe('QuoteForm', () => {
  it('submits the authenticated request and renders the full breakdown on success', async () => {
    mockedApiFetch.mockResolvedValue(SAMPLE_QUOTE);

    const { user } = renderForm();
    await fillAndSubmit(user, VALID_INPUT);

    expect(await screen.findByTestId('quote-result')).toBeInTheDocument();
    expect(screen.getByTestId('quote-zoneName')).toHaveTextContent('Sofia');
    expect(screen.getByTestId('quote-basePremium')).toHaveTextContent('300');
    expect(screen.getByTestId('quote-ageSurcharge')).toHaveTextContent('0');
    expect(screen.getByTestId('quote-oneTimePremium')).toHaveTextContent('300');
    expect(screen.getByTestId('quote-installments')).toHaveTextContent('2');
    expect(screen.getByTestId('quote-installmentFee')).toHaveTextContent('15');
    expect(screen.getByTestId('quote-totalPremium')).toHaveTextContent('315');
    expect(screen.getByTestId('quote-installmentAmount')).toHaveTextContent('157.5');

    // First authenticated call in the codebase (spec Boundaries &
    // Constraints) - asserts the option is actually passed through, and
    // that the numeric fields are sent as numbers, not the raw input strings.
    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/quotes', {
      method: 'POST',
      authenticated: true,
      body: { driverAge: 30, regionCode: 'SOF', engineCc: 1600, installments: 2 },
    });
    expect(submitButton()).toBeEnabled();
  });

  it('renders a field-level error on the region input for an unknown region code', async () => {
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 400', 400, 'PRICING_UNKNOWN_REGION', [
        { field: 'regionCode', message: 'Unknown region code: XX' },
      ]),
    );

    const { user } = renderForm();
    await fillAndSubmit(user, { ...VALID_INPUT, regionCode: 'XX' });

    expect(await screen.findByText('Unknown region code: XX')).toBeInTheDocument();
    expect(screen.queryByTestId('quote-error')).not.toBeInTheDocument();
    expect(screen.queryByTestId('quote-result')).not.toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  it('renders a field-level error on the installments input for an unsupported installment count', async () => {
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 400', 400, 'PRICING_UNSUPPORTED_INSTALLMENTS', [
        { field: 'installments', message: 'Unsupported installment count: 3' },
      ]),
    );

    const { user } = renderForm();
    await fillAndSubmit(user, { ...VALID_INPUT, installments: '3' });

    expect(await screen.findByText('Unsupported installment count: 3')).toBeInTheDocument();
    expect(screen.queryByTestId('quote-error')).not.toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  it('renders field-level errors from a bean-validation failure next to each offending input', async () => {
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 400', 400, 'SHARED_VALIDATION_ERROR', [
        { field: 'driverAge', message: 'must be greater than or equal to 18' },
        { field: 'engineCc', message: 'must be greater than or equal to 800' },
        { field: 'regionCode', message: 'must not be blank' },
      ]),
    );

    const { user } = renderForm();
    // driverAge<18, engineCc<800, blank regionCode - installments left valid.
    await user.type(driverAgeField(), '16');
    await user.type(engineCcField(), '500');
    await user.type(installmentsField(), '2');
    await user.click(submitButton());

    expect(await screen.findByText('must be greater than or equal to 18')).toBeInTheDocument();
    expect(screen.getByText('must be greater than or equal to 800')).toBeInTheDocument();
    expect(screen.getByText('must not be blank')).toBeInTheDocument();
    expect(screen.queryByTestId('quote-error')).not.toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  it('falls back to a generic form-level error on a 401 (no/expired token), and leaves the form editable', async () => {
    mockedApiFetch.mockRejectedValue(new ApiRequestError('Request failed with status 401', 401));

    const { user } = renderForm();
    await fillAndSubmit(user, VALID_INPUT);

    expect(await screen.findByTestId('quote-error')).toHaveTextContent(GENERIC_ERROR);
    expect(screen.queryByTestId('quote-result')).not.toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
    expect(driverAgeField()).toBeEnabled();
    // The typed inputs survived the failure - no re-entry needed.
    expect(driverAgeField()).toHaveValue(30);
    expect(regionCodeField()).toHaveValue('SOF');
  });

  it('falls back to a generic form-level error on a plain network error (not an ApiRequestError)', async () => {
    mockedApiFetch.mockRejectedValue(new Error('Failed to fetch'));

    const { user } = renderForm();
    await fillAndSubmit(user, VALID_INPUT);

    expect(await screen.findByTestId('quote-error')).toHaveTextContent(GENERIC_ERROR);
    expect(screen.queryByTestId('quote-result')).not.toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  it('falls back to a generic form-level error when fieldErrors names a field this form does not render', async () => {
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 400', 400, 'SHARED_VALIDATION_ERROR', [
        { field: 'someUnrenderedField', message: 'this will never be shown inline' },
      ]),
    );

    const { user } = renderForm();
    await fillAndSubmit(user, VALID_INPUT);

    expect(await screen.findByTestId('quote-error')).toHaveTextContent(GENERIC_ERROR);
    expect(screen.queryByText('this will never be shown inline')).not.toBeInTheDocument();
    expect(screen.queryByTestId('quote-result')).not.toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  it('recovers from a controlled failure: retrying after a field error succeeds', async () => {
    mockedApiFetch.mockRejectedValueOnce(
      new ApiRequestError('Request failed with status 400', 400, 'PRICING_UNKNOWN_REGION', [
        { field: 'regionCode', message: 'Unknown region code: XX' },
      ]),
    );

    const { user } = renderForm();
    await fillAndSubmit(user, { ...VALID_INPUT, regionCode: 'XX' });
    expect(await screen.findByText('Unknown region code: XX')).toBeInTheDocument();

    mockedApiFetch.mockResolvedValueOnce(SAMPLE_QUOTE);
    await user.clear(regionCodeField());
    await user.type(regionCodeField(), 'SOF');
    await user.click(submitButton());

    await waitFor(() => {
      expect(screen.getByTestId('quote-result')).toBeInTheDocument();
    });
    expect(screen.queryByText('Unknown region code: XX')).not.toBeInTheDocument();
  });
});
