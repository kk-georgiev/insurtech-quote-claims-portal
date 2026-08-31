package com.motorinsurance.quote.application;

import com.motorinsurance.pricing.application.PricingResult;
import com.motorinsurance.pricing.application.PricingService;
import com.motorinsurance.quote.api.CreateQuoteRequest;
import com.motorinsurance.quote.api.QuoteResponse;
import com.motorinsurance.quote.domain.Quote;
import com.motorinsurance.quote.persistence.QuoteRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final PricingService pricingService;
    private final QuoteRepository quoteRepository;

    public QuoteService(PricingService pricingService, QuoteRepository quoteRepository) {
        this.pricingService = pricingService;
        this.quoteRepository = quoteRepository;
    }

    @Transactional
    public QuoteResponse calculate(CreateQuoteRequest request, UUID customerId) {
        PricingResult result = pricingService.calculate(
                request.driverAge(),
                request.regionCode(),
                request.engineCc(),
                request.installments(),
                request.bonusMalusClass());

        Quote quote = new Quote(
                customerId,
                request.driverAge(),
                result.regionCode(),
                request.engineCc(),
                result.zoneId(),
                result.zoneName(),
                result.basePremium(),
                result.ageSurcharge(),
                result.bonusMalusClass(),
                result.bonusMalusFactor(),
                result.oneTimePremium(),
                request.installments(),
                result.installmentFee(),
                result.totalPremium(),
                result.installmentAmount(),
                result.currency());

        Quote saved;
        try {
            // saveAndFlush, not save: forces the INSERT (and thus the
            // customer_id foreign-key check) to run now, inside this
            // try/catch, rather than being deferred to this method's
            // @Transactional commit - where a DataIntegrityViolationException
            // would surface outside any catch block here.
            saved = quoteRepository.saveAndFlush(quote);
        } catch (DataIntegrityViolationException ex) {
            // Only reachable via customer_id's FK into users (every other
            // NOT NULL column is guaranteed by CreateQuoteRequest's Bean
            // Validation or PricingService's own computed values) - a token
            // whose subject no longer identifies a real account. No account-
            // deletion feature exists yet, so this is currently unreachable
            // in production, but was previously an unhandled 500 (epic-1-retro-item-5).
            log.warn("Rejected quote calculation: customer id {} has no matching account", customerId, ex);
            throw new QuoteCustomerNotFoundException(customerId);
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
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
                quote.getDriverAge(),
                quote.getRegionCode(),
                quote.getEngineCc(),
                quote.getZoneId(),
                quote.getZoneName(),
                quote.getBasePremium(),
                quote.getAgeSurcharge(),
                quote.getBonusMalusCode(),
                quote.getBonusMalusFactor(),
                quote.getOneTimePremium(),
                quote.getInstallments(),
                quote.getInstallmentFee(),
                quote.getTotalPremium(),
                quote.getInstallmentAmount(),
                quote.getCurrency());
    }
}
