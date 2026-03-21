package com.nurseli.logconsumer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI logConsumerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NRS Log Consumer Service API")
                        .version("v1")
                        .description("Log consumer service endpoints"));
    }
}
