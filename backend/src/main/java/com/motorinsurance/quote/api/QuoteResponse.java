package com.motorinsurance.quote.api;

import com.motorinsurance.quote.domain.QuoteStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Quote response DTO (Stories 1.5/1.6) - every component the total is built
 * from, not just the total, plus the persisted {@code id}/{@code createdAt}
 * a client needs to retrieve this exact quote again later, plus the
 * original inputs ({@code driverAge}/{@code regionCode}/{@code engineCc}) -
 * Story 1.6's AC asks for "the full original quote" on retrieval, not just
 * the resulting breakdown (review-loop finding, Story 1.6). {@code regionCode}
 * is the normalized form actually priced against ({@link
 * com.motorinsurance.pricing.application.PricingResult}'s javadoc), not
 * necessarily the exact casing the client originally submitted.
 */
public record QuoteResponse(
        UUID id,
        Instant createdAt,
        int driverAge,
        String regionCode,
        int engineCc,
        short zoneId,
        String zoneName,
        BigDecimal basePremium,
        BigDecimal ageSurcharge,
        // Story 6.1 - grows the response additively (Architecture Spine
        // AD-13): every field above keeps its name, type, and meaning.
        String bonusMalusClass,
        BigDecimal bonusMalusFactor,
        BigDecimal oneTimePremium,
        int installments,
        BigDecimal installmentFee,
        BigDecimal totalPremium,
        BigDecimal installmentAmount,
        String currency,
        // Story 6.2 - also additive (AD-13). `status` is derived, never
        // stored (AD-3); `acceptedAt` stays null through this milestone's
        // Epic 6 (see Quote's javadoc) and is only ever set by Story 8.1.
        LocalDate validUntil,
        QuoteStatus status,
        Instant acceptedAt,
        // Story 8.3 - the last field Architecture Spine AD-13 anticipates,
        // and additive like every one before it. Null unless this quote has
        // been accepted; it is what lets an accepted quote's screen link to
        // the contract it produced rather than making the client fetch and
        // match the whole policy list.
        UUID policyId) {
}
