package com.motorinsurance.auth.domain;

import java.util.Locale;

/**
 * Canonicalizes an email address for storage and lookup. Both
 * {@code auth.application.RegistrationService} (persists the value) and
 * {@code auth.application.AuthenticationService} (looks the value up) must
 * apply the <em>exact</em> same transformation, or a user who registered as
 * {@code "User@Example.com"} could never log back in with a differently-cased
 * attempt against {@code users.email}'s case-sensitive {@code UNIQUE} column
 * (Epic 1 retro action item 3 - the two call sites previously each inlined
 * this, with a comment in each acknowledging they had to stay in sync).
 *
 * <p>{@link Locale#ROOT} is deliberate: the default-locale
 * {@code toLowerCase()} the two call sites used before this was extracted
 * lowercases {@code 'I'} to {@code 'ı'} (dotless i) under a Turkish locale,
 * so the normalized form would depend on the server's locale.
 */
public final class Emails {

    private Emails() {
    }

    /** Trims surrounding whitespace and lower-cases, locale-independently. */
    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
