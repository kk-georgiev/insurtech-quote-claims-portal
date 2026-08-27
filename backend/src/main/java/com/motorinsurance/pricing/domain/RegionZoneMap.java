package com.motorinsurance.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity backing {@code region_zone_map} (mirrors
 * {@code V3__create_pricing_tables.sql} exactly - {@code ddl-auto: validate}
 * requires it). Maps a vehicle's plate-prefix region code to the pricing
 * zone it falls into; reference data only, never mutated at runtime.
 */
@Entity
@Table(name = "region_zone_map")
public class RegionZoneMap {

    @Id
    @Column(name = "region_code")
    private String regionCode;

    @Column(name = "zone_id", nullable = false)
    private short zoneId;

    /** JPA-only. */
    protected RegionZoneMap() {
    }

    public String getRegionCode() {
        return regionCode;
    }

    public short getZoneId() {
        return zoneId;
    }
}
