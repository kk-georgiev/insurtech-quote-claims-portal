package bg.sirma.insurtech.motorinsurance.quote.application;

import java.time.Instant;
import java.util.UUID;

import bg.sirma.insurtech.motorinsurance.quote.domain.QuoteCalculation;
import bg.sirma.insurtech.motorinsurance.quote.domain.QuoteInput;
import bg.sirma.insurtech.motorinsurance.quote.domain.QuoteStatus;

public record QuoteView(
        UUID id,
        QuoteStatus status,
        QuoteInput input,
        QuoteCalculation calculation,
        Instant createdAt,
        Instant validUntil) {
}
