package com.motorinsurance.policy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the policy-number format (FR-M3-06, BA 7.4, Architecture Spine
 * AD-7). A pure function, so no Spring context, no clock, and no database
 * is involved - the year and the sequence value are both the caller's to
 * resolve.
 */
class PolicyNumberTest {

    @Test
    void format_padsTheSequenceValueToEightDigits() {
        assertThat(PolicyNumber.format(2026, 1234)).isEqualTo("MI-2026-00001234");
    }

    @Test
    void format_firstEverPolicy_isStillEightDigitsWide() {
        assertThat(PolicyNumber.format(2026, 1)).isEqualTo("MI-2026-00000001");
    }

    @Test
    void format_carriesTheYearItIsGiven_notTheCurrentOne() {
        // The sequence is global and never resets per year (AD-7): the same
        // numeric value simply renders under whichever year it was issued
        // in, so numbers stay unique across years by construction.
        assertThat(PolicyNumber.format(2027, 42)).isEqualTo("MI-2027-00000042");
    }

    @Test
    void format_atTheEightDigitCeiling_stillRendersExactly() {
        assertThat(PolicyNumber.format(2026, 99_999_999L)).isEqualTo("MI-2026-99999999");
    }

    @Test
    void format_pastTheEightDigitCeiling_widensRatherThanWraps() {
        // Eight digits is a minimum width, not a modulus: growing wider
        // keeps numbers unique, whereas wrapping would collide with an
        // already-issued policy.
        assertThat(PolicyNumber.format(2026, 100_000_000L)).isEqualTo("MI-2026-100000000");
    }
}
