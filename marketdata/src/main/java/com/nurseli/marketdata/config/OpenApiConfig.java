package com.nurseli.marketdata.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI marketDataOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NRS Market Data Service API")
                        .version("v1")
                        .description("Market data service endpoints"));
    }
}

