package com.motorinsurance.policy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Pins the coverage-period arithmetic (FR-M3-04, Architecture Spine AD-6).
 *
 * <p>A unit test rather than an HTTP one since Story 8.2: the acceptance
 * endpoint now refuses a coverage start more than 90 days ahead, so the
 * month-end cases below - which need specific calendar dates, years out -
 * can no longer be reached through the API at all. Testing the rule where
 * it lives keeps it provable and makes the boundary explicit.
 */
class CoveragePeriodTest {

    @Test
    void endFor_ordinaryStart_isTheDayBeforeTheAnniversary() {
        // Inclusive at both ends: a year of cover from 1 March runs through
        // the end of February, not into 1 March again.
        assertThat(CoveragePeriod.endFor(LocalDate.of(2026, 3, 1), 12)).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void endFor_leapDayStart_clampsBeforeSubtracting() {
        // 29 Feb 2028 + 12 months clamps to 28 Feb 2029, so the inclusive
        // end is the 27th - 364 days, one short of a full year. This is what
        // the architecture's formula produces; recorded as a decided rule
        // rather than discovered by a client on a leap day.
        assertThat(CoveragePeriod.endFor(LocalDate.of(2028, 2, 29), 12)).isEqualTo(LocalDate.of(2029, 2, 27));
    }

    @Test
    void endFor_monthEndStart_keepsTheSameDayOfMonthWhereItExists() {
        // 31 January is valid in both years, so no clamping happens and the
        // period is a full year minus its inclusive last day.
        assertThat(CoveragePeriod.endFor(LocalDate.of(2027, 1, 31), 12)).isEqualTo(LocalDate.of(2028, 1, 30));
    }

    @Test
    void endFor_startingOnALeapYearsFirstDay_spansTheExtraDay() {
        assertThat(CoveragePeriod.endFor(LocalDate.of(2028, 1, 1), 12)).isEqualTo(LocalDate.of(2028, 12, 31));
    }

    @Test
    void endFor_singleMonthPeriod_isSupported() {
        // The length is configuration, not a constant: nothing in the rule
        // assumes twelve.
        assertThat(CoveragePeriod.endFor(LocalDate.of(2026, 9, 1), 1)).isEqualTo(LocalDate.of(2026, 9, 30));
    }
}
