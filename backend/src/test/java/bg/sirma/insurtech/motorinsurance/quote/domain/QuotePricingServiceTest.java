package bg.sirma.insurtech.motorinsurance.quote.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuotePricingServiceTest {

    private final QuotePricingService pricingService = new QuotePricingService();

    @Test
    void shouldCalculateTransparentPremiumForTypicalDriver() {
        var input = new QuoteInput(35, 12, RegionRisk.SOFIA, 100, BonusMalusLevel.NEUTRAL);

        var result = pricingService.calculate(input);

        assertThat(result.basePremium()).isEqualByComparingTo("180.00");
        assertThat(result.ageFactor()).isEqualByComparingTo("1.000");
        assertThat(result.experienceFactor()).isEqualByComparingTo("1.000");
        assertThat(result.regionFactor()).isEqualByComparingTo("1.200");
        assertThat(result.powerFactor()).isEqualByComparingTo("1.000");
        assertThat(result.bonusMalusFactor()).isEqualByComparingTo("1.000");
        assertThat(result.premium()).isEqualByComparingTo("216.00");
        assertThat(result.currency()).isEqualTo("EUR");
        assertThat(result.pricingVersion()).isEqualTo("2026.1-demo");
    }

    @Test
    void shouldApplyAllRiskFactorsForYoungInexperiencedDriver() {
        var input = new QuoteInput(20, 1, RegionRisk.SOFIA, 180, BonusMalusLevel.MALUS_50);

        var result = pricingService.calculate(input);

        assertThat(result.ageFactor()).isEqualByComparingTo("1.350");
        assertThat(result.experienceFactor()).isEqualByComparingTo("1.300");
        assertThat(result.powerFactor()).isEqualByComparingTo("1.350");
        assertThat(result.bonusMalusFactor()).isEqualByComparingTo("1.500");
        assertThat(result.premium()).isEqualByComparingTo("767.64");
    }

    @Test
    void shouldApplyBonusForLowerRiskQuote() {
        var input = new QuoteInput(45, 20, RegionRisk.OTHER, 70, BonusMalusLevel.BONUS_20);

        var result = pricingService.calculate(input);

        assertThat(result.premium()).isEqualByComparingTo("129.60");
    }
}
