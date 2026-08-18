package bg.sirma.insurtech.motorinsurance.quote.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import bg.sirma.insurtech.motorinsurance.quote.application.QuoteView;
import bg.sirma.insurtech.motorinsurance.quote.domain.BonusMalusLevel;
import bg.sirma.insurtech.motorinsurance.quote.domain.QuoteStatus;
import bg.sirma.insurtech.motorinsurance.quote.domain.RegionRisk;

public record QuoteResponse(
        UUID id,
        QuoteStatus status,
        QuoteInputResponse input,
        PremiumBreakdownResponse breakdown,
        BigDecimal premium,
        String currency,
        String pricingVersion,
        Instant createdAt,
        Instant validUntil) {

    public static QuoteResponse from(QuoteView view) {
        var input = view.input();
        var calculation = view.calculation();
        return new QuoteResponse(
                view.id(),
                view.status(),
                new QuoteInputResponse(
                        input.driverAge(),
                        input.drivingExperienceYears(),
                        input.region(),
                        input.vehiclePowerKw(),
                        input.bonusMalusLevel()),
                new PremiumBreakdownResponse(
                        calculation.basePremium(),
                        calculation.ageFactor(),
                        calculation.experienceFactor(),
                        calculation.regionFactor(),
                        calculation.powerFactor(),
                        calculation.bonusMalusFactor()),
                calculation.premium(),
                calculation.currency(),
                calculation.pricingVersion(),
                view.createdAt(),
                view.validUntil());
    }

    public record QuoteInputResponse(
            int driverAge,
            int drivingExperienceYears,
            RegionRisk region,
            int vehiclePowerKw,
            BonusMalusLevel bonusMalusLevel) {
    }

    public record PremiumBreakdownResponse(
            BigDecimal basePremium,
            BigDecimal ageFactor,
            BigDecimal experienceFactor,
            BigDecimal regionFactor,
            BigDecimal powerFactor,
            BigDecimal bonusMalusFactor) {
    }
}
