import { useState } from 'react';
import type { FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { apiFetch } from '../../api/client';
import { QuoteResult } from './QuoteResult';
import { useFormSubmission } from '../../hooks/useFormSubmission';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { FormField } from '../../components/ui/FormField';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Spinner } from '../../components/ui/Spinner';

/** Mirrors the backend's `CreateQuoteRequest` (READ-ONLY, quote/api). */
interface CreateQuoteRequest {
  driverAge: number;
  regionCode: string;
  engineCc: number;
  installments: number;
  bonusMalusClass: string;
}

/**
 * Mirrors the backend's `quote.domain.QuoteStatus` enum (Story 6.2). Never
 * chosen by this frontend - always read off a `QuoteResponse`, derived
 * server-side (Architecture Spine AD-3). `CANCELLED` is reserved: no
 * response can carry it yet (no producer this milestone), but the type
 * already accounts for it so a later story doesn't widen this union.
 */
export type QuoteStatus = 'CALCULATED' | 'ACCEPTED' | 'EXPIRED' | 'CANCELLED';

/** Mirrors the backend's `QuoteResponse` (READ-ONLY, quote/api) field for field. */
export interface QuoteResponse {
  id: string;
  createdAt: string;
  driverAge: number;
  regionCode: string;
  engineCc: number;
  zoneId: number;
  zoneName: string;
  basePremium: number;
  ageSurcharge: number;
  bonusMalusClass: string;
  bonusMalusFactor: number;
  oneTimePremium: number;
  installments: number;
  installmentFee: number;
  totalPremium: number;
  installmentAmount: number;
  currency: string;
  // Story 6.2 - additive (Architecture Spine AD-13). `acceptedAt` stays
  // `null` through this milestone's Epic 6 - only Story 8.1's acceptance
  // endpoint ever sets it.
  validUntil: string;
  status: QuoteStatus;
  acceptedAt: string | null;
}

// Story 6.1 - the five classes seeded in `bonus_malus_class`. Fixed here
// alongside the form rather than fetched: same pattern the existing
// `installments` field already uses (a small, backend-defined enum
// rendered as a closed set of options, not free text).
const BONUS_MALUS_CLASSES = ['BONUS_20', 'BONUS_10', 'NEUTRAL', 'MALUS_25', 'MALUS_50'] as const;

// The only fields this form actually renders an inline error next to. If a
// `fieldErrors` response names anything outside this set, the error would be
// stored but never shown - fall back to the generic message so the user
// always sees something instead of a submit that silently did nothing.
const KNOWN_FIELDS = new Set([
  'driverAge',
  'regionCode',
  'engineCc',
  'installments',
  'bonusMalusClass',
]);


/**
 * Quote request form (Story 1.7, FR-8/FR-9). Submits `driverAge`,
 * `regionCode`, `engineCc`, `installments` to `POST /api/v1/quotes` as the
 * first authenticated frontend call (`{ authenticated: true }`, see
 * `api/client.ts`) and renders the full breakdown in place via
 * `QuoteResult` on success. No route guard here (out of scope - Story
 * 2.4's job); a logged-out visitor reaching this form still gets a
 * controlled failure because the backend rejects the unauthenticated
 * request and this form falls back to the generic error message exactly
 * like any other failure.
 */
