package com.motorinsurance.quote.application;

import com.motorinsurance.pricing.application.PricingResult;
import com.motorinsurance.pricing.application.PricingService;
import com.motorinsurance.quote.api.CreateQuoteRequest;
import com.motorinsurance.quote.api.QuoteResponse;
import com.motorinsurance.quote.domain.Quote;
import com.motorinsurance.quote.persistence.QuoteRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quote module's use cases for Stories 1.5/1.6 - delegates calculation to
 * {@code pricing}'s sole entry point (AD-2), then persists the result
 * immediately as part of calculation (Story 1.6 AC - there is no separate
 * "save" step) and supports retrieving it back by id, scoped to the
 * requesting customer.
 */
@Service
public class QuoteService {

    private final PricingService pricingService;
    private final QuoteRepository quoteRepository;

    public QuoteService(PricingService pricingService, QuoteRepository quoteRepository) {
        this.pricingService = pricingService;
        this.quoteRepository = quoteRepository;
    }

    @Transactional
    public QuoteResponse calculate(CreateQuoteRequest request, UUID customerId) {
        PricingResult result = pricingService.calculate(
                request.driverAge(), request.regionCode(), request.engineCc(), request.installments());

        Quote quote = new Quote(
                customerId,
                request.driverAge(),
                request.regionCode(),
                request.engineCc(),
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
        Quote saved = quoteRepository.save(quote);

        return toResponse(saved);
    }

    public QuoteResponse getById(UUID id, UUID customerId) {
        Quote quote = quoteRepository
                .findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new QuoteNotFoundException(id));

        return toResponse(quote);
    }

    private QuoteResponse toResponse(Quote quote) {
        return new QuoteResponse(
                quote.getId(),
                quote.getCreatedAt(),
                quote.getZoneId(),
                quote.getZoneName(),
                quote.getBasePremium(),
                quote.getAgeSurcharge(),
                quote.getOneTimePremium(),
                quote.getInstallments(),
                quote.getInstallmentFee(),
                quote.getTotalPremium(),
                quote.getInstallmentAmount(),
                quote.getCurrency());
    }
}
