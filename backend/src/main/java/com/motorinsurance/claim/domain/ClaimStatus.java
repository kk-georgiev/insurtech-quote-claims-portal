package com.motorinsurance.claim.domain;

/**
 * A claim's lifecycle state (BA §8.3, FR-M4-09). Unlike {@code
 * policy.domain.PolicyStatus}, this is <strong>stored</strong>, not derived
 * (M4-AD-1, the one deliberate departure from M3 AD-3): a claim's status
 * records a human decision and cannot be recomputed from persisted dates.
 *
 * <p>This story ({@code claim.application.ClaimSubmissionService}) only ever
 * writes {@link #SUBMITTED}. The legal transition table ({@code
 * canTransitionTo}, Story 11.1) and the four business operations that move a
 * claim through the rest of these states belong to that story, not here -
 * this enum exists now only because {@code claims.status} needs a concrete,
 * fully-named type from its first migration.
 */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    PAID
}
