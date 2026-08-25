package com.motorinsurance.auth.domain;

/**
 * All four roles the {@code users} table can hold (Architecture Spine
 * Structural Seed). Story 1.2's self-registration only ever produces
 * {@link #CLIENT} - {@link #AGENT}, {@link #LIQUIDATOR} and
 * {@link #ADMINISTRATOR} are seeded by Epic 2 into this same table (see
 * Architecture Spine "Deferred").
 */
public enum Role {
    CLIENT,
    AGENT,
    LIQUIDATOR,
    ADMINISTRATOR
}
