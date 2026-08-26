package com.motorinsurance.pricing.persistence;

import com.motorinsurance.pricing.domain.TariffZone;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@code findById(zoneId)} is the whole lookup - {@code zone_id} is the primary key. */
public interface TariffZoneRepository extends JpaRepository<TariffZone, Short> {
}
