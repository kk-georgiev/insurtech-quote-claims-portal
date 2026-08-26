package com.motorinsurance.pricing.application;

import com.motorinsurance.shared.api.ApiError;
import com.motorinsurance.shared.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a quote's {@code installments} value isn't one of the counts
 * configured in {@code installment_plan} (currently 1, 2, or 4). Maps to
 * HTTP 400 with code {@code PRICING_UNSUPPORTED_INSTALLMENTS} through the
 * generic {@code ApiException} handler in {@code shared.api.GlobalExceptionHandler}
 * - no one-off catch block.
 */
public class UnsupportedInstallmentCountException extends ApiException {

    public UnsupportedInstallmentCountException(int installments) {
        super(
                HttpStatus.BAD_REQUEST.value(),
                "PRICING_UNSUPPORTED_INSTALLMENTS",
                "Unsupported installment count: " + installments,
                List.of(new ApiError.FieldError(
                        "installments", "Unsupported installment count: " + installments)));
    }
}
