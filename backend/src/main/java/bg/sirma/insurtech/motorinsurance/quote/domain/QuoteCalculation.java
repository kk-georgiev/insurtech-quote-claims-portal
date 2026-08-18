package bg.sirma.insurtech.motorinsurance.quote.domain;

import java.math.BigDecimal;

public record QuoteCalculation(
        BigDecimal basePremium,
        BigDecimal ageFactor,
        BigDecimal experienceFactor,
        BigDecimal regionFactor,
        BigDecimal powerFactor,
        BigDecimal bonusMalusFactor,
        BigDecimal premium,
        String currency,
        String pricingVersion) {
}
