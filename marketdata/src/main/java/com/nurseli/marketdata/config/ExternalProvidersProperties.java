package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "market.providers")
@Data
public class ExternalProvidersProperties {

    private final Debt debt = new Debt();

    @Data
    public static class Debt {
        private boolean enabled = false;
        private String baseUrl;
        private String apiKey;
        private int timeoutMs = 3000;
        private String latestPath = "/debt/latest";
    }
}
