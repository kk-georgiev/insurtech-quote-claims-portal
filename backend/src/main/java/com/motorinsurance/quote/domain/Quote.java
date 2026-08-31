package com.motorinsurance.quote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity backing {@code quotes} (mirrors {@code V4__create_quotes_table.sql}
 * exactly - {@code ddl-auto: validate} requires it). Per Architecture Spine
 * Consistency Conventions, entities are mutated only from an
 * {@code application}-layer service method (AD-2) - this class deliberately
 * exposes no setters.
 *
 * <p>Stores the resolved breakdown flat, not a reference into {@code pricing}'s
 * reference-data tables: a persisted quote must keep showing exactly what
 * the customer was quoted at calculation time, unaffected by any later
 * change to the tariff (Story 1.6 Design Notes).
 *
 * <p>Carries no {@code status} column (Architecture Spine AD-3, Story 6.2) -
 * {@link #status} derives it on every call from {@link #validUntil} and
 * {@link #acceptedAt}, which is the one place this milestone's status rule
 * is implemented. {@code acceptedAt} stays {@code null} through every story
 * before 8.1 (Accept a Quote and Issue a Policy) - no code path in this
 * milestone's Epic 6 sets it.
 */
@Entity
@Table(name = "quotes")
public class Quote {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** JPA-only. */
    protected Quote() {
    }

    public Quote(
            UUID customerId,
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
            int installments,
            BigDecimal installmentFee,
            BigDecimal totalPremium,
            BigDecimal installmentAmount,
            String currency,
            LocalDate validUntil) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
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
        this.installments = (short) installments;
        this.installmentFee = installmentFee;
        this.totalPremium = totalPremium;
        this.installmentAmount = installmentAmount;
        this.currency = currency;
        this.createdAt = Instant.now();
        this.validUntil = validUntil;
        // acceptedAt stays null - no constructor path in this milestone
        // accepts a quote at creation time (Story 8.1's job).
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    /**
     * Derives this quote's status as of {@code today} (Architecture Spine
     * AD-3, AD-6) - the one place this rule is implemented; every read path
     * calls this rather than re-deriving it. {@code today} is the caller's
     * responsibility to resolve from the injected {@code Clock} in the
     * business zone (never {@code LocalDate.now()} directly) - this method
     * itself stays a pure function of its arguments, which is what makes it
     * testable without a Spring context or a fixed clock bean.
     *
     * <p>The {@code validUntil} boundary is inclusive: a quote is still
     * {@link QuoteStatus#CALCULATED}, not {@link QuoteStatus#EXPIRED}, on
     * {@code validUntil} itself.
     */
    public QuoteStatus status(LocalDate today) {
        if (acceptedAt != null) {
            return QuoteStatus.ACCEPTED;
        }
        if (today.isAfter(validUntil)) {
            return QuoteStatus.EXPIRED;
        }
        return QuoteStatus.CALCULATED;
    }
}
