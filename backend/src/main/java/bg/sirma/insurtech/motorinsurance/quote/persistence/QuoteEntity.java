package bg.sirma.insurtech.motorinsurance.quote.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import bg.sirma.insurtech.motorinsurance.quote.application.QuoteView;
import bg.sirma.insurtech.motorinsurance.quote.domain.BonusMalusLevel;
import bg.sirma.insurtech.motorinsurance.quote.domain.QuoteCalculation;
import bg.sirma.insurtech.motorinsurance.quote.domain.QuoteInput;
import bg.sirma.insurtech.motorinsurance.quote.domain.QuoteStatus;
import bg.sirma.insurtech.motorinsurance.quote.domain.RegionRisk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quotes")
public class QuoteEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuoteStatus status;

    @Column(name = "driver_age", nullable = false)
    private int driverAge;

    @Column(name = "driving_experience_years", nullable = false)
    private int drivingExperienceYears;

    @Enumerated(EnumType.STRING)
    @Column(name = "region_risk", nullable = false, length = 30)
    private RegionRisk region;

    @Column(name = "vehicle_power_kw", nullable = false)
    private int vehiclePowerKw;

    @Enumerated(EnumType.STRING)
    @Column(name = "bonus_malus_level", nullable = false, length = 30)
    private BonusMalusLevel bonusMalusLevel;

    @Column(name = "base_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePremium;

    @Column(name = "age_factor", nullable = false, precision = 6, scale = 3)
    private BigDecimal ageFactor;

    @Column(name = "experience_factor", nullable = false, precision = 6, scale = 3)
    private BigDecimal experienceFactor;

    @Column(name = "region_factor", nullable = false, precision = 6, scale = 3)
    private BigDecimal regionFactor;

    @Column(name = "power_factor", nullable = false, precision = 6, scale = 3)
    private BigDecimal powerFactor;

    @Column(name = "bonus_malus_factor", nullable = false, precision = 6, scale = 3)
    private BigDecimal bonusMalusFactor;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal premium;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "pricing_version", nullable = false, length = 40)
    private String pricingVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    protected QuoteEntity() {
    }

    private QuoteEntity(
            UUID id,
            QuoteStatus status,
            QuoteInput input,
            QuoteCalculation calculation,
            Instant createdAt,
            Instant validUntil) {
        this.id = id;
        this.status = status;
        this.driverAge = input.driverAge();
        this.drivingExperienceYears = input.drivingExperienceYears();
        this.region = input.region();
        this.vehiclePowerKw = input.vehiclePowerKw();
        this.bonusMalusLevel = input.bonusMalusLevel();
        this.basePremium = calculation.basePremium();
        this.ageFactor = calculation.ageFactor();
        this.experienceFactor = calculation.experienceFactor();
        this.regionFactor = calculation.regionFactor();
        this.powerFactor = calculation.powerFactor();
        this.bonusMalusFactor = calculation.bonusMalusFactor();
        this.premium = calculation.premium();
        this.currency = calculation.currency();
        this.pricingVersion = calculation.pricingVersion();
        this.createdAt = createdAt;
        this.validUntil = validUntil;
    }

    public static QuoteEntity create(
            UUID id,
            QuoteInput input,
            QuoteCalculation calculation,
            Instant createdAt,
            Instant validUntil) {
        return new QuoteEntity(id, QuoteStatus.CREATED, input, calculation, createdAt, validUntil);
    }

    public QuoteView toView() {
        var input = new QuoteInput(
                driverAge,
                drivingExperienceYears,
                region,
                vehiclePowerKw,
                bonusMalusLevel);
        var calculation = new QuoteCalculation(
                basePremium,
                ageFactor,
                experienceFactor,
                regionFactor,
                powerFactor,
                bonusMalusFactor,
                premium,
                currency,
                pricingVersion);
        return new QuoteView(id, status, input, calculation, createdAt, validUntil);
    }
}
