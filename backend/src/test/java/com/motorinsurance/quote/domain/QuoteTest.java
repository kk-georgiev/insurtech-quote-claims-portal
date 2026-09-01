package com.motorinsurance.quote.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
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
    private static final Instant ACCEPTED_AT = Instant.parse("2026-09-01T10:15:30Z");

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
        // Story 8.1 supplies what this test previously could only describe:
        // accept() is now the one way acceptedAt is ever set, so the
        // ACCEPTED branch is asserted directly rather than documented.
        Quote quote = quoteValidUntil(VALID_UNTIL);

        assertThat(quote.getAcceptedAt()).isNull();
        assertThat(quote.status(VALID_UNTIL.plusDays(100))).isEqualTo(QuoteStatus.EXPIRED);

        quote.accept(ACCEPTED_AT);

        // ACCEPTED takes precedence over the expiry check: a quote accepted
        // while it was still valid must not start reading EXPIRED once its
        // offer window closes - the policy it produced does not expire with
        // the offer.
        assertThat(quote.getAcceptedAt()).isEqualTo(ACCEPTED_AT);
        assertThat(quote.status(VALID_UNTIL.plusDays(100))).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(quote.status(VALID_UNTIL.minusDays(1))).isEqualTo(QuoteStatus.ACCEPTED);
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
