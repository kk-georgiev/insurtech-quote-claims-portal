import type { QuoteResponse } from './QuoteForm';
import { useTranslation } from 'react-i18next';

interface QuoteResultProps {
  quote: QuoteResponse;
}

/**
 * Renders the full breakdown a successful `POST /api/v1/quotes` returns
 * (Story 1.7). Every amount is interpolated exactly as the API sent it -
 * never re-derived, re-rounded, or re-formatted client-side (spec Boundaries
 * & Constraints: "Money renders exactly as the API returns it").
 */
export function QuoteResult({ quote }: QuoteResultProps) {
  const { t } = useTranslation();

  return (
    <section data-testid="quote-result" aria-label={t('quote.result.label')}>
      <h3>{t('quote.result.heading')}</h3>
      <dl>
        <dt>{t('quote.result.zone')}</dt>
        <dd data-testid="quote-zoneName">{quote.zoneName}</dd>

        <dt>{t('quote.result.basePremium')}</dt>
        <dd data-testid="quote-basePremium">
          {quote.basePremium} {quote.currency}
        </dd>

        <dt>{t('quote.result.ageSurcharge')}</dt>
        <dd data-testid="quote-ageSurcharge">
          {quote.ageSurcharge} {quote.currency}
        </dd>

        <dt>{t('quote.result.oneTimePremium')}</dt>
        <dd data-testid="quote-oneTimePremium">
          {quote.oneTimePremium} {quote.currency}
        </dd>

        <dt>{t('quote.result.installments')}</dt>
        <dd data-testid="quote-installments">{quote.installments}</dd>

        <dt>{t('quote.result.installmentFee')}</dt>
        <dd data-testid="quote-installmentFee">
          {quote.installmentFee} {quote.currency}
        </dd>

        <dt>{t('quote.result.totalPremium')}</dt>
        <dd data-testid="quote-totalPremium">
          {quote.totalPremium} {quote.currency}
        </dd>

        <dt>{t('quote.result.installmentAmount')}</dt>
        <dd data-testid="quote-installmentAmount">
          {quote.installmentAmount} {quote.currency}
        </dd>
      </dl>
    </section>
  );
}
