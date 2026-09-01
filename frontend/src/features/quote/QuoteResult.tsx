import type { QuoteResponse } from './QuoteForm';
import type { PolicyResponse } from '../policy/policyTypes';
import { useTranslation } from 'react-i18next';
import { Card } from '../../components/ui/Card';

/**
 * Everything this breakdown renders. Story 8.3 widened the prop from
 * `QuoteResponse` to this: a policy stores every component under the same
 * names, so both screens show one identical breakdown rather than two that
 * merely look alike (FR-M3-07/FR-M3-10) - which is what makes "presented
 * identically" checkable instead of aspirational.
 */
export type QuoteBreakdown = Pick<
  QuoteResponse & PolicyResponse,
  | 'zoneId'
  | 'basePremium'
  | 'ageSurcharge'
  | 'bonusMalusClass'
  | 'bonusMalusFactor'
  | 'oneTimePremium'
  | 'installments'
  | 'installmentFee'
  | 'totalPremium'
  | 'installmentAmount'
  | 'currency'
>;

interface QuoteResultProps {
  quote: QuoteBreakdown;
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
    <section data-testid="quote-result" aria-label={t('quote.result.label')} className="mt-6">
      <Card title={t('quote.result.heading')} titleAs="h3">
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <dt className="text-text-muted">{t('quote.result.zone')}</dt>
          <dd data-testid="quote-zoneName" className="text-right">
            {t(`quote.result.zones.${quote.zoneId}`, {
              defaultValue: t('quote.result.zoneFallback', { zoneId: quote.zoneId }),
            })}
          </dd>

          <dt className="text-text-muted">{t('quote.result.basePremium')}</dt>
          <dd data-testid="quote-basePremium" className="text-right">
            {quote.basePremium} {quote.currency}
          </dd>

          <dt className="text-text-muted">{t('quote.result.ageSurcharge')}</dt>
          <dd data-testid="quote-ageSurcharge" className="text-right">
            {quote.ageSurcharge} {quote.currency}
          </dd>

          <dt className="text-text-muted">{t('quote.result.bonusMalusFactor')}</dt>
          <dd data-testid="quote-bonusMalusFactor" className="text-right">
            {t(`quote.form.bonusMalusClasses.${quote.bonusMalusClass}`, {
              defaultValue: quote.bonusMalusClass,
            })}{' '}
            (&times;{quote.bonusMalusFactor})
          </dd>

          <dt className="text-text-muted">{t('quote.result.oneTimePremium')}</dt>
          <dd data-testid="quote-oneTimePremium" className="text-right">
            {quote.oneTimePremium} {quote.currency}
          </dd>

          <dt className="text-text-muted">{t('quote.result.installments')}</dt>
          <dd data-testid="quote-installments" className="text-right">
            {quote.installments}
          </dd>

          <dt className="text-text-muted">{t('quote.result.installmentFee')}</dt>
          <dd data-testid="quote-installmentFee" className="text-right">
            {quote.installmentFee} {quote.currency}
          </dd>

          <dt className="col-span-2 mt-2 border-t border-border pt-2 text-base font-semibold text-text">
            {t('quote.result.totalPremium')}
          </dt>
          <dd
            data-testid="quote-totalPremium"
            className="col-span-2 text-right text-base font-semibold text-text"
          >
            {quote.totalPremium} {quote.currency}
          </dd>

          <dt className="text-text-muted">{t('quote.result.installmentAmount')}</dt>
          <dd data-testid="quote-installmentAmount" className="text-right">
            {quote.installmentAmount} {quote.currency}
          </dd>
        </dl>
      </Card>
    </section>
  );
}
