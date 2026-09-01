package com.motorinsurance.policy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Policy#status} directly (Story 8.3, FR-M3-09) - a pure
 * function of its {@code today} argument, so both coverage boundaries are
 * provable without a Spring context or a fixed {@code Clock} bean, exactly
 * as {@code quote.domain.QuoteTest} does for the quote's own rule.
 */
class PolicyStatusTest {

    private static final LocalDate COVERAGE_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate COVERAGE_END = LocalDate.of(2027, 8, 31);

    @Test
    void status_beforeCoverageStarts_isScheduled() {
        assertThat(policy().status(COVERAGE_START.minusDays(1))).isEqualTo(PolicyStatus.SCHEDULED);
    }

    @Test
    void status_onTheFirstDayOfCover_isActive_boundaryInclusive() {
        assertThat(policy().status(COVERAGE_START)).isEqualTo(PolicyStatus.ACTIVE);
    }

    @Test
    void status_midTerm_isActive() {
        assertThat(policy().status(LocalDate.of(2027, 1, 15))).isEqualTo(PolicyStatus.ACTIVE);
    }

    @Test
    void status_onTheLastDayOfCover_isStillActive_boundaryInclusive() {
        // The whole point of coverage_end being inclusive: a client is
        // covered for an incident on the final day of their policy.
        assertThat(policy().status(COVERAGE_END)).isEqualTo(PolicyStatus.ACTIVE);
    }

    @Test
    void status_theDayAfterCoverEnds_isExpired() {
        assertThat(policy().status(COVERAGE_END.plusDays(1))).isEqualTo(PolicyStatus.EXPIRED);
    }

    @Test
    void status_neverReturnsTheReservedCancelledValue() {
        // CANCELLED exists in the type but has no producer this milestone
        // (AD-3) - no date can derive it.
        for (LocalDate day : new LocalDate[] {
            COVERAGE_START.minusYears(5), COVERAGE_START, COVERAGE_END, COVERAGE_END.plusYears(5)
        }) {
            assertThat(policy().status(day)).isNotEqualTo(PolicyStatus.CANCELLED);
        }
    }

    private static Policy policy() {
        return new Policy(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "MI-2026-00000001",
                "Ivan Petrov",
                "CA1234BM",
                null,
                COVERAGE_START,
                COVERAGE_END,
                Instant.parse("2026-09-01T08:00:00Z"),
                30,
                "KH",
                1500,
                (short) 1,
                "Zone 1",
                new BigDecimal("141.12"),
                new BigDecimal("0.00"),
                "NEUTRAL",
                new BigDecimal("1.000"),
                new BigDecimal("141.12"),
                (short) 1,
                new BigDecimal("0.00"),
                new BigDecimal("141.12"),
                new BigDecimal("141.12"),
                "EUR");
    }
}
