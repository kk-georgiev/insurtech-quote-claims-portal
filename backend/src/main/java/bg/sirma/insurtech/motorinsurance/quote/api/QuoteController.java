package bg.sirma.insurtech.motorinsurance.quote.api;

import java.net.URI;
import java.util.UUID;

import bg.sirma.insurtech.motorinsurance.quote.application.QuoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> create(@Valid @RequestBody CreateQuoteRequest request) {
        var quote = QuoteResponse.from(quoteService.create(request.toCommand()));
        return ResponseEntity
                .created(URI.create("/api/v1/quotes/" + quote.id()))
                .body(quote);
    }

    @GetMapping("/{quoteId}")
    public QuoteResponse get(@PathVariable UUID quoteId) {
        return QuoteResponse.from(quoteService.get(quoteId));
    }
}
