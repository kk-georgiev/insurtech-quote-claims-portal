package com.motorinsurance.pricing.application;

import com.motorinsurance.shared.api.ApiError;
import com.motorinsurance.shared.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a quote's {@code regionCode} doesn't exist in
 * {@code region_zone_map} - either a typo, or a plate-prefix code this
 * tariff doesn't cover yet (see the PRD addendum's open caveats on
 * {@code BA}/{@code CP}/{@code XX}). Maps to HTTP 400 with code
 * {@code PRICING_UNKNOWN_REGION} through the generic {@code ApiException}
 * handler in {@code shared.api.GlobalExceptionHandler} - no one-off catch
 * block.
 */
public class UnknownRegionCodeException extends ApiException {

    public UnknownRegionCodeException(String regionCode) {
        super(
                HttpStatus.BAD_REQUEST.value(),
                "PRICING_UNKNOWN_REGION",
                "Unknown region code: " + regionCode,
                List.of(new ApiError.FieldError("regionCode", "Unknown region code: " + regionCode)));
    }
}
