package com.motorinsurance.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA entity backing {@code tariff_rate} (mirrors
 * {@code V3__create_pricing_tables.sql} exactly - {@code ddl-auto: validate}
 * requires it). One row = the base premium for a zone x engine-cc band;
 * {@code engineCcMax} is {@code null} for the open-ended top band ("2501 and
 * above"). Reference data only, never mutated at runtime.
 */
@Entity
@Table(name = "tariff_rate")
public class TariffRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false)
    private short zoneId;

    @Column(name = "engine_cc_min", nullable = false)
    private int engineCcMin;

    @Column(name = "engine_cc_max")
    private Integer engineCcMax;

    @Column(name = "base_premium", nullable = false)
    private BigDecimal basePremium;

    @Column(nullable = false)
    private String currency;

    /** JPA-only. */
    protected TariffRate() {
    }

    public Long getId() {
        return id;
    }

    public short getZoneId() {
        return zoneId;
    }

    public int getEngineCcMin() {
        return engineCcMin;
    }

    public Integer getEngineCcMax() {
        return engineCcMax;
    }

    public BigDecimal getBasePremium() {
        return basePremium;
    }

    public String getCurrency() {
        return currency;
    }
}
