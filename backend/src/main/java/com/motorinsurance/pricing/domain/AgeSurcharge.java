package com.motorinsurance.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA entity backing {@code age_surcharge} (mirrors
 * {@code V3__create_pricing_tables.sql} exactly - {@code ddl-auto: validate}
 * requires it). One row = the surcharge added to the base premium for a
 * driver-age band; {@code maxAge} is {@code null} for the open-ended top
 * band (86+). The 25-85 band is a real row with a {@code 0.00} surcharge,
 * not the absence of one - driving experience plays no part in this model.
 */
@Entity
@Table(name = "age_surcharge")
public class AgeSurcharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "min_age", nullable = false)
    private int minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(nullable = false)
    private BigDecimal surcharge;

    /** JPA-only. */
    protected AgeSurcharge() {
    }

    public Long getId() {
        return id;
    }

    public int getMinAge() {
        return minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public BigDecimal getSurcharge() {
        return surcharge;
    }
}
