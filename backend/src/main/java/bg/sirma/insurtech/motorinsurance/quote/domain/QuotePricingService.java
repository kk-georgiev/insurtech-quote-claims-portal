package bg.sirma.insurtech.motorinsurance.quote.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

@Service
public class QuotePricingService {

    static final String PRICING_VERSION = "2026.1-demo";
    static final String CURRENCY = "EUR";

    private static final BigDecimal BASE_PREMIUM = money("180.00");
    private static final BigDecimal MINIMUM_PREMIUM = money("120.00");
    private static final BigDecimal MAXIMUM_PREMIUM = money("1500.00");

    public QuoteCalculation calculate(QuoteInput input) {
        var ageFactor = ageFactor(input.driverAge());
        var experienceFactor = experienceFactor(input.drivingExperienceYears());
        var regionFactor = regionFactor(input.region());
        var powerFactor = powerFactor(input.vehiclePowerKw());
        var bonusMalusFactor = bonusMalusFactor(input.bonusMalusLevel());

        var rawPremium = BASE_PREMIUM
                .multiply(ageFactor)
                .multiply(experienceFactor)
                .multiply(regionFactor)
                .multiply(powerFactor)
                .multiply(bonusMalusFactor);

        var premium = rawPremium.max(MINIMUM_PREMIUM).min(MAXIMUM_PREMIUM)
                .setScale(2, RoundingMode.HALF_UP);

        return new QuoteCalculation(
                BASE_PREMIUM,
                ageFactor,
                experienceFactor,
                regionFactor,
                powerFactor,
                bonusMalusFactor,
                premium,
                CURRENCY,
                PRICING_VERSION);
    }

    private BigDecimal ageFactor(int driverAge) {
        if (driverAge < 25) return factor("1.350");
        if (driverAge < 30) return factor("1.150");
        if (driverAge >= 70) return factor("1.250");
        return factor("1.000");
    }

    private BigDecimal experienceFactor(int experienceYears) {
        if (experienceYears < 2) return factor("1.300");
        if (experienceYears < 5) return factor("1.100");
        return factor("1.000");
    }

    private BigDecimal regionFactor(RegionRisk region) {
        return switch (region) {
            case SOFIA -> factor("1.200");
            case LARGE_CITY -> factor("1.100");
            case OTHER -> factor("1.000");
        };
    }

    private BigDecimal powerFactor(int vehiclePowerKw) {
        if (vehiclePowerKw <= 74) return factor("0.900");
        if (vehiclePowerKw <= 110) return factor("1.000");
        if (vehiclePowerKw <= 150) return factor("1.150");
        return factor("1.350");
    }

    private BigDecimal bonusMalusFactor(BonusMalusLevel level) {
        return switch (level) {
            case BONUS_20 -> factor("0.800");
            case BONUS_10 -> factor("0.900");
            case NEUTRAL -> factor("1.000");
            case MALUS_25 -> factor("1.250");
            case MALUS_50 -> factor("1.500");
        };
    }

    private static BigDecimal factor(String value) {
        return new BigDecimal(value).setScale(3, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
    }
}
