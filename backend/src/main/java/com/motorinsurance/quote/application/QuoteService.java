package com.motorinsurance.quote.application;

import com.motorinsurance.pricing.application.PricingResult;
import com.motorinsurance.pricing.application.PricingService;
import com.motorinsurance.quote.api.CreateQuoteRequest;
import com.motorinsurance.quote.api.QuoteResponse;
import org.springframework.stereotype.Service;

/**
 * Quote module's use case for Story 1.5 - delegates the actual calculation
 * to {@code pricing}'s sole entry point (AD-2) and maps the result onto the
 * API response shape. No persistence yet (Story 1.6).
 */
@Service
public class QuoteService {

    private final PricingService pricingService;

    public QuoteService(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    public QuoteResponse calculate(CreateQuoteRequest request) {
        PricingResult result = pricingService.calculate(
                request.driverAge(), request.regionCode(), request.engineCc(), request.installments());

        return new QuoteResponse(
                result.zoneId(),
                result.zoneName(),
                result.basePremium(),
                result.ageSurcharge(),
                result.oneTimePremium(),
                result.installments(),
                result.installmentFee(),
                result.totalPremium(),
                result.installmentAmount(),
                result.currency());
    }
}
