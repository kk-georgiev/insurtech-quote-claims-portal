package com.motorinsurance.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity backing {@code tariff_zone} (mirrors
 * {@code V3__create_pricing_tables.sql} exactly - {@code ddl-auto: validate}
 * requires it). Reference data only, never mutated at runtime - the
 * human-readable name shown alongside {@code zoneId} in a quote's breakdown
 * (review-loop finding, Story 1.5: a bare zone number undercut the story's
 * own "transparent breakdown" goal).
 */
@Entity
@Table(name = "tariff_zone")
public class TariffZone {

    @Id
    @Column(name = "zone_id")
    private short zoneId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    /** JPA-only. */
    protected TariffZone() {
    }

    public short getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }
}
