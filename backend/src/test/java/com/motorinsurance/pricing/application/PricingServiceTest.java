package com.motorinsurance.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises {@link PricingService#calculate} against a real Postgres
 * (Testcontainers, real Flyway migrations - business analysis §16.4) rather
 * than H2, so the range queries, {@code NUMERIC} precision, and CHECK
 * constraints in {@code V3__create_pricing_tables.sql} are all tested as
 * they actually run in production, not against an H2-compatibility
 * approximation of them.
 *
 * <p>The "known inputs" case is the worked example from the PRD addendum
 * ("Quote Engine - Milestone 1 tariff"): driver age 20 (18-24 surcharge
 * band), region {@code KH} (Zone 1), 1500cc (1301-2100 band), 2 installments
 * - every intermediate figure is asserted, not just the total, matching
 * Story 1.5's "transparent breakdown" requirement.
 */
@SpringBootTest
@Testcontainers
class PricingServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private PricingService pricingService;

    @Test
    void calculate_knownInputs_returnsExactExpectedBreakdown() {
        PricingResult result = pricingService.calculate(20, "KH", 1500, 2, "NEUTRAL");

        assertThat(result.zoneId()).isEqualTo((short) 1);
        assertThat(result.zoneName()).isEqualTo("Zone 1");
        assertThat(result.basePremium()).isEqualByComparingTo("141.12");
        assertThat(result.ageSurcharge()).isEqualByComparingTo("36.00");
        assertThat(result.bonusMalusClass()).isEqualTo("NEUTRAL");
        assertThat(result.bonusMalusFactor()).isEqualByComparingTo("1.000");
        assertThat(result.oneTimePremium()).isEqualByComparingTo("177.12");
        assertThat(result.installments()).isEqualTo(2);
        assertThat(result.installmentFee()).isEqualByComparingTo("2.00");
        assertThat(result.totalPremium()).isEqualByComparingTo("179.12");
        assertThat(result.installmentAmount()).isEqualByComparingTo("89.56");
        assertThat(result.currency()).isEqualTo("EUR");
    }

    @Test
    void calculate_oneInstallment_totalEqualsOneTimePremiumWithNoFee() {
        PricingResult result = pricingService.calculate(30, "KH", 900, 1, "NEUTRAL");

        assertThat(result.installmentFee()).isEqualByComparingTo("0.00");
        assertThat(result.oneTimePremium()).isEqualByComparingTo("131.91");
        assertThat(result.totalPremium()).isEqualByComparingTo("131.91");
        assertThat(result.installmentAmount()).isEqualByComparingTo("131.91");
    }

    @Test
    void calculate_fourInstallments_returnsExactExpectedBreakdown() {
        PricingResult result = pricingService.calculate(30, "KH", 900, 4, "NEUTRAL");

        assertThat(result.installmentFee()).isEqualByComparingTo("4.00");
        assertThat(result.totalPremium()).isEqualByComparingTo("135.91");
        assertThat(result.installmentAmount()).isEqualByComparingTo("33.98");
    }

    @Test
    void calculate_regionCodeLowercase_isNormalizedAndStillResolves() {
        PricingResult lowercase = pricingService.calculate(20, "kh", 1500, 2, "NEUTRAL");
        PricingResult uppercase = pricingService.calculate(20, "KH", 1500, 2, "NEUTRAL");

        assertThat(lowercase.totalPremium()).isEqualByComparingTo(uppercase.totalPremium());
    }

    @Test
    void calculate_ageInBaselineTwentyFiveToEightyFiveBand_noSurcharge() {
        PricingResult result = pricingService.calculate(40, "B", 900, 1, "NEUTRAL");

        assertThat(result.ageSurcharge()).isEqualByComparingTo("0.00");
    }

    @Test
    void calculate_ageExactlyEightyFive_isStillBaselineNotSurcharged() {
        // Addendum.md's explicit boundary assumption: the 25-85 band includes
        // 85 itself at no surcharge; +10 starts at 86, not 85.
        PricingResult result = pricingService.calculate(85, "B", 900, 1, "NEUTRAL");

        assertThat(result.ageSurcharge()).isEqualByComparingTo("0.00");
    }

    @Test
    void calculate_ageEightySix_appliesOverEightyFiveSurcharge() {
        PricingResult result = pricingService.calculate(86, "B", 900, 1, "NEUTRAL");

        assertThat(result.ageSurcharge()).isEqualByComparingTo("10.00");
    }

    @Test
    void calculate_engineCcInOpenEndedTopBand_resolvesToUnboundedRate() {
        PricingResult result = pricingService.calculate(30, "B", 5000, 1, "NEUTRAL");

        assertThat(result.basePremium()).isEqualByComparingTo("169.24");
    }

    @Test
    void calculate_unknownRegionCode_throwsWithFieldError() {
        assertThatThrownBy(() -> pricingService.calculate(30, "ZZ", 1000, 1, "NEUTRAL"))
                .isInstanceOf(UnknownRegionCodeException.class);
    }

    @Test
    void calculate_militaryCodeBA_isNotSeededAsARegionAndIsRejected() {
        // BA is deliberately excluded from region_zone_map (it's Bulgaria's
        // military-vehicle plate code, not a civilian Sofia sub-code - see
        // addendum.md) - asserts the omission actually took effect.
        assertThatThrownBy(() -> pricingService.calculate(30, "BA", 1000, 1, "NEUTRAL"))
                .isInstanceOf(UnknownRegionCodeException.class);
    }

    @Test
    void calculate_unsupportedInstallmentCount_throws() {
        assertThatThrownBy(() -> pricingService.calculate(30, "B", 1000, 3, "NEUTRAL"))
                .isInstanceOf(UnsupportedInstallmentCountException.class);
    }

    @Test
    void calculate_installmentsOverflowsShortRange_isRejectedNotAliased() {
        // 65540 narrows via (short) cast to 4 (65540 mod 65536), which IS a
        // seeded plan - without the explicit range guard in PricingService,
        // this would silently succeed with a nonsensical installmentAmount
        // instead of being rejected (review-loop finding, Story 1.5).
        // CreateQuoteRequest's @Max(4) stops this before it reaches here from
        // HTTP; this proves PricingService itself refuses it too, as pricing's
        // sole entry point (AD-2), regardless of caller.
        assertThatThrownBy(() -> pricingService.calculate(30, "KH", 1500, 65540, "NEUTRAL"))
                .isInstanceOf(UnsupportedInstallmentCountException.class);
    }

    @Test
    void calculate_negativeInstallments_isRejected() {
        assertThatThrownBy(() -> pricingService.calculate(30, "KH", 1500, -1, "NEUTRAL"))
                .isInstanceOf(UnsupportedInstallmentCountException.class);
    }

    // --- Story 6.1: bonus-malus rating factor ---

    @Test
    void calculate_bonusClass_appliesFactorBeforeInstallmentFeeIsAdded() {
        // Same KH/1500cc/age 20 inputs as the known-inputs case:
        // (141.12 + 36.00) x 0.800 = 141.696 -> HALF_UP 141.70; the fee
        // (2.00 for 2 installments) is added AFTER, untouched by the factor
        // (Architecture Spine AD-8).
        PricingResult result = pricingService.calculate(20, "KH", 1500, 2, "BONUS_20");

        assertThat(result.bonusMalusClass()).isEqualTo("BONUS_20");
        assertThat(result.bonusMalusFactor()).isEqualByComparingTo("0.800");
        assertThat(result.oneTimePremium()).isEqualByComparingTo("141.70");
        assertThat(result.installmentFee()).isEqualByComparingTo("2.00");
        assertThat(result.totalPremium()).isEqualByComparingTo("143.70");
        assertThat(result.installmentAmount()).isEqualByComparingTo("71.85");
    }

    @Test
    void calculate_malusClass_increasesOneTimePremium() {
        // (141.12 + 36.00) x 1.500 = 265.68.
        PricingResult result = pricingService.calculate(20, "KH", 1500, 1, "MALUS_50");

        assertThat(result.bonusMalusFactor()).isEqualByComparingTo("1.500");
        assertThat(result.oneTimePremium()).isEqualByComparingTo("265.68");
        assertThat(result.totalPremium()).isEqualByComparingTo("265.68");
    }

    @Test
    void calculate_bonusMalusClassLowercase_isNormalizedAndStillResolves() {
        PricingResult lowercase = pricingService.calculate(20, "KH", 1500, 2, "bonus_20");
        PricingResult uppercase = pricingService.calculate(20, "KH", 1500, 2, "BONUS_20");

        assertThat(lowercase.bonusMalusClass()).isEqualTo("BONUS_20");
        assertThat(lowercase.totalPremium()).isEqualByComparingTo(uppercase.totalPremium());
    }

    @Test
    void calculate_unknownBonusMalusClass_throwsWithFieldError() {
        // Fails closed - never silently defaulted to NEUTRAL (Architecture
        // Spine AD-8).
        assertThatThrownBy(() -> pricingService.calculate(30, "KH", 1000, 1, "NOT_A_CLASS"))
                .isInstanceOf(UnknownBonusMalusClassException.class);
    }
}
