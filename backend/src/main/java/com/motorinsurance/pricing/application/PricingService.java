package com.motorinsurance.pricing.application;

import com.motorinsurance.pricing.domain.AgeSurcharge;
import com.motorinsurance.pricing.domain.InstallmentPlan;
import com.motorinsurance.pricing.domain.RegionZoneMap;
import com.motorinsurance.pricing.domain.TariffRate;
import com.motorinsurance.pricing.domain.TariffZone;
import com.motorinsurance.pricing.persistence.AgeSurchargeRepository;
import com.motorinsurance.pricing.persistence.InstallmentPlanRepository;
import com.motorinsurance.pricing.persistence.RegionZoneMapRepository;
import com.motorinsurance.pricing.persistence.TariffRateRepository;
import com.motorinsurance.pricing.persistence.TariffZoneRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * {@code pricing}'s sole entry point (AD-2/AD-6) - the only class another
 * module may call into this one through. Calculates a GO motor-liability
 * premium from the zone/engine-cc tariff (PRD addendum, "Quote Engine -
 * Milestone 1 tariff"): driving experience and vehicle power play no part in
 * this model, unlike the superseded placeholder formula.
 *
 * <p>{@code driverAge}/{@code engineCc} are trusted to already satisfy their
 * structural bounds (18+, 800+) - {@code quote.api.CreateQuoteRequest}'s Bean
 * Validation enforces that before this service ever runs. What this service
 * validates is whether the given {@code regionCode}/{@code installments}
 * resolve to configured reference data at all - a lookup miss on either is a
 * real, expected user-input mistake (unknown plate prefix, unsupported
 * installment count), not a data-integrity bug, so each has its own
 * {@link com.motorinsurance.shared.api.ApiException} subtype with a
 * field-level error. A tariff-rate or age-surcharge miss, by contrast, would
 * mean the seed data itself has a gap for an otherwise-valid input - that
 * can't happen with the current seed data (every zone has all four cc bands;
 * the three age bands are contiguous from 18 with no upper bound), so it is
 * deliberately left to surface as the generic 500 rather than a dedicated
 * exception type for a case the input validation above already rules out.
 */
@Service
public class PricingService {

    private final RegionZoneMapRepository regionZoneMapRepository;
    private final TariffZoneRepository tariffZoneRepository;
    private final TariffRateRepository tariffRateRepository;
    private final AgeSurchargeRepository ageSurchargeRepository;
    private final InstallmentPlanRepository installmentPlanRepository;

    public PricingService(
            RegionZoneMapRepository regionZoneMapRepository,
            TariffZoneRepository tariffZoneRepository,
            TariffRateRepository tariffRateRepository,
            AgeSurchargeRepository ageSurchargeRepository,
            InstallmentPlanRepository installmentPlanRepository) {
        this.regionZoneMapRepository = regionZoneMapRepository;
        this.tariffZoneRepository = tariffZoneRepository;
        this.tariffRateRepository = tariffRateRepository;
        this.ageSurchargeRepository = ageSurchargeRepository;
        this.installmentPlanRepository = installmentPlanRepository;
    }

    public PricingResult calculate(int driverAge, String regionCode, int engineCc, int installments) {
        // Seed data (V3__create_pricing_tables.sql) stores every code
        // uppercase; normalizing here means a lowercase but otherwise-valid
        // plate prefix isn't wrongly rejected as unknown (review-loop
        // finding, Story 1.5) - same rationale as auth's email normalization
        // (auth.domain.Emails). Locale.ROOT for the same reason it does:
        // the normalized value is persisted (quotes.region_code) and must
        // not depend on the server's default locale.
        String normalizedRegionCode = regionCode.trim().toUpperCase(Locale.ROOT);
        RegionZoneMap zoneMap = regionZoneMapRepository
                .findById(normalizedRegionCode)
                .orElseThrow(() -> new UnknownRegionCodeException(normalizedRegionCode));
        short zoneId = zoneMap.getZoneId();

        TariffZone zone = tariffZoneRepository
                .findById(zoneId)
                .orElseThrow(() -> new IllegalStateException("No tariff_zone row configured for zone " + zoneId));

        TariffRate rate = tariffRateRepository
                .findApplicableRate(zoneId, engineCc)
                .orElseThrow(() -> new IllegalStateException(
                        "No tariff rate configured for zone " + zoneId + ", engineCc " + engineCc));

        AgeSurcharge ageSurcharge = ageSurchargeRepository
                .findApplicableSurcharge(driverAge)
                .orElseThrow(() -> new IllegalStateException("No age surcharge band configured for age " + driverAge));

        // Guards the narrowing cast just below: an int outside short range
        // (e.g. 65540) would otherwise silently alias to a valid plan id on
        // overflow (65540 -> (short) 4) instead of being rejected -
        // review-loop finding, Story 1.5. CreateQuoteRequest's @Min/@Max
        // already stops this from the HTTP path; this is this method's own
        // defense as pricing's sole entry point (AD-2), independent of caller.
        if (installments < 1 || installments > Short.MAX_VALUE) {
            throw new UnsupportedInstallmentCountException(installments);
        }
        InstallmentPlan plan = installmentPlanRepository
                .findById((short) installments)
                .orElseThrow(() -> new UnsupportedInstallmentCountException(installments));

        BigDecimal basePremium = rate.getBasePremium();
        BigDecimal oneTimePremium =
                basePremium.add(ageSurcharge.getSurcharge()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPremium = oneTimePremium.add(plan.getFee()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal installmentAmount =
                totalPremium.divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);

        return new PricingResult(
                zoneId,
                zone.getZoneName(),
                normalizedRegionCode,
                basePremium,
                ageSurcharge.getSurcharge(),
                oneTimePremium,
                installments,
                plan.getFee(),
                totalPremium,
                installmentAmount,
                rate.getCurrency());
    }
}
