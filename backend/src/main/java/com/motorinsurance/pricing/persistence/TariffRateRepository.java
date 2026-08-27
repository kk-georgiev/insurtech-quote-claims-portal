package com.motorinsurance.pricing.persistence;

import com.motorinsurance.pricing.domain.TariffRate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TariffRateRepository extends JpaRepository<TariffRate, Long> {

    /** The zone's cc-band whose range contains {@code engineCc}; the top band per zone has no upper bound. */
    @Query(
            """
            SELECT t FROM TariffRate t
            WHERE t.zoneId = :zoneId
              AND t.engineCcMin <= :engineCc
              AND (t.engineCcMax IS NULL OR t.engineCcMax >= :engineCc)
            """)
    Optional<TariffRate> findApplicableRate(@Param("zoneId") short zoneId, @Param("engineCc") int engineCc);
}
