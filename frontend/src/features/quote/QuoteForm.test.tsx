import { StrictMode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QuoteForm } from './QuoteForm';
import type { QuoteResponse } from './QuoteForm';
import { ApiRequestError, apiFetch } from '../../api/client';
import bg from '../../i18n/bg.json';

// `apiFetch` is the only seam mocked — no backend, no network. The rest of
// `api/client.ts` (notably `ApiRequestError`) stays real. `vitest.config.ts`
// sets `mockReset: true`, so the fn is cleared before every test. Mirrors
// `LoginForm.test.tsx`'s mocking pattern (spec Tasks & Acceptance).
vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

const GENERIC_ERROR = bg.errors.generic;

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
  bonusMalusClass: 'NEUTRAL',
  bonusMalusFactor: 1.0,
  oneTimePremium: 300,
  installments: 2,
  installmentFee: 15,
  totalPremium: 315,
  installmentAmount: 157.5,
  currency: 'BGN',
  validUntil: '2026-09-11',
  status: 'CALCULATED',
  acceptedAt: null,
};

function renderForm() {
  render(
    <StrictMode>
      <QuoteForm />
    </StrictMode>,
  );
  return { user: userEvent.setup() };
}

const driverAgeField = () => screen.getByLabelText(bg.quote.form.driverAge);
const regionCodeField = () => screen.getByLabelText(bg.quote.form.regionCode);
const engineCcField = () => screen.getByLabelText(bg.quote.form.engineCc);
const installmentsField = () => screen.getByLabelText(bg.quote.form.installments);
const submitButton = () => screen.getByRole('button', { name: bg.quote.form.submit });

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
const bonusMalusClassField = () => screen.getByLabelText(bg.quote.form.bonusMalusClass);

