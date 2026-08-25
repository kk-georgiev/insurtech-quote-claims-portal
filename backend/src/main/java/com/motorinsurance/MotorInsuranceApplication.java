package com.motorinsurance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point. Not a module in its own right (AD-1/AD-6) - just
 * the Spring Boot bootstrap for the single deployable process that the
 * {@code shared}, {@code auth}, {@code quote} and {@code pricing} modules
 * live inside.
 */
@SpringBootApplication
public class MotorInsuranceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MotorInsuranceApplication.class, args);
    }
}
