package com.motorinsurance.claim.domain;

/**
 * The human-readable claim number's format (FR-M4-07, M4-AD-8): {@code
 * CL-{year}-{8 digits, zero-padded}}, e.g. {@code CL-2026-00001234}. Mirrors
 * {@code policy.domain.PolicyNumber} exactly and deliberately - the same
 * shape, the same guarantees - so a reader who knows one already knows both.
 *
 * <p>A pure function of its two arguments: the year is resolved by the
 * caller in the business zone, and the numeric part comes from {@code
 * claim_number_seq} ({@code V10__create_claims_table.sql}), which - together
 * with {@code uq_claims_claim_number} - is what guarantees uniqueness.
 * Nothing here reads the highest existing number.
 */
public final class ClaimNumber {

    private static final String FORMAT = "CL-%d-%08d";

    private ClaimNumber() {
    }

    public static String format(int year, long sequenceValue) {
        return FORMAT.formatted(year, sequenceValue);
    }
}
