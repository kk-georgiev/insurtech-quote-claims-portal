package com.motorinsurance.pricing.persistence;

import com.motorinsurance.pricing.domain.RegionZoneMap;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@code findById(regionCode)} is the whole lookup - {@code region_code} is the primary key. */
public interface RegionZoneMapRepository extends JpaRepository<RegionZoneMap, String> {
}
