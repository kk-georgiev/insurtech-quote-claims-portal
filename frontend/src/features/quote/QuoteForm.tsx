import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { apiFetch, ApiRequestError } from '../../api/client';
import type { ApiFieldError } from '../../api/client';
import { QuoteResult } from './QuoteResult';

/** Mirrors the backend's `CreateQuoteRequest` (READ-ONLY, quote/api). */
interface CreateQuoteRequest {
  driverAge: number;
  regionCode: string;
  engineCc: number;
  installments: number;
}

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
  oneTimePremium: number;
  installments: number;
  installmentFee: number;
  totalPremium: number;
  installmentAmount: number;
  currency: string;
}

type FormPhase = 'editing' | 'submitting';

// AD-7: `code` is the only thing the frontend uses to select user-facing
// text - never the backend's dev/log-facing `message`. The screen copy moved
// into the i18n catalogs in Story 3.2a; this code-driven message is Story
// 3.2b's, which will delete this constant. Plain English until then. Every failure this form
// can hit either arrives as `fieldErrors` (bean validation,
// `PRICING_UNKNOWN_REGION`, `PRICING_UNSUPPORTED_INSTALLMENTS` - all shaped
// the same way, no code-specific branching needed) or falls back to this one
// generic message (no/expired token, network error, unexpected failure) -
// same fallback pattern as `LoginForm`.
const GENERIC_ERROR_MESSAGE = 'Something went wrong. Please try again.';

// The only fields this form actually renders an inline error next to. If a
// `fieldErrors` response names anything outside this set, the error would be
// stored but never shown - fall back to the generic message so the user
// always sees something instead of a submit that silently did nothing.
const KNOWN_FIELDS = new Set(['driverAge', 'regionCode', 'engineCc', 'installments']);

function toFieldErrorMap(errors: ApiFieldError[]): Record<string, string> {
  const map: Record<string, string> = {};
  for (const error of errors) {
    map[error.field] = error.message;
  }
  return map;
}

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
  const [phase, setPhase] = useState<FormPhase>('editing');
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [quote, setQuote] = useState<QuoteResponse | null>(null);

  // Unmount guard, same intent/rationale as LoginForm.tsx's cancelledRef:
  // the request can resolve after the user navigates away mid-submit, and
  // the mount effect must explicitly reset it to `false` so StrictMode's
  // dev double-invoke doesn't leave a stale `true` behind after first mount.
  const cancelledRef = useRef(false);
  useEffect(() => {
    cancelledRef.current = false;
    return () => {
      cancelledRef.current = true;
    };
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (phase === 'submitting') return;
    setPhase('submitting');
    setFormError(null);
    setFieldErrors({});
    setQuote(null);

    const body: CreateQuoteRequest = {
      driverAge: Number(driverAge),
      regionCode,
      engineCc: Number(engineCc),
      installments: Number(installments),
    };

    try {
      const response = await apiFetch<QuoteResponse>('/api/v1/quotes', {
        method: 'POST',
        authenticated: true,
        body,
      });
      if (cancelledRef.current) return;

      setPhase('editing');
      setQuote(response);
    } catch (error) {
      if (cancelledRef.current) return;

      // Form stays editable after any error - never locked/cleared.
      setPhase('editing');

      if (error instanceof ApiRequestError && error.fieldErrors && error.fieldErrors.length > 0) {
        const map = toFieldErrorMap(error.fieldErrors);
        setFieldErrors(map);
        if (!Object.keys(map).some((field) => KNOWN_FIELDS.has(field))) {
          setFormError(GENERIC_ERROR_MESSAGE);
        }
        return;
      }
      setFormError(GENERIC_ERROR_MESSAGE);
    }
  }

  const submitting = phase === 'submitting';

  return (
    <section>
      <h2>{t('quote.form.heading')}</h2>
      <form onSubmit={handleSubmit} noValidate>
        <div>
          <label htmlFor="quote-driverAge">{t('quote.form.driverAge')}</label>
          <input
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
            aria-invalid={fieldErrors.driverAge ? true : undefined}
            aria-describedby={fieldErrors.driverAge ? 'quote-driverAge-error' : undefined}
          />
          {fieldErrors.driverAge && (
            <p role="alert" id="quote-driverAge-error">
              {fieldErrors.driverAge}
            </p>
          )}
        </div>
        <div>
          <label htmlFor="quote-regionCode">{t('quote.form.regionCode')}</label>
          <input
            id="quote-regionCode"
            name="regionCode"
            type="text"
            required
            value={regionCode}
            onChange={(event) => setRegionCode(event.target.value)}
            disabled={submitting}
            aria-invalid={fieldErrors.regionCode ? true : undefined}
            aria-describedby={fieldErrors.regionCode ? 'quote-regionCode-error' : undefined}
          />
          {fieldErrors.regionCode && (
            <p role="alert" id="quote-regionCode-error">
              {fieldErrors.regionCode}
            </p>
          )}
        </div>
        <div>
          <label htmlFor="quote-engineCc">{t('quote.form.engineCc')}</label>
          <input
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
            aria-invalid={fieldErrors.engineCc ? true : undefined}
            aria-describedby={fieldErrors.engineCc ? 'quote-engineCc-error' : undefined}
          />
          {fieldErrors.engineCc && (
            <p role="alert" id="quote-engineCc-error">
              {fieldErrors.engineCc}
            </p>
          )}
        </div>
        <div>
          <label htmlFor="quote-installments">{t('quote.form.installments')}</label>
          <input
            id="quote-installments"
            name="installments"
            type="number"
            min={1}
            max={4}
            required
            value={installments}
            onChange={(event) => setInstallments(event.target.value)}
            disabled={submitting}
            aria-invalid={fieldErrors.installments ? true : undefined}
            aria-describedby={fieldErrors.installments ? 'quote-installments-error' : undefined}
          />
          {fieldErrors.installments && (
            <p role="alert" id="quote-installments-error">
              {fieldErrors.installments}
            </p>
          )}
        </div>
        {formError && (
          <p role="alert" data-testid="quote-error">
            {formError}
          </p>
        )}
        <button type="submit" disabled={submitting}>
          {submitting ? t('quote.form.submitting') : t('quote.form.submit')}
        </button>
      </form>
      {quote && <QuoteResult quote={quote} />}
    </section>
  );
}
