package com.motorinsurance.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA entity backing {@code bonus_malus_class} (mirrors
 * {@code V6__create_bonus_malus_class_table.sql} exactly - {@code
 * ddl-auto: validate} requires it). One row = one bonus-malus class and the
 * multiplicative factor it applies to {@code base_premium + age_surcharge}
 * (Story 6.1, Architecture Spine AD-8).
 *
 * <p><strong>Provenance (binding, product owner, 2026-08-31):</strong> the
 * five seeded rows are this project's own demo model, inherited from the
 * team's prototype - not official or regulatorily determined Bulgarian
 * market values. See the seed migration's header for the full statement;
 * this class does not repeat it per-instance, only records that the
 * constraint exists.
 */
@Entity
@Table(name = "bonus_malus_class")
public class BonusMalusClass {

    @Id
    private String code;

    @Column(nullable = false)
    private BigDecimal factor;

    /** JPA-only. */
    protected BonusMalusClass() {
    }

    public String getCode() {
        return code;
    }

    public BigDecimal getFactor() {
        return factor;
    }
}
