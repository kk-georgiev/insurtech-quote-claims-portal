package com.motorinsurance.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one injectable {@link Clock} every business-date comparison in this
 * backend must use (Architecture Spine AD-6, Story 6.2) - fixed to
 * {@code Europe/Sofia}, the business time zone, so no service compares a
 * business date against UTC or the JVM default and drifts near midnight.
 *
 * <p>No production code calls {@code Instant.now()} / {@code LocalDate.now()}
 * / {@code LocalDate.now(ZoneId.systemDefault())} directly for a business
 * date - every such comparison takes this bean and calls
 * {@code LocalDate.now(clock)} instead, which is also what makes the
 * comparison substitutable with a fixed clock in a test.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Sofia"));
    }
}
