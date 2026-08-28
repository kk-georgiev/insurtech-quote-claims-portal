import type { QuoteResponse } from './QuoteForm';

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
  return (
    <section data-testid="quote-result" aria-label="Quote breakdown">
      <h3>Your quote</h3>
      <dl>
        <dt>Zone</dt>
        <dd data-testid="quote-zoneName">{quote.zoneName}</dd>

        <dt>Base premium</dt>
        <dd data-testid="quote-basePremium">
          {quote.basePremium} {quote.currency}
        </dd>

        <dt>Age surcharge</dt>
        <dd data-testid="quote-ageSurcharge">
          {quote.ageSurcharge} {quote.currency}
        </dd>

        <dt>One-time premium</dt>
        <dd data-testid="quote-oneTimePremium">
          {quote.oneTimePremium} {quote.currency}
        </dd>

        <dt>Installments</dt>
        <dd data-testid="quote-installments">{quote.installments}</dd>

        <dt>Installment fee</dt>
        <dd data-testid="quote-installmentFee">
          {quote.installmentFee} {quote.currency}
        </dd>

        <dt>Total premium</dt>
        <dd data-testid="quote-totalPremium">
          {quote.totalPremium} {quote.currency}
        </dd>

        <dt>Installment amount</dt>
        <dd data-testid="quote-installmentAmount">
          {quote.installmentAmount} {quote.currency}
        </dd>
      </dl>
    </section>
  );
}
