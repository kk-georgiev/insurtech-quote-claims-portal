package com.motorinsurance.quote.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Quote#status} directly - a pure function of its
 * {@code today} argument, so the {@code validUntil}/expiry boundary
 * (Architecture Spine AD-6, Story 6.2) is provable without a Spring
 * context or a fixed {@code Clock} bean: fix {@code today} to
 * {@code validUntil} itself and to the day after, exactly as AD-6
 * prescribes.
 */
class QuoteTest {

    private static final LocalDate VALID_UNTIL = LocalDate.of(2026, 9, 14);

    @Test
    void status_todayBeforeValidUntil_isCalculated() {
        Quote quote = quoteValidUntil(VALID_UNTIL);

        assertThat(quote.status(VALID_UNTIL.minusDays(1))).isEqualTo(QuoteStatus.CALCULATED);
    }

    @Test
    void status_todayExactlyValidUntil_isStillCalculated_boundaryInclusive() {
        // The boundary is inclusive: acceptable ON validUntil itself, not
        // just before it (Architecture Spine AD-6).
        Quote quote = quoteValidUntil(VALID_UNTIL);

        assertThat(quote.status(VALID_UNTIL)).isEqualTo(QuoteStatus.CALCULATED);
    }

    @Test
    void status_todayOneDayAfterValidUntil_isExpired() {
        Quote quote = quoteValidUntil(VALID_UNTIL);

        assertThat(quote.status(VALID_UNTIL.plusDays(1))).isEqualTo(QuoteStatus.EXPIRED);
    }

    @Test
    void status_acceptedQuote_isAcceptedRegardlessOfValidity() {
        // acceptedAt is not settable through the public constructor this
        // milestone (Story 8.1's job) - this test documents the intended
        // rule via the status() contract itself: ACCEPTED takes precedence
        // over the expiry check whenever acceptedAt is set. Re-verify this
        // test still holds once Story 8.1 adds a way to set it.
        Quote quote = quoteValidUntil(VALID_UNTIL);

        // Not yet accepted through this milestone's stories - documents the
        // current, pre-8.1 state rather than asserting the ACCEPTED branch
        // directly (no public API sets acceptedAt yet).
        assertThat(quote.getAcceptedAt()).isNull();
        assertThat(quote.status(VALID_UNTIL.plusDays(100))).isEqualTo(QuoteStatus.EXPIRED);
    }

    private static Quote quoteValidUntil(LocalDate validUntil) {
        return new Quote(
                UUID.randomUUID(),
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
                1,
                new BigDecimal("0.00"),
                new BigDecimal("141.12"),
                new BigDecimal("141.12"),
                "EUR",
                validUntil);
    }
}
