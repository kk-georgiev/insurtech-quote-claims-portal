package bg.sirma.insurtech.motorinsurance.quote.application;

import bg.sirma.insurtech.motorinsurance.quote.domain.BonusMalusLevel;
import bg.sirma.insurtech.motorinsurance.quote.domain.RegionRisk;

public record CreateQuoteCommand(
        int driverAge,
        int drivingExperienceYears,
        RegionRisk region,
        int vehiclePowerKw,
        BonusMalusLevel bonusMalusLevel) {
}
