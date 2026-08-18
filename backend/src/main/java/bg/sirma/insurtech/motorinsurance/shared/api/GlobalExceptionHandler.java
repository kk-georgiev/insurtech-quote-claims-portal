package bg.sirma.insurtech.motorinsurance.shared.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import bg.sirma.insurtech.motorinsurance.quote.application.QuoteNotFoundException;
import bg.sirma.insurtech.motorinsurance.quote.application.QuoteValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(QuoteValidationException.class)
    public ResponseEntity<ApiError> handleQuoteValidation(QuoteValidationException exception) {
        return error(HttpStatus.BAD_REQUEST, "QUOTE_VALIDATION_ERROR", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest() {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                "Request body contains a missing or unsupported value",
                Map.of());
    }

    @ExceptionHandler(QuoteNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(QuoteNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "QUOTE_NOT_FOUND", exception.getMessage(), Map.of());
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                code,
                message,
                fieldErrors));
    }
}
