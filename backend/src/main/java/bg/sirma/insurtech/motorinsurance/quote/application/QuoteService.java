package bg.sirma.insurtech.motorinsurance.quote.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import bg.sirma.insurtech.motorinsurance.quote.domain.QuoteInput;
import bg.sirma.insurtech.motorinsurance.quote.domain.QuotePricingService;
import bg.sirma.insurtech.motorinsurance.quote.persistence.QuoteEntity;
import bg.sirma.insurtech.motorinsurance.quote.persistence.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuoteService {

    private static final Duration QUOTE_VALIDITY = Duration.ofDays(30);

    private final QuoteRepository quoteRepository;
    private final QuotePricingService pricingService;

    public QuoteService(QuoteRepository quoteRepository, QuotePricingService pricingService) {
        this.quoteRepository = quoteRepository;
        this.pricingService = pricingService;
    }

    @Transactional
    public QuoteView create(CreateQuoteCommand command) {
        validate(command);

        var input = new QuoteInput(
                command.driverAge(),
                command.drivingExperienceYears(),
                command.region(),
                command.vehiclePowerKw(),
                command.bonusMalusLevel());
        var calculation = pricingService.calculate(input);
        var createdAt = Instant.now();
        var quote = QuoteEntity.create(
                UUID.randomUUID(),
                input,
                calculation,
                createdAt,
                createdAt.plus(QUOTE_VALIDITY));

        return quoteRepository.save(quote).toView();
    }

    @Transactional(readOnly = true)
    public QuoteView get(UUID quoteId) {
        return quoteRepository.findById(quoteId)
                .orElseThrow(() -> new QuoteNotFoundException(quoteId))
                .toView();
    }

    private void validate(CreateQuoteCommand command) {
        var maximumPossibleExperience = command.driverAge() - 17;
        if (command.drivingExperienceYears() > maximumPossibleExperience) {
            throw new QuoteValidationException(
                    "Driving experience cannot be greater than driver age minus 17 years");
        }
    }
}
