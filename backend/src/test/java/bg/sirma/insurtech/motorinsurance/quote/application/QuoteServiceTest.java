package bg.sirma.insurtech.motorinsurance.quote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import bg.sirma.insurtech.motorinsurance.quote.domain.BonusMalusLevel;
import bg.sirma.insurtech.motorinsurance.quote.domain.QuoteCalculation;
import bg.sirma.insurtech.motorinsurance.quote.domain.QuotePricingService;
import bg.sirma.insurtech.motorinsurance.quote.domain.RegionRisk;
import bg.sirma.insurtech.motorinsurance.quote.persistence.QuoteEntity;
import bg.sirma.insurtech.motorinsurance.quote.persistence.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private QuoteRepository quoteRepository;

    @Mock
    private QuotePricingService pricingService;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteService(quoteRepository, pricingService);
    }

    @Test
    void shouldCreateAndPersistQuoteSnapshot() {
        var command = new CreateQuoteCommand(35, 12, RegionRisk.SOFIA, 100, BonusMalusLevel.NEUTRAL);
        var calculation = calculation("216.00");
        when(pricingService.calculate(any())).thenReturn(calculation);
        when(quoteRepository.save(any(QuoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = quoteService.create(command);

        assertThat(result.id()).isNotNull();
        assertThat(result.status().name()).isEqualTo("CREATED");
        assertThat(result.input().driverAge()).isEqualTo(35);
        assertThat(result.calculation().premium()).isEqualByComparingTo("216.00");
        assertThat(result.validUntil()).isAfter(result.createdAt());
    }

    @Test
    void shouldRejectImpossibleDrivingExperience() {
        var command = new CreateQuoteCommand(20, 10, RegionRisk.OTHER, 80, BonusMalusLevel.NEUTRAL);

        assertThatThrownBy(() -> quoteService.create(command))
                .isInstanceOf(QuoteValidationException.class)
                .hasMessageContaining("driver age minus 17");
        verifyNoInteractions(pricingService, quoteRepository);
    }

    private QuoteCalculation calculation(String premium) {
        return new QuoteCalculation(
                new BigDecimal("180.00"),
                new BigDecimal("1.000"),
                new BigDecimal("1.000"),
                new BigDecimal("1.200"),
                new BigDecimal("1.000"),
                new BigDecimal("1.000"),
                new BigDecimal(premium),
                "EUR",
                "2026.1-demo");
    }
}
