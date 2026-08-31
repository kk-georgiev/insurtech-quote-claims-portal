package com.motorinsurance.pricing.application;

import com.motorinsurance.shared.api.ApiError;
import com.motorinsurance.shared.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a quote's {@code bonusMalusClass} doesn't exist in
 * {@code bonus_malus_class} - a typo or an invented class name. Maps to
 * HTTP 400 with code {@code PRICING_UNKNOWN_BONUS_MALUS_CLASS} through the
 * generic {@code ApiException} handler in {@code shared.api.GlobalExceptionHandler}
 * - no one-off catch block. An unknown class fails closed; it is never
 * silently defaulted to {@code NEUTRAL} (Architecture Spine AD-8).
 */
public class UnknownBonusMalusClassException extends ApiException {

    public UnknownBonusMalusClassException(String bonusMalusClass) {
        super(
                HttpStatus.BAD_REQUEST.value(),
                "PRICING_UNKNOWN_BONUS_MALUS_CLASS",
                "Unknown bonus-malus class: " + bonusMalusClass,
                List.of(new ApiError.FieldError(
                        "bonusMalusClass", "Unknown bonus-malus class: " + bonusMalusClass)));
    }
}
