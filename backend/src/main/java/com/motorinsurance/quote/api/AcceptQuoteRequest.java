package com.motorinsurance.quote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * What a client supplies when accepting a quote (Story 8.1, FR-M3-04 and
 * FR-M3-08): when cover should begin, who holds the policy, and which
 * vehicle it covers. Nothing about price - that is copied from the quote
 * being accepted, never re-sent by the caller.
 *
 * <p>Identity validation is format-level only, by decision: there is no
 * external registry lookup, so these patterns are deliberately permissive
 * about what a real plate looks like and strict only about what could not
 * be one.
 *
 * <p>Exactly one of {@code vehicleRegistration} / {@code vehicleVin} must
 * be supplied. Both patterns therefore also admit the empty string - a form
 * that submits the field it did not use as {@code ""} gets the specific
 * {@code QUOTE_VEHICLE_IDENTIFIER_REQUIRED} message from the application
 * layer rather than a generic pattern failure. Blank is treated as absent
 * there; see {@code quote.application.QuoteAcceptanceTransaction}.
 *
 * <p>{@code coverageStart} is only checked for presence here: whether it is
 * in the past depends on the business zone's today, which is the injected
 * clock's job (Architecture Spine AD-6), not Bean Validation's.
 *
 * <p>Both text patterns are anchored on a non-space character at each end,
 * and the length bounds live inside the pattern rather than in a separate
 * {@code @Size}. Validation runs before the application layer trims, so a
 * padded value like {@code "  AB  "} would otherwise satisfy a bare length
 * rule and then be stored two characters long - the minimum has to hold
 * against the value that actually gets persisted.
 */
public record AcceptQuoteRequest(
        @NotNull LocalDate coverageStart,
        @NotBlank @Size(min = 2, max = 120) @Pattern(regexp = "^\\S(.*\\S)?$") String holderName,
        @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9 -]{2,14}[A-Za-z0-9]$") String vehicleRegistration,
        @Pattern(regexp = "^$|^[A-HJ-NPR-Z0-9]{17}$", flags = Pattern.Flag.CASE_INSENSITIVE) String vehicleVin) {
}
