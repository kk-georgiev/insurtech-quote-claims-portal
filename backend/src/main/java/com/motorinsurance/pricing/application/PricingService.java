package com.motorinsurance.pricing.application;

import com.motorinsurance.pricing.domain.AgeSurcharge;
import com.motorinsurance.pricing.domain.InstallmentPlan;
import com.motorinsurance.pricing.domain.RegionZoneMap;
import com.motorinsurance.pricing.domain.TariffRate;
import com.motorinsurance.pricing.persistence.AgeSurchargeRepository;
import com.motorinsurance.pricing.persistence.InstallmentPlanRepository;
import com.motorinsurance.pricing.persistence.RegionZoneMapRepository;
import com.motorinsurance.pricing.persistence.TariffRateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final TariffRateRepository tariffRateRepository;
    private final AgeSurchargeRepository ageSurchargeRepository;
    private final InstallmentPlanRepository installmentPlanRepository;

    public PricingService(
            RegionZoneMapRepository regionZoneMapRepository,
            TariffRateRepository tariffRateRepository,
            AgeSurchargeRepository ageSurchargeRepository,
            InstallmentPlanRepository installmentPlanRepository) {
        this.regionZoneMapRepository = regionZoneMapRepository;
        this.tariffRateRepository = tariffRateRepository;
        this.ageSurchargeRepository = ageSurchargeRepository;
        this.installmentPlanRepository = installmentPlanRepository;
    }

    public PricingResult calculate(int driverAge, String regionCode, int engineCc, int installments) {
        short zoneId = regionZoneMapRepository
                .findById(regionCode)
                .map(RegionZoneMap::getZoneId)
                .orElseThrow(() -> new UnknownRegionCodeException(regionCode));

        TariffRate rate = tariffRateRepository
                .findApplicableRate(zoneId, engineCc)
                .orElseThrow(() -> new IllegalStateException(
                        "No tariff rate configured for zone " + zoneId + ", engineCc " + engineCc));

        AgeSurcharge ageSurcharge = ageSurchargeRepository
                .findApplicableSurcharge(driverAge)
                .orElseThrow(() -> new IllegalStateException("No age surcharge band configured for age " + driverAge));

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
