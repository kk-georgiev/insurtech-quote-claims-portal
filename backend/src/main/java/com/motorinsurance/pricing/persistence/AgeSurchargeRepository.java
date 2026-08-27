package com.motorinsurance.pricing.persistence;

import com.motorinsurance.pricing.domain.AgeSurcharge;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgeSurchargeRepository extends JpaRepository<AgeSurcharge, Long> {

    /** The age band containing {@code driverAge}; the top band (86+) has no upper bound. */
    @Query(
            """
            SELECT a FROM AgeSurcharge a
            WHERE a.minAge <= :driverAge
              AND (a.maxAge IS NULL OR a.maxAge >= :driverAge)
            """)
    Optional<AgeSurcharge> findApplicableSurcharge(@Param("driverAge") int driverAge);
}
