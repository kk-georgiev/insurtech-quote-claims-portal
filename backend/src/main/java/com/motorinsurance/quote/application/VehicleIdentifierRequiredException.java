package com.motorinsurance.quote.application;

import com.motorinsurance.shared.api.ApiError;
import com.motorinsurance.shared.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an acceptance names neither a vehicle registration nor a VIN,
 * or names both (Story 8.1, FR-M3-08). 400 with code
 * {@code QUOTE_VEHICLE_IDENTIFIER_REQUIRED} and a field error on
 * {@code vehicleRegistration}, the field the acceptance form leads with.
 *
 * <p>A cross-field rule, so it lives here rather than as a per-field Bean
 * Validation annotation: a class-level constraint would attribute the
 * violation to a synthetic property name the form has no field for.
 * {@code ck_policies_vehicle_identity} in the schema is the backstop, not
 * the check a client ever sees.
 *
 * <p>Exactly one, not "at least one": a registered vehicle is identified by
 * its plate and an unregistered one by its VIN, and accepting both would
 * leave two identities on a contract with nothing saying which governs.
 */
public class VehicleIdentifierRequiredException extends ApiException {

    public VehicleIdentifierRequiredException() {
        super(
                HttpStatus.BAD_REQUEST.value(),
                "QUOTE_VEHICLE_IDENTIFIER_REQUIRED",
                "Exactly one of vehicleRegistration or vehicleVin is required",
                List.of(new ApiError.FieldError("vehicleRegistration", "Provide either a registration number or a VIN")));
    }
}
