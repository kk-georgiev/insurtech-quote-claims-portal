package com.motorinsurance.quote.application;

import com.motorinsurance.shared.api.ApiError;
import com.motorinsurance.shared.api.ApiException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the requested coverage start date is further ahead than the
 * portal schedules cover (Story 8.2). 400 with code
 * {@code QUOTE_COVERAGE_START_TOO_FAR_AHEAD} and a field error on
 * {@code coverageStart} - the mirror image of {@link
 * CoverageStartInPastException}, and modelled on it exactly.
 *
 * <p>The horizon is a <strong>project rule, not an official or legal
 * requirement</strong> (product-owner decision, 2026-09-01), and is stated
 * as such wherever it is surfaced to a reader (NFR-8) - the same posture
 * the bonus-malus scale's provenance takes.
 *
 * <p>This check is the authority. The acceptance form also puts a
 * {@code max} on its date input, but that is a courtesy that stops the
 * common case in the browser: the endpoint is reachable without the form,
 * so a frontend-only bound would not be a bound at all (M1 AD-4).
 *
 * <p>Like every other date boundary this milestone defines, the limit is
 * inclusive: the last permitted day is itself acceptable.
 */
public class CoverageStartTooFarAheadException extends ApiException {

    public CoverageStartTooFarAheadException(LocalDate coverageStart, LocalDate latestPermitted) {
        super(
                HttpStatus.BAD_REQUEST.value(),
                "QUOTE_COVERAGE_START_TOO_FAR_AHEAD",
                "Coverage start " + coverageStart + " is after the latest permitted start (" + latestPermitted + ")",
                List.of(new ApiError.FieldError("coverageStart", "Coverage starts too far in the future")));
    }
}
