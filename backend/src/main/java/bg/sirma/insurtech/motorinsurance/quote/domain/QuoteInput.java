package bg.sirma.insurtech.motorinsurance.quote.domain;

public record QuoteInput(
        int driverAge,
        int drivingExperienceYears,
        RegionRisk region,
        int vehiclePowerKw,
        BonusMalusLevel bonusMalusLevel) {
}
