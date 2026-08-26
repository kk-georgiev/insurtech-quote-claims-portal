package com.motorinsurance.quote.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Quote calculation request DTO (Story 1.5). {@code driverAge}/{@code engineCc}
 * carry the structural floor the tariff data itself starts at (18, 800) -
 * anything below is rejected here as a clean field-level error rather than
 * reaching {@code pricing} at all. {@code regionCode}/{@code installments}
 * can only be checked against reference data ({@code region_zone_map}/
 * {@code installment_plan}), so {@code pricing.application.PricingService}
 * validates those.
 */
public record CreateQuoteRequest(
        @NotNull @Min(18) Integer driverAge,
        @NotBlank @Size(max = 5) String regionCode,
        @NotNull @Min(800) Integer engineCc,
        @NotNull Integer installments) {
}
