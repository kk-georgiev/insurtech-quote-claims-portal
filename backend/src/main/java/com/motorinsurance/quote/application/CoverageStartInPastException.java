package com.motorinsurance.quote.application;

import com.motorinsurance.shared.api.ApiError;
import com.motorinsurance.shared.api.ApiException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the requested coverage start date is already in the past
 * (Story 8.1, FR-M3-04). 400 with code {@code QUOTE_COVERAGE_START_IN_PAST}
 * and a field error on {@code coverageStart}, so the acceptance form can
 * put the message beside the field rather than at the top of the screen -
 * the same field-level shape {@code PRICING_UNKNOWN_REGION} already uses.
 *
 * <p>Lives in {@code application} rather than as a Bean Validation
 * annotation on the request DTO because "past" is only meaningful against
 * the business zone's today, which comes from the injected {@link
 * java.time.Clock} (Architecture Spine AD-6). {@code @FutureOrPresent} would
 * resolve today from the JVM default zone instead, which is exactly the
 * midnight-drift AD-6 exists to prevent.
 *
 * <p>Today itself is accepted - the boundary is inclusive, matching every
 * other date boundary this milestone defines.
 */
public class CoverageStartInPastException extends ApiException {

    public CoverageStartInPastException(LocalDate coverageStart, LocalDate today) {
        super(
                HttpStatus.BAD_REQUEST.value(),
                "QUOTE_COVERAGE_START_IN_PAST",
                "Coverage start " + coverageStart + " is before today (" + today + ")",
                List.of(new ApiError.FieldError("coverageStart", "Coverage cannot start in the past")));
    }
}
