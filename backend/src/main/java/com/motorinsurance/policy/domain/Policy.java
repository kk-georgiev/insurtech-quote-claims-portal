package com.motorinsurance.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity backing {@code policies} (mirrors {@code
 * V9__create_policies_table.sql} exactly - {@code ddl-auto: validate}
 * requires it). Like {@code quote.domain.Quote}, it exposes no setters: an
 * issued policy is immutable by design (Architecture Spine AD-4, M3), which
 * is the whole point of storing a snapshot rather than joining live data.
 *
 * <p>{@code quoteId} is a plain {@link UUID}, deliberately <strong>not</strong>
 * a JPA association to {@code Quote}: it exists solely as the idempotency
 * key behind {@code uq_policies_quote_id} (AD-5) and is never dereferenced
 * to render or recompute a policy. This class - and the whole {@code policy}
 * module - imports nothing from {@code quote} (AD-1); a policy row is
 * readable and complete with the {@code quotes} table empty.
 *
 * <p>Carries no {@code status} column (AD-3). Deriving a policy's status
 * from its coverage dates is FR-M3-09, which Story 8.3 adds here in the
 * domain layer; this story stores the facts that derivation will read.
 */
@Entity
@Table(name = "policies")
public class Policy {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(name = "vehicle_registration")
    private String vehicleRegistration;

    @Column(name = "vehicle_vin")
    private String vehicleVin;

    @Column(name = "coverage_start", nullable = false)
    private LocalDate coverageStart;

    @Column(name = "coverage_end", nullable = false)
    private LocalDate coverageEnd;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "driver_age", nullable = false)
    private int driverAge;

    @Column(name = "region_code", nullable = false)
    private String regionCode;

    @Column(name = "engine_cc", nullable = false)
    private int engineCc;

    @Column(name = "zone_id", nullable = false)
    private short zoneId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    @Column(name = "base_premium", nullable = false)
    private BigDecimal basePremium;

    @Column(name = "age_surcharge", nullable = false)
    private BigDecimal ageSurcharge;

    @Column(name = "bonus_malus_code", nullable = false)
    private String bonusMalusCode;

    @Column(name = "bonus_malus_factor", nullable = false)
    private BigDecimal bonusMalusFactor;

    @Column(name = "one_time_premium", nullable = false)
    private BigDecimal oneTimePremium;

    @Column(nullable = false)
    private short installments;

    @Column(name = "installment_fee", nullable = false)
    private BigDecimal installmentFee;

    @Column(name = "total_premium", nullable = false)
    private BigDecimal totalPremium;

    @Column(name = "installment_amount", nullable = false)
    private BigDecimal installmentAmount;

    @Column(nullable = false)
    private String currency;

    /** JPA-only. */
    protected Policy() {
    }

    public Policy(
            UUID customerId,
            UUID quoteId,
            String policyNumber,
            String holderName,
            String vehicleRegistration,
            String vehicleVin,
            LocalDate coverageStart,
            LocalDate coverageEnd,
            Instant issuedAt,
            int driverAge,
            String regionCode,
            int engineCc,
            short zoneId,
            String zoneName,
            BigDecimal basePremium,
            BigDecimal ageSurcharge,
            String bonusMalusCode,
            BigDecimal bonusMalusFactor,
            BigDecimal oneTimePremium,
            short installments,
            BigDecimal installmentFee,
            BigDecimal totalPremium,
            BigDecimal installmentAmount,
            String currency) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.quoteId = quoteId;
        this.policyNumber = policyNumber;
        this.holderName = holderName;
        this.vehicleRegistration = vehicleRegistration;
        this.vehicleVin = vehicleVin;
        this.coverageStart = coverageStart;
        this.coverageEnd = coverageEnd;
        this.issuedAt = issuedAt;
        this.driverAge = driverAge;
        this.regionCode = regionCode;
        this.engineCc = engineCc;
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.basePremium = basePremium;
        this.ageSurcharge = ageSurcharge;
        this.bonusMalusCode = bonusMalusCode;
        this.bonusMalusFactor = bonusMalusFactor;
        this.oneTimePremium = oneTimePremium;
        this.installments = installments;
        this.installmentFee = installmentFee;
        this.totalPremium = totalPremium;
        this.installmentAmount = installmentAmount;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getQuoteId() {
        return quoteId;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getVehicleRegistration() {
        return vehicleRegistration;
    }

    public String getVehicleVin() {
        return vehicleVin;
    }

    public LocalDate getCoverageStart() {
        return coverageStart;
    }

    public LocalDate getCoverageEnd() {
        return coverageEnd;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public int getDriverAge() {
        return driverAge;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public int getEngineCc() {
        return engineCc;
    }

    public short getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public BigDecimal getBasePremium() {
        return basePremium;
    }

    public BigDecimal getAgeSurcharge() {
        return ageSurcharge;
    }

    public String getBonusMalusCode() {
        return bonusMalusCode;
    }

    public BigDecimal getBonusMalusFactor() {
        return bonusMalusFactor;
    }

    public BigDecimal getOneTimePremium() {
        return oneTimePremium;
    }

    public short getInstallments() {
        return installments;
    }

    public BigDecimal getInstallmentFee() {
        return installmentFee;
    }

    public BigDecimal getTotalPremium() {
        return totalPremium;
    }

    public BigDecimal getInstallmentAmount() {
        return installmentAmount;
    }

    public String getCurrency() {
        return currency;
    }
}
