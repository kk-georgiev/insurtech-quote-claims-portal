package com.motorinsurance.pricing.application;

import java.math.BigDecimal;

/**
 * Every component {@link PricingService#calculate} builds the total from -
 * the transparent breakdown Story 1.5 requires, not just the final number.
 */
public record PricingResult(
        short zoneId,
        String zoneName,
        BigDecimal basePremium,
        BigDecimal ageSurcharge,
        BigDecimal oneTimePremium,
        int installments,
        BigDecimal installmentFee,
        BigDecimal totalPremium,
        BigDecimal installmentAmount,
        String currency) {
}