export function QuoteForm() {
  const { t } = useTranslation();

  const [driverAge, setDriverAge] = useState('');
  const [regionCode, setRegionCode] = useState('');
  const [engineCc, setEngineCc] = useState('');
  const [installments, setInstallments] = useState('');
  // Defaults to NEUTRAL (factor 1.000) - the neutral position, not first
  // alphabetically (UX EXPERIENCE.md, Interaction Primitives).
  const [bonusMalusClass, setBonusMalusClass] = useState('NEUTRAL');
  const [quote, setQuote] = useState<QuoteResponse | null>(null);

  // The phase machine, the double-submit guard, the unmount guard and the
  // error routing are all shared now (Story 8.2, Epic 5 retro item 41) -
  // this form keeps only what is its own: its inputs and its result.
  const { submitting, formError, fieldErrors, submit } = useFormSubmission('quote.form', t, {
    knownFields: KNOWN_FIELDS,
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const body: CreateQuoteRequest = {
      driverAge: Number(driverAge),
      regionCode,
      engineCc: Number(engineCc),
      installments: Number(installments),
      bonusMalusClass,
    };

    await submit(async (isCancelled) => {
      setQuote(null);
      const response = await apiFetch<QuoteResponse>('/api/v1/quotes', {
        method: 'POST',
        authenticated: true,
        body,
      });
      if (isCancelled()) return;
      setQuote(response);
    });
  }

  return (
    <Card title={t('quote.form.heading')} titleAs="h2">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormField
          label={t('quote.form.driverAge')}
          error={fieldErrors.driverAge}
          errorId="quote-driverAge-error"
        >
          <Input
            id="quote-driverAge"
            name="driverAge"
            type="number"
            min={18}
            // 100 mirrors the sanity ceiling on CreateQuoteRequest.driverAge -
            // see that class's javadoc for why 100 was chosen.
            max={100}
            required
            value={driverAge}
            onChange={(event) => setDriverAge(event.target.value)}
            disabled={submitting}
            invalid={Boolean(fieldErrors.driverAge)}
          />
        </FormField>
        <FormField
          label={t('quote.form.regionCode')}
          error={fieldErrors.regionCode}
          errorId="quote-regionCode-error"
        >
          <Input
            id="quote-regionCode"
            name="regionCode"
            type="text"
            required
            value={regionCode}
            onChange={(event) => setRegionCode(event.target.value)}
            disabled={submitting}
            invalid={Boolean(fieldErrors.regionCode)}
          />
        </FormField>
        <FormField
          label={t('quote.form.engineCc')}
          error={fieldErrors.engineCc}
          errorId="quote-engineCc-error"
        >
          <Input
            id="quote-engineCc"
            name="engineCc"
            type="number"
            min={800}
            // 8000 mirrors the sanity ceiling on CreateQuoteRequest.engineCc -
            // see that class's javadoc for why 8000 was chosen.
            max={8000}
            required
            value={engineCc}
            onChange={(event) => setEngineCc(event.target.value)}
            disabled={submitting}
            invalid={Boolean(fieldErrors.engineCc)}
          />
        </FormField>
        <FormField
          label={t('quote.form.installments')}
          error={fieldErrors.installments}
          errorId="quote-installments-error"
        >
          <Input
            id="quote-installments"
            name="installments"
            type="number"
            min={1}
            max={4}
            required
            value={installments}
            onChange={(event) => setInstallments(event.target.value)}
            disabled={submitting}
            invalid={Boolean(fieldErrors.installments)}
          />
        </FormField>
        <FormField
          label={t('quote.form.bonusMalusClass')}
          error={fieldErrors.bonusMalusClass}
          errorId="quote-bonusMalusClass-error"
        >
          <Select
            id="quote-bonusMalusClass"
            name="bonusMalusClass"
            required
            value={bonusMalusClass}
            onChange={(event) => setBonusMalusClass(event.target.value)}
            disabled={submitting}
            invalid={Boolean(fieldErrors.bonusMalusClass)}
          >
            {BONUS_MALUS_CLASSES.map((bmClass) => (
              <option key={bmClass} value={bmClass}>
                {t(`quote.form.bonusMalusClasses.${bmClass}`)}
              </option>
            ))}
          </Select>
        </FormField>
        <p className="text-xs text-text-muted">{t('quote.form.bonusMalusNote')}</p>
        {formError && (
          <Alert variant="danger" data-testid="quote-error">
            {formError}
          </Alert>
        )}
        <Button type="submit" disabled={submitting}>
          {submitting ? (
            <>
              <Spinner className="mr-2" />
              {t('quote.form.submitting')}
            </>
          ) : (
            t('quote.form.submit')
          )}
        </Button>
      </form>
      {quote && <QuoteResult quote={quote} />}
    </Card>
  );
}
