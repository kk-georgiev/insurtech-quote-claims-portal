package com.motorinsurance.policy.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The one, fully-formed issuance command {@code policy} accepts
 * (Architecture Spine AD-1, M3). Every value a policy needs is already
 * resolved by the caller: {@code policy} looks nothing up, and in
 * particular never reads {@code quotes} to fill a gap here.
 *
 * <p>Carries the whole breakdown rather than a quote reference because a
 * policy copies, it never references (AD-4) - the premium is copied
 * verbatim from the accepted quote and never recalculated (NFR-1).
 *
 * <p>{@code coverageStart} is the client's choice; the coverage period's
 * length is {@code policy}'s own rule, so no coverage end appears here (see
 * {@link PolicyService}). Exactly one of {@code vehicleRegistration} /
 * {@code vehicleVin} is non-null; the caller has already enforced that, and
 * {@code ck_policies_vehicle_identity} is the backstop.
 *
 * <p>{@code issuedAt} is resolved once by the caller, from the same clock
 * read that stamps the quote's {@code accepted_at} and that "today" was
 * compared against (AD-6). Reading the clock a second time here would let a
 * midnight boundary fall between validation and issuance - a coverage start
 * validated as "today" could then be stored on a policy issued the next
 * day, and the policy number could carry the following year.
 */
public record IssuePolicyCommand(
        UUID quoteId,
        UUID customerId,
        Instant issuedAt,
        LocalDate coverageStart,
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
        // short, not int: the value is copied from an already-persisted
        // SMALLINT column, so widening here only to narrow again on the way
        // into Policy would hide a range problem rather than prevent one.
        short installments,
        BigDecimal installmentFee,
        BigDecimal totalPremium,
        BigDecimal installmentAmount,
        String currency) {
}
