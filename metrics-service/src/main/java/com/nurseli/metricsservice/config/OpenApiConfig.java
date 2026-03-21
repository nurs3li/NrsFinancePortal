package com.nurseli.metricsservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI metricsServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NRS Metrics Service API")
                        .version("v1")
                        .description("Metrics service endpoints"));
    }
}

