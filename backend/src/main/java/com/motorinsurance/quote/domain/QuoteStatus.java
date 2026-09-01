package com.motorinsurance.quote.domain;

/**
 * A quote's lifecycle state (Story 6.2, BA §7.1). Never stored - no
 * {@code status} column exists on {@code quotes} (Architecture Spine AD-3);
 * every value here is derived on read by {@link Quote#status}.
 *
 * <p>{@link #CANCELLED} is reserved: no operation in this milestone
 * produces it, and {@link Quote#status} has no branch that returns it. It
 * exists so the type is already correct for the milestone that adds a
 * cancellation operation, rather than that milestone widening this enum
 * and every switch/mapping over it.
 */
public enum QuoteStatus {
    /** Calculated and still within its offer-validity window; acceptable. */
    CALCULATED,
    /** {@code accepted_at} is set - a policy exists for this quote. */
    ACCEPTED,
    /** Past {@code valid_until} with no acceptance; can no longer be accepted. */
    EXPIRED,
    /** Reserved - no producer this milestone. */
    CANCELLED
}
