package com.motorinsurance.quote.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Quote calculation request DTO (Story 1.5). {@code driverAge}/{@code engineCc}
 * carry the structural floor the tariff data itself starts at (18, 800) -
 * anything below is rejected here as a clean field-level error rather than
 * reaching {@code pricing} at all. {@code regionCode} gets only a blank
 * check here (its actual validity is a {@code region_zone_map} lookup -
 * {@code pricing.application.PricingService}'s job, not a length/format
 * guess here). {@code installments} gets a wide {@code [1,4]} bound - not
 * because those are the only valid counts (that's still pricing's
 * {@code installment_plan} lookup to decide - 3 is in range but still
 * rejected there), but because {@code PricingService} narrows this value to
 * {@code short} for that lookup, and an unbounded {@code int} silently
 * aliases into a valid plan id on overflow (e.g. 65540 -> (short) 4) instead
 * of being rejected - review-loop finding, Story 1.5.
 */
public record CreateQuoteRequest(
        @NotNull @Min(18) Integer driverAge,
        @NotBlank String regionCode,
        @NotNull @Min(800) Integer engineCc,
        @NotNull @Min(1) @Max(4) Integer installments) {
}
