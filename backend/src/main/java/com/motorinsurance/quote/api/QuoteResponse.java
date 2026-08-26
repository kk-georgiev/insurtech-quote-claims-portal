package com.motorinsurance.quote.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Quote response DTO (Stories 1.5/1.6) - every component the total is built
 * from, not just the total, plus the persisted {@code id}/{@code createdAt}
 * a client needs to retrieve this exact quote again later.
 */
public record QuoteResponse(
        UUID id,
        Instant createdAt,
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
