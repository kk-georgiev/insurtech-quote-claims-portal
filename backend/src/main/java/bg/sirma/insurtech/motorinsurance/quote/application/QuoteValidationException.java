package bg.sirma.insurtech.motorinsurance.quote.application;

public class QuoteValidationException extends RuntimeException {

    public QuoteValidationException(String message) {
        super(message);
    }
}
