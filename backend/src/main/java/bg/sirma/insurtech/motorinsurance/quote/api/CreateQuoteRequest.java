package bg.sirma.insurtech.motorinsurance.quote.api;

import bg.sirma.insurtech.motorinsurance.quote.application.CreateQuoteCommand;
import bg.sirma.insurtech.motorinsurance.quote.domain.BonusMalusLevel;
import bg.sirma.insurtech.motorinsurance.quote.domain.RegionRisk;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateQuoteRequest(
        @Min(value = 18, message = "Driver age must be at least 18")
        @Max(value = 100, message = "Driver age must be at most 100")
        int driverAge,

        @Min(value = 0, message = "Driving experience cannot be negative")
        @Max(value = 82, message = "Driving experience must be at most 82 years")
        int drivingExperienceYears,

        @NotNull(message = "Region is required")
        RegionRisk region,

        @Min(value = 20, message = "Vehicle power must be at least 20 kW")
        @Max(value = 500, message = "Vehicle power must be at most 500 kW")
        int vehiclePowerKw,

        @NotNull(message = "Bonus-malus level is required")
        BonusMalusLevel bonusMalusLevel) {

    public CreateQuoteCommand toCommand() {
        return new CreateQuoteCommand(
                driverAge,
                drivingExperienceYears,
                region,
                vehiclePowerKw,
                bonusMalusLevel);
    }
}
