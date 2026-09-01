package com.motorinsurance.quote.application;

import com.motorinsurance.policy.application.PolicyView;

/**
 * The result of an acceptance attempt: the policy, and whether this call is
 * the one that created it.
 *
 * <p>{@code created} exists solely so {@code quote.api} can pick 201 or 200
 * (Architecture Spine AD-5, M3): the first successful acceptance creates
 * and returns 201; every later call for the same quote - a client retrying
 * after a dropped response, or the loser of a concurrent race - returns the
 * same policy with 200. Both are successes; neither is an error.
 */
public record AcceptanceOutcome(PolicyView policy, boolean created) {

    public static AcceptanceOutcome issued(PolicyView policy) {
        return new AcceptanceOutcome(policy, true);
    }

    public static AcceptanceOutcome existing(PolicyView policy) {
        return new AcceptanceOutcome(policy, false);
    }
}
