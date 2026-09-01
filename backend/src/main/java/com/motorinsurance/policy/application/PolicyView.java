package com.motorinsurance.policy.application;

import com.motorinsurance.policy.domain.PolicyStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An issued policy as everything outside {@code policy} sees it - the
 * "created policy representation" the accept endpoint returns
 * (Architecture Spine AD-2, M3).
 *
 * <p>Lives in {@code application}, not {@code api}, for the same reason
 * {@code pricing.application.PricingResult} does: it crosses a module
 * boundary, and cross-module access goes through the target's
 * {@code application} package only (M1 AD-2). That is what lets
 * {@code quote.api} return this record straight from the accept endpoint
 * without importing anything from a {@code policy.api} package - which does
 * not exist yet in any case; Story 8.3 adds the policy read endpoints.
 *
 * <p>Carries the whole stored snapshot, so a client comparing a policy
 * against the quote it came from sees the same figures (FR-M3-07).
 * {@code quoteId} is included as the record of which offer produced this
 * contract - exposing it is not the same as dereferencing it, which AD-4
 * forbids and nothing here does.
 *
 * <p>{@code status} is derived on every read from the coverage dates
 * (Story 8.3, FR-M3-09, AD-3), never stored - so a policy becomes
 * {@code ACTIVE} and later {@code EXPIRED} on its own, with no column to
 * keep in step and no job to run.
 */
public record PolicyView(
        UUID id,
        String policyNumber,
        UUID quoteId,
        Instant issuedAt,
        LocalDate coverageStart,
        LocalDate coverageEnd,
        String holderName,
        String vehicleRegistration,
        String vehicleVin,
        int driverAge,
        String regionCode,
        int engineCc,
        short zoneId,
        String zoneName,
        BigDecimal basePremium,
        BigDecimal ageSurcharge,
        String bonusMalusClass,
        BigDecimal bonusMalusFactor,
        BigDecimal oneTimePremium,
        int installments,
        BigDecimal installmentFee,
        BigDecimal totalPremium,
        BigDecimal installmentAmount,
        String currency,
        PolicyStatus status) {
}
