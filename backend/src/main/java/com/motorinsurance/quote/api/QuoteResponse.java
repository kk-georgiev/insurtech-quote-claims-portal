package com.motorinsurance.quote.api;

import java.math.BigDecimal;

/**
 * Quote calculation response DTO (Story 1.5) - every component the total is
 * built from, not just the total (transparent breakdown). No {@code quoteId}
 * yet: this story only calculates, it doesn't persist - Story 1.6 adds
 * persistence and, with it, an id to retrieve by.
 */
public record QuoteResponse(
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
