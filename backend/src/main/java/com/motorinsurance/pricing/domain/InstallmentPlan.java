package com.motorinsurance.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA entity backing {@code installment_plan} (mirrors
 * {@code V3__create_pricing_tables.sql} exactly - {@code ddl-auto: validate}
 * requires it). The set of rows present here <em>is</em> the set of
 * supported installment counts - a lookup miss means "unsupported", not
 * "not configured yet" (see {@code PricingService}).
 */
@Entity
@Table(name = "installment_plan")
public class InstallmentPlan {

    @Id
    private short installments;

    @Column(nullable = false)
    private BigDecimal fee;

    /** JPA-only. */
    protected InstallmentPlan() {
    }

    public short getInstallments() {
        return installments;
    }

    public BigDecimal getFee() {
        return fee;
    }
}
