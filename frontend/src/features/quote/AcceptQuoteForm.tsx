import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { apiFetch, ApiRequestError } from '../../api/client';
import type { PolicyResponse } from '../policy/policyTypes';
import { useFormSubmission } from '../../hooks/useFormSubmission';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { FormField } from '../../components/ui/FormField';
import { Input } from '../../components/ui/Input';
import { Spinner } from '../../components/ui/Spinner';

/** Mirrors the backend's `AcceptQuoteRequest` (READ-ONLY, quote/api). */
interface AcceptQuoteRequestBody {
  coverageStart: string;
  holderName: string;
  vehicleRegistration?: string;
  vehicleVin?: string;
}

/**
 * How far ahead cover may be scheduled. Mirrors the backend's
 * `quote.max-coverage-start-days-ahead`, which is the authority - this
 * bound only stops the common case in the browser, before a request is
 * made. A project rule, not an official or legal one; the note beside the
 * field says so (NFR-8).
 */
const MAX_COVERAGE_START_DAYS_AHEAD = 90;

/** The fields this form renders an inline error next to. */
const KNOWN_FIELDS = new Set(['coverageStart', 'holderName', 'vehicleRegistration', 'vehicleVin']);

function isoDate(date: Date): string {
  // Local calendar date, not `toISOString()`, which would shift the day for
  // anyone east of UTC late in the evening - the client would then offer a
  // `min` of yesterday and the backend would refuse their own default.
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

interface AcceptQuoteFormProps {
  quoteId: string;
  /**
   * Called when the server refuses because the offer expired while this
   * screen was open (UX-DR8) - the parent re-reads the quote so the screen
   * re-renders as expired in the same beat, rather than leaving a form on
   * screen that asserts acceptability from a stale fetch.
   */
  onQuoteExpired: () => void;
}

/**
 * The acceptance section of the quote detail screen (Story 8.2, FR-M3-05 /
 * FR-M3-08). Rendered below the breakdown, in the page flow - never a modal
 * (UX-DR5) - and read in the order what you are buying (the breakdown
 * above) -> who you are -> when it starts -> commit.
 *
 * On success the client is taken to the new policy's own screen (Story
 * 8.3) - not back to a list, and no longer the in-place confirmation Story
 * 8.2 shipped as an interim while `/policies/:id` did not yet exist.
 *
 * Nothing is shown as done before the backend confirms it (UX-DR14): the
 * navigation happens only once the server has answered with a real policy.
 */
export function AcceptQuoteForm({ quoteId, onQuoteExpired }: AcceptQuoteFormProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const today = isoDate(new Date());
  const latestStart = isoDate(
    new Date(Date.now() + MAX_COVERAGE_START_DAYS_AHEAD * 24 * 60 * 60 * 1000),
  );

  const [holderName, setHolderName] = useState('');
  const [vehicleRegistration, setVehicleRegistration] = useState('');
  const [vehicleVin, setVehicleVin] = useState('');
  const [coverageStart, setCoverageStart] = useState(today);

  const { submitting, formError, fieldErrors, submit } = useFormSubmission('quotes.accept', t, {
    knownFields: KNOWN_FIELDS,
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const body: AcceptQuoteRequestBody = {
      coverageStart,
      holderName,
      // Sent only when filled: the backend requires exactly one of the two,
      // and treats blank as absent either way.
      ...(vehicleRegistration.trim() ? { vehicleRegistration } : {}),
      ...(vehicleVin.trim() ? { vehicleVin } : {}),
    };

    await submit(async (isCancelled) => {
      try {
        const issued = await apiFetch<PolicyResponse>(`/api/v1/quotes/${quoteId}/accept`, {
          method: 'POST',
          authenticated: true,
          body,
        });
        if (isCancelled()) return;
        navigate(`/policies/${issued.id}`);
      } catch (error) {
        // The one refusal this form cannot simply display: the offer died
        // while the screen sat open, so the screen itself is now wrong.
        // Rethrown so the shared handling still renders the message, and
        // the parent re-reads in the same beat (UX-DR8).
        if (error instanceof ApiRequestError && error.code === 'QUOTE_EXPIRED' && !isCancelled()) {
          onQuoteExpired();
        }
        throw error;
      }
    });
  }

  return (
    <Card title={t('quotes.accept.heading')} titleAs="h3" data-testid="accept-form">
      <p className="mb-4 text-sm text-text-muted">{t('quotes.accept.intro')}</p>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormField
          label={t('quotes.accept.holderName')}
          error={fieldErrors.holderName}
          errorId="accept-holderName-error"
        >
          <Input
            id="accept-holderName"
            name="holderName"
            type="text"
            required
            value={holderName}
            onChange={(event) => setHolderName(event.target.value)}
            disabled={submitting}
            invalid={Boolean(fieldErrors.holderName)}
          />
        </FormField>

        <FormField
          label={t('quotes.accept.vehicleRegistration')}
          error={fieldErrors.vehicleRegistration}
          errorId="accept-vehicleRegistration-error"
        >
          <Input
            id="accept-vehicleRegistration"
            name="vehicleRegistration"
            type="text"
            value={vehicleRegistration}
            onChange={(event) => setVehicleRegistration(event.target.value)}
            disabled={submitting}
            invalid={Boolean(fieldErrors.vehicleRegistration)}
          />
        </FormField>

        <FormField
          label={t('quotes.accept.vehicleVin')}
          error={fieldErrors.vehicleVin}
          errorId="accept-vehicleVin-error"
        >
          <Input
            id="accept-vehicleVin"
            name="vehicleVin"
            type="text"
            value={vehicleVin}
            onChange={(event) => setVehicleVin(event.target.value)}
            disabled={submitting}
            invalid={Boolean(fieldErrors.vehicleVin)}
          />
        </FormField>
        <p className="text-xs text-text-muted">{t('quotes.accept.vehicleNote')}</p>

        <FormField
          label={t('quotes.accept.coverageStart')}
          error={fieldErrors.coverageStart}
          errorId="accept-coverageStart-error"
        >
          <Input
            id="accept-coverageStart"
            name="coverageStart"
            // Native date input, no custom picker (UX-DR9). min/max mirror
            // the backend's own rules; the backend still re-checks both.
            type="date"
            required
            min={today}
            max={latestStart}
            value={coverageStart}
            onChange={(event) => setCoverageStart(event.target.value)}
            disabled={submitting}
            invalid={Boolean(fieldErrors.coverageStart)}
          />
        </FormField>
        <p className="text-xs text-text-muted">
          {t('quotes.accept.horizonNote', { days: MAX_COVERAGE_START_DAYS_AHEAD })}
        </p>

        {formError && (
          <Alert variant="danger" data-testid="accept-error">
            {formError}
          </Alert>
        )}

        {/* The label does not change while in flight (UX-DR6): the control
            names the outcome, and swapping in a "submitting" label would
            change the button's accessible name mid-action. The spinner and
            the disabled state carry the progress instead. */}
        <Button type="submit" disabled={submitting}>
          {submitting && <Spinner className="mr-2" />}
          {t('quotes.accept.submit')}
        </Button>
      </form>
    </Card>
  );
}
