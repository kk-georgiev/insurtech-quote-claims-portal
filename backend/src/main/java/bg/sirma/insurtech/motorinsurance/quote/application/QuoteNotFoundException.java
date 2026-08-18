package bg.sirma.insurtech.motorinsurance.quote.application;

import java.util.UUID;

public class QuoteNotFoundException extends RuntimeException {

    public QuoteNotFoundException(UUID quoteId) {
        super("Quote " + quoteId + " was not found");
    }
}
