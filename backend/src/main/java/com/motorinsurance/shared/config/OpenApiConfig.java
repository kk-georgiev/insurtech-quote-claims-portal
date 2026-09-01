package com.motorinsurance.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API metadata for the springdoc-generated OpenAPI document (Story 9.1,
 * FR-M3-14). springdoc-openapi infers every endpoint's request/response
 * shape by reflecting over the existing {@code @RequestMapping}/{@code
 * @PathVariable}/{@code @RequestBody} annotations and DTOs already present on
 * {@code AuthController}, {@code QuoteController}, and {@code
 * PolicyController} - no controller or DTO changes needed. This bean supplies
 * the one piece of context that can't be inferred that way: the bonus-malus
 * provenance disclaimer.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI motorInsuranceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Motor Insurance Quote & Claims Portal API")
                        .version("v1")
                        .description(
                                """
                                REST API for the Motor Insurance Quote & Claims Portal: \
                                authentication, quote calculation/retrieval, and policy \
                                retrieval.

                                Bonus-malus provenance: the bonus-malus scale used in \
                                quote calculation is this project's own demo model, \
                                inherited from the team's prototype - not official or \
                                regulatorily determined Bulgarian market values."""));
    }
}