describe('QuoteForm', () => {
  it('defaults the bonus-malus class to NEUTRAL (Story 6.1)', () => {
    renderForm();
    expect(bonusMalusClassField()).toHaveValue('NEUTRAL');
  });

  it('sends the selected bonus-malus class and renders its factor in the breakdown', async () => {
    mockedApiFetch.mockResolvedValue({ ...SAMPLE_QUOTE, bonusMalusClass: 'MALUS_50', bonusMalusFactor: 1.5 });

    const { user } = renderForm();
    await user.type(driverAgeField(), VALID_INPUT.driverAge);
    await user.type(regionCodeField(), VALID_INPUT.regionCode);
    await user.type(engineCcField(), VALID_INPUT.engineCc);
    await user.type(installmentsField(), VALID_INPUT.installments);
    await user.selectOptions(bonusMalusClassField(), 'MALUS_50');
    await user.click(submitButton());

    expect(await screen.findByTestId('quote-bonusMalusFactor')).toHaveTextContent('1.5');
    expect(mockedApiFetch).toHaveBeenCalledWith(
      '/api/v1/quotes',
      expect.objectContaining({ body: expect.objectContaining({ bonusMalusClass: 'MALUS_50' }) }),
    );
  });

  it('renders a field-level error on the bonus-malus select for an unknown class', async () => {
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 400', 400, 'PRICING_UNKNOWN_BONUS_MALUS_CLASS', [
        { field: 'bonusMalusClass', message: 'Unknown bonus-malus class: NOT_A_CLASS' },
      ]),
    );

    const { user } = renderForm();
    await fillAndSubmit(user, VALID_INPUT);

    expect(
      await screen.findByText(bg.errors.codes.PRICING_UNKNOWN_BONUS_MALUS_CLASS),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('quote-error')).not.toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  it('renders the driverAge/engineCc sanity ceilings as native max attributes', () => {
    // spec-quote-input-bounds.md: mirrors CreateQuoteRequest's @Max(100)/
    // @Max(8000) as native hints (additive only - noValidate still means
    // these don't block submission; see the bean-validation tests below for
    // the actual enforcement path).
    renderForm();

    expect(driverAgeField()).toHaveAttribute('max', '100');
    expect(engineCcField()).toHaveAttribute('max', '8000');
  });

  it('submits the authenticated request and renders the full breakdown on success', async () => {
    mockedApiFetch.mockResolvedValue(SAMPLE_QUOTE);

    const { user } = renderForm();
    await fillAndSubmit(user, VALID_INPUT);

    expect(await screen.findByTestId('quote-result')).toBeInTheDocument();
    // Story 3.2b: the zone is labelled from `zoneId` via the catalog. The
    // backend's English `zoneName` ('Sofia' in this fixture) is still on the
    // wire and must never reach the screen.
    expect(screen.getByTestId('quote-zoneName')).toHaveTextContent(bg.quote.result.zones['1']);
    expect(screen.getByTestId('quote-result')).not.toHaveTextContent(SAMPLE_QUOTE.zoneName);
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
      body: {
        driverAge: 30,
        regionCode: 'SOF',
        engineCc: 1600,
        installments: 2,
        bonusMalusClass: 'NEUTRAL',
      },
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

    expect(await screen.findByText(bg.errors.codes.PRICING_UNKNOWN_REGION)).toBeInTheDocument();
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

    expect(await screen.findByText(bg.errors.codes.PRICING_UNSUPPORTED_INSTALLMENTS)).toBeInTheDocument();
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
    // Before submitting, none of the fields carry error-describing aria
    // attributes — no error has been set yet.
    expect(driverAgeField()).not.toHaveAttribute('aria-invalid');
    expect(driverAgeField()).not.toHaveAttribute('aria-describedby');
    expect(installmentsField()).not.toHaveAttribute('aria-invalid');
    expect(installmentsField()).not.toHaveAttribute('aria-describedby');

    // driverAge<18, engineCc<800, blank regionCode - installments left valid.
    await user.type(driverAgeField(), '16');
    await user.type(engineCcField(), '500');
    await user.type(installmentsField(), '2');
    await user.click(submitButton());

    expect(await screen.findByText(bg.quote.form.fieldErrors.driverAge)).toBeInTheDocument();
    expect(screen.getByText(bg.quote.form.fieldErrors.engineCc)).toBeInTheDocument();
    expect(screen.getByText(bg.quote.form.fieldErrors.regionCode)).toBeInTheDocument();
    expect(screen.queryByTestId('quote-error')).not.toBeInTheDocument();
    expect(submitButton()).toBeEnabled();

    expect(driverAgeField()).toHaveAttribute('aria-invalid', 'true');
    expect(driverAgeField()).toHaveAttribute('aria-describedby', 'quote-driverAge-error');
    expect(screen.getByText(bg.quote.form.fieldErrors.driverAge).id).toBe('quote-driverAge-error');
    expect(engineCcField()).toHaveAttribute('aria-invalid', 'true');
    expect(engineCcField()).toHaveAttribute('aria-describedby', 'quote-engineCc-error');
    expect(screen.getByText(bg.quote.form.fieldErrors.engineCc).id).toBe('quote-engineCc-error');
    expect(regionCodeField()).toHaveAttribute('aria-invalid', 'true');
    expect(regionCodeField()).toHaveAttribute('aria-describedby', 'quote-regionCode-error');
    expect(screen.getByText(bg.quote.form.fieldErrors.regionCode).id).toBe('quote-regionCode-error');
    // installments had no field error - neither attribute is present.
    expect(installmentsField()).not.toHaveAttribute('aria-invalid');
    expect(installmentsField()).not.toHaveAttribute('aria-describedby');
  });

  it('renders field-level errors from an over-ceiling (@Max) bean-validation failure next to each offending input', async () => {
    // spec-quote-input-bounds.md: the new @Max(100)/@Max(8000) ceilings
    // produce the same SHARED_VALIDATION_ERROR shape as the existing @Min
    // case above - confirms the fieldErrors rendering path also covers the
    // over-ceiling direction, not just under-floor.
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 400', 400, 'SHARED_VALIDATION_ERROR', [
        { field: 'driverAge', message: 'must be less than or equal to 100' },
        { field: 'engineCc', message: 'must be less than or equal to 8000' },
      ]),
    );

    const { user } = renderForm();
    // driverAge>100, engineCc>8000 - regionCode/installments left valid.
    await user.type(driverAgeField(), '101');
    await user.type(regionCodeField(), 'SOF');
    await user.type(engineCcField(), '8001');
    await user.type(installmentsField(), '2');
    await user.click(submitButton());

    expect(await screen.findByText(bg.quote.form.fieldErrors.driverAge)).toBeInTheDocument();
    expect(screen.getByText(bg.quote.form.fieldErrors.engineCc)).toBeInTheDocument();
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

  // Before Story 3.2b this showed the one hardcoded generic string. Now the
  // envelope's `code` is resolved first, so the user gets the more specific
  // "check the details you entered" instead. The guard's purpose is unchanged
  // — the user still always sees a form-level message rather than a submit
  // that silently did nothing — and the message is strictly more useful.
  it("resolves the envelope's code form-level when fieldErrors names a field this form does not render", async () => {
    mockedApiFetch.mockRejectedValue(
      new ApiRequestError('Request failed with status 400', 400, 'SHARED_VALIDATION_ERROR', [
        { field: 'someUnrenderedField', message: 'this will never be shown inline' },
      ]),
    );

    const { user } = renderForm();
    await fillAndSubmit(user, VALID_INPUT);

    expect(await screen.findByTestId('quote-error')).toHaveTextContent(
      bg.errors.codes.SHARED_VALIDATION_ERROR,
    );
    // The backend's prose never reaches the DOM, inline or otherwise.
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
    expect(await screen.findByText(bg.errors.codes.PRICING_UNKNOWN_REGION)).toBeInTheDocument();

    mockedApiFetch.mockResolvedValueOnce(SAMPLE_QUOTE);
    await user.clear(regionCodeField());
    await user.type(regionCodeField(), 'SOF');
    await user.click(submitButton());

    await waitFor(() => {
      expect(screen.getByTestId('quote-result')).toBeInTheDocument();
    });
    expect(screen.queryByText(bg.errors.codes.PRICING_UNKNOWN_REGION)).not.toBeInTheDocument();
  });

  it('sends only one request when the user submits twice before the response resolves', async () => {
    let resolveFetch: (value: QuoteResponse) => void;
    mockedApiFetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve;
        }),
    );

    const { user } = renderForm();
    await user.type(driverAgeField(), VALID_INPUT.driverAge);
    await user.type(regionCodeField(), VALID_INPUT.regionCode);
    await user.type(engineCcField(), VALID_INPUT.engineCc);
    await user.type(installmentsField(), VALID_INPUT.installments);
    // Two rapid clicks on the same element before the first request
    // settles - only one call should go out (spec: double-submit guard is
    // a synchronous phase check, not reliant on `disabled` having
    // committed to the DOM yet). Re-querying by accessible name for the
    // second click would fail once the label switches to "Calculating…".
    const button = submitButton();
    await user.click(button);
    await user.click(button);

    expect(mockedApiFetch).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveFetch!(SAMPLE_QUOTE);
    });
  });

  it('allows a second submission once the first request has settled', async () => {
    mockedApiFetch.mockRejectedValueOnce(
      new ApiRequestError('Request failed with status 400', 400, 'PRICING_UNKNOWN_REGION', [
        { field: 'regionCode', message: 'Unknown region code: XX' },
      ]),
    );
    mockedApiFetch.mockResolvedValueOnce(SAMPLE_QUOTE);

    const { user } = renderForm();
    await fillAndSubmit(user, { ...VALID_INPUT, regionCode: 'XX' });
    expect(await screen.findByText(bg.errors.codes.PRICING_UNKNOWN_REGION)).toBeInTheDocument();
    expect(submitButton()).toBeEnabled();

    // The guard only blocks a submit while the previous one is still
    // pending - once the first request has settled (here, with a
    // failure), a genuine second submission must go through.
    await user.click(submitButton());

    expect(mockedApiFetch).toHaveBeenCalledTimes(2);
  });

  it('does not throw when the request resolves after unmounting mid-submit', async () => {
    let resolveFetch: (value: QuoteResponse) => void;
    mockedApiFetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve;
        }),
    );

    const { user } = renderForm();
    await fillAndSubmit(user, VALID_INPUT);

    cleanup();

    // Unlike LoginForm, this form has no external side effect (like
    // `saveToken`) to spy on post-unmount - its only post-response side
    // effects are internal React state updates, which React 18 already
    // no-ops silently after unmount (no console warning exists to assert
    // on). This is the more modest, honestly-scoped claim available: that
    // resolving the pending request after unmount does not throw or
    // produce an uncaught rejection, i.e. the `cancelledRef` guard's early
    // return is reached cleanly instead of a setter blowing up.
    await expect(
      (async () => {
        resolveFetch!(SAMPLE_QUOTE);
        await new Promise((resolve) => setTimeout(resolve, 0));
      })(),
    ).resolves.not.toThrow();
  });
});
