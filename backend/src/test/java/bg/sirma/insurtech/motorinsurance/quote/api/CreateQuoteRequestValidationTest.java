package bg.sirma.insurtech.motorinsurance.quote.api;

import static org.assertj.core.api.Assertions.assertThat;

import bg.sirma.insurtech.motorinsurance.quote.domain.BonusMalusLevel;
import bg.sirma.insurtech.motorinsurance.quote.domain.RegionRisk;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class CreateQuoteRequestValidationTest {

    @Test
    void shouldReportInvalidApiFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new CreateQuoteRequest(
                    16,
                    -1,
                    RegionRisk.SOFIA,
                    900,
                    BonusMalusLevel.NEUTRAL);

            var violations = validator.validate(request);

            assertThat(violations)
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("driverAge", "drivingExperienceYears", "vehiclePowerKw");
        }
    }
}
