package com.backend.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI cvPilotOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CVPilot AI API")
                .version("v1")
                .description("""
                        REST API behind the CVPilot AI frontend: CV storage and extraction, job parsing, \
                        deterministic match scoring, AI-assisted customization chat and export. \
                        No authentication: deploy on a trusted network (see README). \
                        Endpoints that call the AI provider are rate limited per client."""));
    }
}
