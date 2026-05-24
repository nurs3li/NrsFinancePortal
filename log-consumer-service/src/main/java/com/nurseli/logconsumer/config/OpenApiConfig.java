package com.nurseli.logconsumer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 3 dokümantasyon metadata bean'i.
 */
@Configuration
public class OpenApiConfig {

    /**
     * {@code logConsumerOpenApi} — Log Consumer Service API başlık ve sürüm bilgisini tanımlar.
     */
    @Bean
    public OpenAPI logConsumerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NRS Log Consumer Service API")
                        .version("v1")
                        .description("Log consumer service endpoints"));
    }
}
