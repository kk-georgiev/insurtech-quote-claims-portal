package com.motorinsurance.quote.persistence;

import com.motorinsurance.quote.domain.Quote;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    /**
     * Ownership-scoped lookup - {@code id} alone is never enough (Story 1.6
     * AC "never shown someone else's data"). A quote that exists but belongs
     * to a different customer is indistinguishable from a quote that doesn't
     * exist at all: both return empty here, and both become the same 404 in
     * {@code QuoteService} - not a 403, which would confirm the id is real.
     */
    Optional<Quote> findByIdAndCustomerId(UUID id, UUID customerId);
}
