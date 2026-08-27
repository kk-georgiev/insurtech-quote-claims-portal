package com.motorinsurance.pricing.application;

import java.math.BigDecimal;

/**
 * Every component {@link PricingService#calculate} builds the total from -
 * the transparent breakdown Story 1.5 requires, not just the final number.
 *
 * <p>{@code regionCode} is the normalized (trimmed, uppercased) value
 * actually used for the {@code zoneId}/{@code zoneName} lookup, not
 * whatever case a caller originally sent - callers persisting this result
 * (Story 1.6's {@code QuoteService}) need the canonical form, not the raw
 * input, so the two never drift apart (review-loop finding, Story 1.6).
 */
public record PricingResult(
        short zoneId,
        String zoneName,
        String regionCode,
        BigDecimal basePremium,
        BigDecimal ageSurcharge,
        BigDecimal oneTimePremium,
        int installments,
        BigDecimal installmentFee,
        BigDecimal totalPremium,
        BigDecimal installmentAmount,
        String currency) {
}
