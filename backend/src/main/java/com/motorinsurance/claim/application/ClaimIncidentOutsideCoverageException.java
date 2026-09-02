package com.motorinsurance.claim.application;

import com.motorinsurance.shared.api.ApiException;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the claimed incident date falls outside the policy's coverage
 * window (FR-M4-05, M4-AD-11). A conflict with the *policy's* state, not a
 * shape problem with the request - 409, no field error, mirroring how {@code
 * quote.application.QuoteExpiredException} treats an analogous date-vs-state
 * conflict.
 *
 * <p>Boundaries are inclusive at both ends (NFR-8, M3 AD-6): an incident
 * exactly on {@code coverageStart} or {@code coverageEnd} is covered, so this
 * is thrown only strictly outside that closed range.
 */
public class ClaimIncidentOutsideCoverageException extends ApiException {

    public ClaimIncidentOutsideCoverageException(LocalDate incidentDate, LocalDate coverageStart, LocalDate coverageEnd) {
        super(
                HttpStatus.CONFLICT.value(),
                "CLAIM_INCIDENT_OUTSIDE_COVERAGE",
                "Incident date " + incidentDate + " is outside the policy's coverage window [" + coverageStart + ", "
                        + coverageEnd + "]");
    }
}
