package com.motorinsurance.policy.domain;

/**
 * A policy's lifecycle state (Story 8.3, FR-M3-09, BA 7.2). Never stored -
 * no {@code status} column exists on {@code policies} (Architecture Spine
 * AD-3); every value here is derived on read by {@link Policy#status} from
 * the coverage dates, which is why no batch job is needed to keep a policy
 * honest as time passes.
 *
 * <p>{@link #CANCELLED} is reserved, exactly as {@code
 * quote.domain.QuoteStatus#CANCELLED} is: no operation this milestone
 * produces it, no persisted representation exists, and the derivation has
 * no branch that returns it. It exists so the type is already right for the
 * milestone that adds cancellation.
 */
public enum PolicyStatus {
    /** Issued, but its coverage period has not started yet. */
    SCHEDULED,
    /** Today falls within the coverage period, inclusive at both ends. */
    ACTIVE,
    /** The coverage period has run its full term - the successful outcome. */
    EXPIRED,
    /** Reserved - no producer this milestone. */
    CANCELLED
}
