package com.motorinsurance.policy.domain;

/**
 * The human-readable policy number's format (FR-M3-06, BA 7.4,
 * Architecture Spine AD-7): {@code MI-{year}-{8 digits, zero-padded}}, e.g.
 * {@code MI-2026-00001234}.
 *
 * <p>A pure function of its two arguments on purpose - it takes neither a
 * clock nor a sequence. The year is resolved by the caller in the business
 * zone and the numeric part comes from {@code policy_number_seq}
 * ({@code V9__create_policies_table.sql}), which is what guarantees
 * uniqueness together with the {@code uq_policies_policy_number}
 * constraint. Nothing here reads the highest existing number.
 *
 * <p>Eight digits is a minimum width, not a cap: a sequence value past
 * 99,999,999 renders wider rather than wrapping into a collision. The
 * {@code VARCHAR(20)} column has room for twelve digits, which the global
 * sequence would reach only after a trillion policies.
 */
public final class PolicyNumber {

    private static final String FORMAT = "MI-%d-%08d";

    private PolicyNumber() {
    }

    public static String format(int year, long sequenceValue) {
        return FORMAT.formatted(year, sequenceValue);
    }
}
