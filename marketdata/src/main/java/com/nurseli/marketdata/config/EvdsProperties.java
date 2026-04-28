package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "market.evds")
@Data
public class EvdsProperties {
    private boolean enabled = true;
    private String apiKey;
    private int timeoutMs = 3000;
    private String baseUrl = "https://evds2.tcmb.gov.tr/service/evds";
}
