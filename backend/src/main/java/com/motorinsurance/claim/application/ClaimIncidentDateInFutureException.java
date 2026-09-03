package com.motorinsurance.claim.application;

import com.motorinsurance.shared.api.ApiError;
import com.motorinsurance.shared.api.ApiException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the claimed incident date is after today (FR-M4-06, M4-AD-11:
 * "a future incident date is pure input validation ... a 400 with a
 * field-level error on {@code incidentDate}"). 400 with a field error,
 * mirroring {@code quote.application.CoverageStartInPastException}'s shape
 * for the identical reason: "in the future" is only meaningful against the
 * business zone's today, which comes from the injected {@link
 * java.time.Clock} (Architecture Spine AD-6) - a plain {@code @PastOrPresent}
 * Bean Validation annotation would resolve today from the JVM default zone
 * instead, exactly the midnight-drift AD-6 exists to prevent. That is why
 * this is a dedicated exception in {@code application} rather than an
 * annotation on {@code claim.api.SubmitClaimForm}.
 *
 * <p>Today itself is accepted - the boundary is inclusive, matching every
 * other date boundary this milestone defines.
 */
public class ClaimIncidentDateInFutureException extends ApiException {

    private static final String INCIDENT_DATE_FIELD = "incidentDate";

    public ClaimIncidentDateInFutureException(LocalDate incidentDate, LocalDate today) {
        super(
                HttpStatus.BAD_REQUEST.value(),
                "CLAIM_INCIDENT_DATE_IN_FUTURE",
                "Incident date " + incidentDate + " is after today (" + today + ")",
                List.of(new ApiError.FieldError(INCIDENT_DATE_FIELD, "The incident date cannot be in the future")));
    }
}
