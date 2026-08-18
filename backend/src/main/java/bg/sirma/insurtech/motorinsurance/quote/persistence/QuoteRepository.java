package bg.sirma.insurtech.motorinsurance.quote.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<QuoteEntity, UUID> {
}
