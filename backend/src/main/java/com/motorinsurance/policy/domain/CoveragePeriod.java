package com.motorinsurance.policy.domain;

import java.time.LocalDate;

/**
 * How long a policy covers, given when it starts (FR-M3-04, Architecture
 * Spine AD-6). A pure function of its arguments: the number of months is
 * configuration the caller resolves, and no clock is involved - the start
 * date has already been chosen and validated by then.
 *
 * <p>Lives in {@code policy.domain} rather than inline in the service for
 * the same reason {@code Quote#status} does: it is a domain rule, and
 * keeping it here is what makes the month-end behaviour below provable
 * without a Spring context or a database. Story 8.2 moved it here when the
 * 90-day coverage-start horizon made the previous end-to-end test of this
 * arithmetic impossible to express (a leap-day start is never within 90
 * days of an arbitrary "today").
 */
public final class CoveragePeriod {

    private CoveragePeriod() {
    }

    /**
     * The last day of cover, inclusive - so a twelve-month policy starting
     * on the 1st ends on the last day of the twelfth month, never on the
     * anniversary itself (AD-6).
     *
     * <p><strong>Month-end starts clamp before the subtraction.</strong>
     * {@code plusMonths} lands on the nearest valid day-of-month, so cover
     * starting 29 February 2028 runs to 27 February 2029 - one day short of
     * a full year. That is the arithmetic the architecture prescribes,
     * pinned by {@code CoveragePeriodTest} so it stays a decided rule
     * rather than a surprise discovered on a leap day.
     */
    public static LocalDate endFor(LocalDate coverageStart, int months) {
        return coverageStart.plusMonths(months).minusDays(1);
    }
}
